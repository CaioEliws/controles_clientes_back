package com.caio.controle_clientes.services;

import com.caio.controle_clientes.dto.EmprestimoDetalhadoDTO;
import com.caio.controle_clientes.exceptions.ParcelaPagaException;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class EmprestimoService {

    private final EmprestimoRepositorio emprestimoRepositorio;
    private final ParcelaRepositorio parcelaRepositorio;
    private final CalculadoraEmprestimoService calculadora;

    public EmprestimoService(EmprestimoRepositorio emprestimoRepositorio,
                             ParcelaRepositorio parcelaRepositorio,
                             CalculadoraEmprestimoService calculadora) {
        this.emprestimoRepositorio = emprestimoRepositorio;
        this.parcelaRepositorio = parcelaRepositorio;
        this.calculadora = calculadora;
    }

    @Transactional
    public Emprestimo criarEmprestimo(Cliente cliente,
                                      BigDecimal valorEmprestado,
                                      int quantidadeParcelas,
                                      BigDecimal jurosCobrado,
                                      FormaPagamento formaPagamento,
                                      FrequenciaPagamento frequenciaPagamento,
                                      TipoContrato tipoContrato) {

        validarDadosCriacao(frequenciaPagamento, tipoContrato);

        Emprestimo emprestimo = montarEmprestimo(
                cliente,
                valorEmprestado,
                quantidadeParcelas,
                jurosCobrado,
                formaPagamento,
                frequenciaPagamento,
                tipoContrato
        );

        emprestimoRepositorio.save(emprestimo);

        List<Parcelas> parcelas = calculadora.gerarParcelas(emprestimo);
        emprestimo.setParcelas(parcelas);
        parcelaRepositorio.saveAll(parcelas);

        atualizarPeriodoPagamento(emprestimo, parcelas);

        return emprestimoRepositorio.save(emprestimo);
    }

    @Transactional
    public void pagarParcela(Long idEmprestimo, Integer numeroParcela) {
        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);

        BigDecimal saldoRestante = safe(parcela.getValorParcela())
                .subtract(safe(parcela.getValorPago()));

        pagarParcelaParcial(idEmprestimo, numeroParcela, saldoRestante);
    }

    @Transactional
    public void pagarParcelaParcial(Long idEmprestimo,
                                    Integer numeroParcela,
                                    BigDecimal valorPago) {

        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);
        Emprestimo emprestimo = parcela.getEmprestimo();

        validarPagamentoParcela(emprestimo, parcela, valorPago);

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());
        parcela.setValorPago(valorPagoAtual.add(valorPago));
        parcela.setStatus(
                parcela.getValorPago().compareTo(parcela.getValorParcela()) == 0
                        ? ParcelaStatus.PAGO
                        : ParcelaStatus.PARCIAL
        );

        parcelaRepositorio.save(parcela);
        atualizarFinanceiroEmprestimo(emprestimo, valorPago);
    }

    @Transactional(readOnly = true)
    public List<Emprestimo> listarEmprestimosCliente(Cliente cliente) {
        return emprestimoRepositorio.findAllByCliente(cliente);
    }

    @Transactional(readOnly = true)
    public List<Parcelas> listarParcelas(Long idEmprestimo) {
        return buscarEmprestimoComParcelas(idEmprestimo).getParcelas();
    }

    @Transactional(readOnly = true)
    public List<Parcelas> listarParcelasDoCliente(Long idCliente, Long idEmprestimo) {
        Emprestimo emprestimo = buscarEmprestimoComParcelas(idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);
        return emprestimo.getParcelas();
    }

    @Transactional
    public void deletarEmprestimo(Long idCliente, Long idEmprestimo) {
        Emprestimo emprestimo = buscarEmprestimoPorId(idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);

        boolean existeParcelaPaga = emprestimo.getParcelas() != null &&
                emprestimo.getParcelas().stream()
                        .anyMatch(p -> p.getStatus() == ParcelaStatus.PAGO || p.getStatus() == ParcelaStatus.PARCIAL);

        if (existeParcelaPaga) {
            throw new RuntimeException("Não é possível excluir empréstimo com parcelas pagas");
        }

        parcelaRepositorio.deleteAll(emprestimo.getParcelas());
        emprestimoRepositorio.delete(emprestimo);
    }

    @Transactional(readOnly = true)
    public List<EmprestimoDetalhadoDTO> listarDetalhado(Cliente cliente) {
        return emprestimoRepositorio.findAllByCliente(cliente)
                .stream()
                .map(this::toDetalhadoDTO)
                .toList();
    }

    @Transactional
    public void refinanciarEmprestimo(Long idCliente, Long idEmprestimo) {
        finalizarEmprestimo(idCliente, idEmprestimo, EmprestimoStatus.REFINANCIADO, true);
    }

    @Transactional
    public void quitarEmprestimo(Long idCliente, Long idEmprestimo) {
        finalizarEmprestimo(idCliente, idEmprestimo, EmprestimoStatus.QUITADO, false);
    }

    private void validarDadosCriacao(FrequenciaPagamento frequenciaPagamento,
                                     TipoContrato tipoContrato) {
        if (frequenciaPagamento == null) {
            throw new RuntimeException("Frequência de pagamento é obrigatória");
        }

        if (tipoContrato == null) {
            throw new RuntimeException("Tipo de contrato é obrigatório");
        }
    }

    private Emprestimo montarEmprestimo(Cliente cliente,
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

        BigDecimal valorTotal = valorParcela
                .multiply(BigDecimal.valueOf(quantidadeParcelas))
                .setScale(2, RoundingMode.HALF_UP);

        Emprestimo emprestimo = new Emprestimo();
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

        emprestimo.setInicioPagamento(parcelas.get(0).getDataVencimento());
        emprestimo.setFinalPagamento(parcelas.get(parcelas.size() - 1).getDataVencimento());
    }

    private void validarPagamentoParcela(Emprestimo emprestimo,
                                         Parcelas parcela,
                                         BigDecimal valorPago) {

        if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            throw new RuntimeException("Empréstimo já quitado");
        }

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new ParcelaPagaException();
        }

        if (valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor deve ser maior que zero");
        }

        BigDecimal saldoAtual = safe(parcela.getValorParcela())
                .subtract(safe(parcela.getValorPago()));

        if (valorPago.compareTo(saldoAtual) > 0) {
            throw new RuntimeException("Valor maior que saldo da parcela");
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

    private void finalizarEmprestimo(Long idCliente,
                                     Long idEmprestimo,
                                     EmprestimoStatus statusFinal,
                                     boolean validarRefinanciado) {

        Emprestimo emprestimo = buscarEmprestimoComParcelas(idEmprestimo);
        validarPertencimentoCliente(emprestimo, idCliente);

        if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            throw new RuntimeException("Empréstimo já está quitado");
        }

        if (validarRefinanciado && emprestimo.getEmprestimoStatus() == EmprestimoStatus.REFINANCIADO) {
            throw new RuntimeException("Empréstimo já foi refinanciado");
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
                atrasadas
        );
    }

    private void validarPertencimentoCliente(Emprestimo emprestimo, Long idCliente) {
        if (!emprestimo.getCliente().getId().equals(idCliente)) {
            throw new RuntimeException("Este empréstimo não pertence ao cliente informado");
        }
    }

    private Emprestimo buscarEmprestimoPorId(Long idEmprestimo) {
        return emprestimoRepositorio.findById(idEmprestimo)
                .orElseThrow(() -> new RuntimeException("Empréstimo não encontrado"));
    }

    private Emprestimo buscarEmprestimoComParcelas(Long idEmprestimo) {
        return emprestimoRepositorio.buscarComParcelas(idEmprestimo)
                .orElseThrow(() -> new RuntimeException("Empréstimo não encontrado"));
    }

    private Parcelas buscarParcela(Long idEmprestimo, Integer numeroParcela) {
        return parcelaRepositorio.findByEmprestimoIdAndNumeroParcela(idEmprestimo, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}