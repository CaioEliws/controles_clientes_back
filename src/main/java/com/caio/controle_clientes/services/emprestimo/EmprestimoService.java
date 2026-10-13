package com.caio.controle_clientes.services.emprestimo;

import com.caio.controle_clientes.dto.emprestimo.EmprestimoDetalhadoDTO;
import com.caio.controle_clientes.exceptions.ParcelaPagaException;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.ContratoGeradoRepositorio;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import com.caio.controle_clientes.services.perfil.PerfilService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class EmprestimoService {

    private final PerfilService perfilService;
    private final EmprestimoRepositorio emprestimoRepositorio;
    private final ParcelaRepositorio parcelaRepositorio;
    private final CalculadoraEmprestimoService calculadora;
    private final ClienteRepositorio clienteRepositorio;
    private final ContratoGeradoRepositorio contratoGeradoRepositorio;

    public EmprestimoService(EmprestimoRepositorio emprestimoRepositorio,
                             ParcelaRepositorio parcelaRepositorio,
                             CalculadoraEmprestimoService calculadora,
                             PerfilService perfilService,
                             ClienteRepositorio clienteRepositorio,
                             ContratoGeradoRepositorio contratoGeradoRepositorio) {
        this.emprestimoRepositorio = emprestimoRepositorio;
        this.parcelaRepositorio = parcelaRepositorio;
        this.calculadora = calculadora;
        this.perfilService = perfilService;
        this.clienteRepositorio = clienteRepositorio;
        this.contratoGeradoRepositorio = contratoGeradoRepositorio;
    }

    @Transactional
    public Emprestimo criarEmprestimo(Long perfilId,
                                      Cliente cliente,
                                      BigDecimal valorEmprestado,
                                      int quantidadeParcelas,
                                      BigDecimal jurosCobrado,
                                      FormaPagamento formaPagamento,
                                      FrequenciaPagamento frequenciaPagamento,
                                      TipoContrato tipoContrato) {

        return criarEmprestimo(perfilId, cliente, valorEmprestado, quantidadeParcelas,
                jurosCobrado, formaPagamento, frequenciaPagamento, tipoContrato, null);
    }

    @Transactional
    public Emprestimo criarEmprestimo(Long perfilId,
                                      Cliente cliente,
                                      BigDecimal valorEmprestado,
                                      int quantidadeParcelas,
                                      BigDecimal jurosCobrado,
                                      FormaPagamento formaPagamento,
                                      FrequenciaPagamento frequenciaPagamento,
                                      TipoContrato tipoContrato,
                                      Long fiadorId) {

        if (fiadorId == null) {
            throw new RegraNegocioException("Fiador é obrigatório.");
        }
        validarDadosCriacao(frequenciaPagamento, tipoContrato);
        if (valorEmprestado == null || valorEmprestado.signum() <= 0) {
            throw new RegraNegocioException("Valor emprestado deve ser maior que zero.");
        }
        if (quantidadeParcelas <= 0) {
            throw new RegraNegocioException("Quantidade de parcelas deve ser maior que zero.");
        }
        if (jurosCobrado == null || jurosCobrado.signum() < 0) {
            throw new RegraNegocioException("Taxa de juros deve ser informada e não pode ser negativa.");
        }
        if (formaPagamento == null) {
            throw new RegraNegocioException("Forma de pagamento é obrigatória.");
        }

        Perfil perfil = perfilService.buscarPorId(perfilId);

        if (cliente.getPerfil() == null || !perfilId.equals(cliente.getPerfil().getId())) {
            throw new RegraNegocioException("Cliente não pertence ao perfil informado");
        }

        Cliente fiador = buscarFiador(perfilId, fiadorId);

        Emprestimo emprestimo = montarEmprestimo(
                perfil,
                cliente,
                valorEmprestado,
                quantidadeParcelas,
                jurosCobrado,
                formaPagamento,
                frequenciaPagamento,
                tipoContrato
        );

        emprestimo.setFiador(fiador);
        emprestimoRepositorio.save(emprestimo);

        List<Parcelas> parcelas = calculadora.gerarParcelas(emprestimo);
        parcelas.forEach(p -> {
            p.setEmprestimo(emprestimo);
            p.setPerfil(perfil);
        });

        emprestimo.setParcelas(parcelas);
        parcelaRepositorio.saveAll(parcelas);

        atualizarPeriodoPagamento(emprestimo, parcelas);

        return emprestimoRepositorio.save(emprestimo);
    }

    @Transactional
    public void alterarFiador(Long perfilId, Long idCliente, Long idEmprestimo, Long fiadorId) {
        Emprestimo emprestimo = buscarEmprestimoPorId(perfilId, idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);
        validarClientePertenceAoPerfil(emprestimo.getCliente(), perfilId);
        emprestimo.setFiador(buscarFiador(perfilId, fiadorId));
        emprestimoRepositorio.save(emprestimo);
    }

    private Cliente buscarFiador(Long perfilId, Long fiadorId) {
        if (fiadorId == null) {
            throw new RegraNegocioException("Fiador é obrigatório.");
        }
        return clienteRepositorio.findByIdAndPerfilId(fiadorId, perfilId)
                .orElseThrow(() -> new RegraNegocioException(
                        "Fiador não encontrado para o perfil informado."));
    }

    @Transactional
    public void pagarParcela(Long perfilId, Long idEmprestimo, Integer numeroParcela) {
        bloquearEmprestimo(perfilId, idEmprestimo);
        Parcelas parcela = buscarParcela(perfilId, idEmprestimo, numeroParcela);

        BigDecimal saldoRestante = safe(parcela.getValorParcela())
                .subtract(safe(parcela.getValorPago()));

        pagarParcelaParcial(perfilId, idEmprestimo, numeroParcela, saldoRestante);
    }

    @Transactional
    public void pagarParcelaParcial(Long perfilId,
                                    Long idEmprestimo,
                                    Integer numeroParcela,
                                    BigDecimal valorPago) {
        bloquearEmprestimo(perfilId, idEmprestimo);
        Parcelas parcela = buscarParcela(perfilId, idEmprestimo, numeroParcela);
        Emprestimo emprestimo = parcela.getEmprestimo();

        validarPagamentoParcela(emprestimo, parcela, valorPago);

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());
        parcela.setValorPago(valorPagoAtual.add(valorPago));
        parcela.setStatus(
                parcela.getValorPago().compareTo(parcela.getValorParcela()) == 0
                        ? ParcelaStatus.PAGO
                        : ParcelaStatus.PARCIAL
        );

        if (parcela.getStatus() == ParcelaStatus.PAGO || parcela.getStatus() == ParcelaStatus.PARCIAL) {
            parcela.setDataPagamento(LocalDate.now());
        }

        parcelaRepositorio.save(parcela);
        atualizarFinanceiroEmprestimo(emprestimo, valorPago);
    }

    @Transactional(readOnly = true)
    public List<Emprestimo> listarEmprestimosCliente(Long perfilId, Cliente cliente) {
        validarClientePertenceAoPerfil(cliente, perfilId);
        return emprestimoRepositorio.findAllByClienteAndPerfilId(cliente, perfilId);
    }

    @Transactional(readOnly = true)
    public List<Parcelas> listarParcelas(Long perfilId, Long idEmprestimo) {
        return buscarEmprestimoComParcelas(perfilId, idEmprestimo).getParcelas();
    }

    @Transactional(readOnly = true)
    public List<Parcelas> listarParcelasDoCliente(Long perfilId, Long idCliente, Long idEmprestimo) {
        Emprestimo emprestimo = buscarEmprestimoComParcelas(perfilId, idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);
        return emprestimo.getParcelas();
    }

    @Transactional
    public void deletarEmprestimo(Long perfilId, Long idCliente, Long idEmprestimo) {
        perfilService.buscarPorId(perfilId);
        clienteRepositorio.findByIdAndPerfilId(idCliente, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado para o perfil informado."));
        bloquearEmprestimo(perfilId, idEmprestimo);
        Emprestimo emprestimo = buscarEmprestimoComParcelas(perfilId, idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);

        boolean existeParcelaPaga = emprestimo.getParcelas() != null &&
                emprestimo.getParcelas().stream()
                        .anyMatch(p -> safe(p.getValorPago()).signum() > 0
                                || p.getStatus() == ParcelaStatus.PAGO || p.getStatus() == ParcelaStatus.PARCIAL);

        if (existeParcelaPaga) {
            throw new RegraNegocioException("Não é possível excluir empréstimo com parcelas pagas");
        }

        // Bulk delete executa o SQL antes de agendar a remoção do empréstimo.
        contratoGeradoRepositorio.deleteByEmprestimoId(idEmprestimo);
        parcelaRepositorio.deleteAll(emprestimo.getParcelas());
        emprestimoRepositorio.delete(emprestimo);
    }

    @Transactional(readOnly = true)
    public List<EmprestimoDetalhadoDTO> listarDetalhado(Long perfilId, Cliente cliente) {
        validarClientePertenceAoPerfil(cliente, perfilId);

        return emprestimoRepositorio.findAllByClienteAndPerfilId(cliente, perfilId)
                .stream()
                .map(this::toDetalhadoDTO)
                .toList();
    }

    @Transactional
    public void refinanciarEmprestimo(Long perfilId, Long idCliente, Long idEmprestimo) {
        finalizarEmprestimo(perfilId, idCliente, idEmprestimo, EmprestimoStatus.REFINANCIADO, true);
    }

    @Transactional
    public void quitarEmprestimo(Long perfilId, Long idCliente, Long idEmprestimo) {
        finalizarEmprestimo(perfilId, idCliente, idEmprestimo, EmprestimoStatus.QUITADO, false);
    }

    @Transactional
    public void reprogramarParcelas(Long perfilId,
                                    Long idCliente,
                                    Long idEmprestimo,
                                    LocalDate novaDataInicial,
                                    BigDecimal novoValorParcela) {

        if (novaDataInicial == null) {
            throw new RegraNegocioException("Nova data inicial é obrigatória");
        }

        if (novoValorParcela == null || novoValorParcela.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Novo valor da parcela deve ser maior que zero");
        }

        bloquearEmprestimo(perfilId, idEmprestimo);
        Emprestimo emprestimo = buscarEmprestimoComParcelas(perfilId, idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);

        List<Parcelas> parcelas = emprestimo.getParcelas();

        if (parcelas == null || parcelas.isEmpty()) {
            throw new RegraNegocioException("Empréstimo não possui parcelas");
        }

        List<Parcelas> parcelasAlteraveis = parcelas.stream()
                .filter(p -> p.getStatus() != ParcelaStatus.PAGO)
                .sorted(Comparator.comparing(Parcelas::getNumeroParcela))
                .toList();

        if (parcelasAlteraveis.isEmpty()) {
            throw new RegraNegocioException("Não há parcelas em aberto para reprogramar");
        }

        for (int i = 0; i < parcelasAlteraveis.size(); i++) {
            Parcelas parcela = parcelasAlteraveis.get(i);

            if (novoValorParcela.compareTo(safe(parcela.getValorPago())) < 0) {
                throw new RegraNegocioException(
                        "Novo valor da parcela " + parcela.getNumeroParcela()
                                + " não pode ser menor que o valor já pago"
                );
            }

            LocalDate novaData = calcularNovaData(
                    novaDataInicial,
                    i,
                    emprestimo.getFrequenciaPagamento()
            );

            parcela.setDataVencimento(novaData);
            parcela.setValorParcela(novoValorParcela);

            atualizarStatusParcelaReprogramada(
                    parcela,
                    novaData,
                    novoValorParcela
            );
        }

        parcelaRepositorio.saveAll(parcelasAlteraveis);
        recalcularTotaisEPeriodoEmprestimo(emprestimo);
    }

    private void validarDadosCriacao(FrequenciaPagamento frequenciaPagamento,
                                     TipoContrato tipoContrato) {
        if (frequenciaPagamento == null) {
            throw new RegraNegocioException("Frequência de pagamento é obrigatória");
        }

        if (tipoContrato == null) {
            throw new RegraNegocioException("Tipo de contrato é obrigatório");
        }
    }

    private Emprestimo montarEmprestimo(Perfil perfil,
                                        Cliente cliente,
                                        BigDecimal valorEmprestado,
                                        int quantidadeParcelas,
                                        BigDecimal jurosCobrado,
                                        FormaPagamento formaPagamento,
                                        FrequenciaPagamento frequenciaPagamento,
                                        TipoContrato tipoContrato) {

        BigDecimal valorParcela = calculadora.calcularParcela(
                valorEmprestado,
                jurosCobrado,
                quantidadeParcelas
        );
        if (valorParcela.signum() <= 0) {
            throw new RegraNegocioException("Valor calculado da parcela deve ser maior que zero.");
        }

        BigDecimal valorTotal = valorParcela
                .multiply(BigDecimal.valueOf(quantidadeParcelas))
                .setScale(2, RoundingMode.HALF_UP);

        Emprestimo emprestimo = new Emprestimo();
        emprestimo.setPerfil(perfil);
        emprestimo.setCliente(cliente);
        emprestimo.setValorEmprestado(valorEmprestado);
        emprestimo.setQuantidadeParcelas(quantidadeParcelas);
        emprestimo.setTaxaDeJuros(jurosCobrado);
        emprestimo.setFormaPagamento(formaPagamento);
        emprestimo.setFrequenciaPagamento(frequenciaPagamento);
        emprestimo.setTipoContrato(tipoContrato);
        emprestimo.setDataDoEmprestimo(LocalDate.now());
        emprestimo.setEmprestimoStatus(EmprestimoStatus.EM_ABERTO);
        emprestimo.setValorParcela(valorParcela);
        emprestimo.setValorTotalEmprestimo(valorTotal);
        emprestimo.setValorRecebido(BigDecimal.ZERO);
        emprestimo.setValorAReceber(valorTotal);

        return emprestimo;
    }

    private void atualizarPeriodoPagamento(Emprestimo emprestimo, List<Parcelas> parcelas) {
        if (parcelas == null || parcelas.isEmpty()) {
            return;
        }

        List<Parcelas> ordenadas = parcelas.stream()
                .sorted(Comparator.comparing(Parcelas::getNumeroParcela))
                .toList();

        emprestimo.setInicioPagamento(ordenadas.get(0).getDataVencimento());
        emprestimo.setFinalPagamento(ordenadas.get(ordenadas.size() - 1).getDataVencimento());
    }

    private void validarPagamentoParcela(Emprestimo emprestimo,
                                         Parcelas parcela,
                                         BigDecimal valorPago) {

        if (valorPago == null) {
            throw new RegraNegocioException("Valor do pagamento é obrigatório.");
        }

        if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            throw new RegraNegocioException("Empréstimo já quitado");
        }

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new ParcelaPagaException();
        }

        if (valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Valor deve ser maior que zero");
        }

        BigDecimal saldoAtual = safe(parcela.getValorParcela())
                .subtract(safe(parcela.getValorPago()));

        if (valorPago.compareTo(saldoAtual) > 0) {
            throw new RegraNegocioException("Valor maior que saldo da parcela");
        }
    }

    private void atualizarFinanceiroEmprestimo(Emprestimo emprestimo, BigDecimal valorPago) {
        BigDecimal valorTotal = safe(emprestimo.getValorTotalEmprestimo());
        BigDecimal novoRecebido = safe(emprestimo.getValorRecebido()).add(valorPago);

        emprestimo.setValorRecebido(novoRecebido);
        emprestimo.setValorAReceber(valorTotal.subtract(novoRecebido));

        boolean todasPagas = emprestimo.getParcelas() != null &&
                emprestimo.getParcelas().stream()
                        .allMatch(p -> p.getStatus() == ParcelaStatus.PAGO);

        if (todasPagas) {
            emprestimo.setEmprestimoStatus(EmprestimoStatus.QUITADO);
        }

        emprestimoRepositorio.save(emprestimo);
    }

    private void finalizarEmprestimo(Long perfilId,
                                     Long idCliente,
                                     Long idEmprestimo,
                                     EmprestimoStatus statusFinal,
                                     boolean validarRefinanciado) {
        bloquearEmprestimo(perfilId, idEmprestimo);
        Emprestimo emprestimo = buscarEmprestimoComParcelas(perfilId, idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);

        if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            throw new RegraNegocioException("Empréstimo já está quitado");
        }

        if (validarRefinanciado && emprestimo.getEmprestimoStatus() == EmprestimoStatus.REFINANCIADO) {
            throw new RegraNegocioException("Empréstimo já foi refinanciado");
        }

        LocalDate hoje = LocalDate.now();

        for (Parcelas parcela : emprestimo.getParcelas()) {
            if (statusFinal == EmprestimoStatus.REFINANCIADO && parcela.getStatus() == ParcelaStatus.PAGO) {
                continue;
            }

            BigDecimal falta = calcularSaldoRestante(parcela);
            parcela.setValorPago(safe(parcela.getValorPago()).add(falta));
            parcela.setStatus(ParcelaStatus.PAGO);
            parcela.setDataPagamento(hoje);
        }

        parcelaRepositorio.saveAll(emprestimo.getParcelas());

        BigDecimal recebido = emprestimo.getParcelas().stream()
                .map(p -> safe(p.getValorPago()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = safe(emprestimo.getValorTotalEmprestimo());

        emprestimo.setValorRecebido(recebido);
        emprestimo.setValorAReceber(total.subtract(recebido));
        emprestimo.setEmprestimoStatus(statusFinal);

        emprestimoRepositorio.save(emprestimo);
    }

    private BigDecimal calcularSaldoRestante(Parcelas parcela) {
        BigDecimal falta = safe(parcela.getValorParcela())
                .subtract(safe(parcela.getValorPago()));

        return falta.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : falta;
    }

    private LocalDate calcularNovaData(LocalDate dataInicial,
                                       int indice,
                                       FrequenciaPagamento frequenciaPagamento) {
        return switch (frequenciaPagamento) {
            case DIARIO -> dataInicial.plusDays(indice);
            case SEMANAL -> dataInicial.plusWeeks(indice);
            case QUINZENAL -> dataInicial.plusDays(indice * 15L);
            case MENSAL -> calcularNovaDataMensal(dataInicial, indice);
        };
    }

    private LocalDate calcularNovaDataMensal(LocalDate dataInicial, int indice) {
        int diaBase = dataInicial.getDayOfMonth();

        LocalDate mesBase = dataInicial.plusMonths(indice);
        int ultimoDiaDoMes = mesBase.lengthOfMonth();
        int diaNoMes = Math.min(diaBase, ultimoDiaDoMes);

        return mesBase.withDayOfMonth(diaNoMes);
    }

    private void atualizarStatusParcelaReprogramada(Parcelas parcela,
                                                    LocalDate novaData,
                                                    BigDecimal novoValorParcela) {

        BigDecimal valorPago = safe(parcela.getValorPago());

        if (valorPago.compareTo(BigDecimal.ZERO) == 0) {
            parcela.setStatus(
                    novaData.isBefore(LocalDate.now())
                            ? ParcelaStatus.ATRASADO
                            : ParcelaStatus.PENDENTE
            );
            return;
        }

        if (valorPago.compareTo(novoValorParcela) >= 0) {
            parcela.setStatus(ParcelaStatus.PAGO);
            return;
        }

        parcela.setStatus(
                novaData.isBefore(LocalDate.now())
                        ? ParcelaStatus.ATRASADO
                        : ParcelaStatus.PARCIAL
        );
    }

    private void recalcularTotaisEPeriodoEmprestimo(Emprestimo emprestimo) {
        List<Parcelas> parcelas = emprestimo.getParcelas();

        BigDecimal totalEmprestimo = parcelas.stream()
                .map(p -> safe(p.getValorParcela()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRecebido = parcelas.stream()
                .map(p -> safe(p.getValorPago()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        emprestimo.setValorParcela(
                parcelas.stream()
                        .filter(p -> p.getStatus() != ParcelaStatus.PAGO)
                        .map(Parcelas::getValorParcela)
                        .filter(v -> v != null)
                        .findFirst()
                        .orElse(emprestimo.getValorParcela())
        );

        emprestimo.setValorTotalEmprestimo(totalEmprestimo);
        emprestimo.setValorRecebido(totalRecebido);
        emprestimo.setValorAReceber(totalEmprestimo.subtract(totalRecebido));

        boolean todasPagas = parcelas.stream()
                .allMatch(p -> p.getStatus() == ParcelaStatus.PAGO);

        if (todasPagas) {
            emprestimo.setEmprestimoStatus(EmprestimoStatus.QUITADO);
        } else if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            emprestimo.setEmprestimoStatus(EmprestimoStatus.EM_ABERTO);
        }

        List<Parcelas> ordenadas = parcelas.stream()
                .sorted(Comparator.comparing(Parcelas::getNumeroParcela))
                .toList();

        atualizarPeriodoPagamento(emprestimo, ordenadas);
        emprestimoRepositorio.save(emprestimo);
    }

    @Transactional(readOnly = true)
    public List<EmprestimoDetalhadoDTO> listarDetalhadoDoPerfil(Long perfilId) {
        perfilService.buscarPorId(perfilId);
        return emprestimoRepositorio.findAllByPerfilId(perfilId).stream()
                .map(this::toDetalhadoDTO).toList();
    }

    private EmprestimoDetalhadoDTO toDetalhadoDTO(Emprestimo e) {
        long pagas = e.getParcelas().stream()
                .filter(p -> p.getStatus().isPago())
                .count();

        long pendentes = e.getParcelas().stream()
                .filter(p -> p.getStatus().isPendente())
                .count();

        long atrasadas = e.getParcelas().stream()
                .filter(p -> p.getStatus().isAtrasada())
                .count();

        return new EmprestimoDetalhadoDTO(
                e.getId(),
                e.getCliente().getNome(),
                e.getDataDoEmprestimo(),
                e.getFormaPagamento().name(),
                e.getFrequenciaPagamento().name(),
                e.getTipoContrato().name(),
                e.getValorEmprestado(),
                e.getValorTotalEmprestimo(),
                e.getValorParcela(),
                e.getValorRecebido(),
                e.getValorAReceber(),
                e.getInicioPagamento(),
                e.getFinalPagamento(),
                e.getEmprestimoStatus(),
                e.getQuantidadeParcelas(),
                pagas,
                pendentes,
                atrasadas,
                e.getFiador() == null ? null : e.getFiador().getId(),
                e.getFiador() == null ? null : e.getFiador().getNome()
        );
    }

    private void validarPertencimentoCliente(Emprestimo emprestimo, Long idCliente) {
        if (!emprestimo.getCliente().getId().equals(idCliente)) {
            throw new RegraNegocioException("Este empréstimo não pertence ao cliente informado");
        }
    }

    private void validarClientePertenceAoPerfil(Cliente cliente, Long perfilId) {
        if (cliente.getPerfil() == null || !perfilId.equals(cliente.getPerfil().getId())) {
            throw new RegraNegocioException("Cliente não pertence ao perfil informado");
        }
    }

    private Emprestimo buscarEmprestimoPorId(Long perfilId, Long idEmprestimo) {
        return emprestimoRepositorio.findByIdAndPerfilId(idEmprestimo, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Empréstimo não encontrado"));
    }

    private void bloquearEmprestimo(Long perfilId, Long idEmprestimo) {
        emprestimoRepositorio.bloquearParaAtualizacao(idEmprestimo, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Empréstimo não encontrado"));
    }

    private Emprestimo buscarEmprestimoComParcelas(Long perfilId, Long idEmprestimo) {
        return emprestimoRepositorio.buscarComParcelas(idEmprestimo, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Empréstimo não encontrado"));
    }

    private Parcelas buscarParcela(Long perfilId, Long idEmprestimo, Integer numeroParcela) {
        return parcelaRepositorio.findByEmprestimoIdAndPerfilIdAndNumeroParcela(idEmprestimo, perfilId, numeroParcela)
                .orElseThrow(() -> new RegraNegocioException("Parcela não encontrada"));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
