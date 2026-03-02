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
                                      FrequenciaPagamento frequenciaPagamento) {

        if (frequenciaPagamento == null) {
            throw new RuntimeException("Frequência de pagamento é obrigatória");
        }

        Emprestimo emprestimo = new Emprestimo();

        emprestimo.setCliente(cliente);
        emprestimo.setValorEmprestado(valorEmprestado);
        emprestimo.setQuantidadeParcelas(quantidadeParcelas);
        emprestimo.setTaxaDeJuros(jurosCobrado);
        emprestimo.setFormaPagamento(formaPagamento);
        emprestimo.setFrequenciaPagamento(frequenciaPagamento);

        emprestimo.setDataDoEmprestimo(LocalDate.now());
        emprestimo.setEmprestimoStatus(EmprestimoStatus.EM_ABERTO);

        BigDecimal valorParcela =
                calculadora.calcularParcela(valorEmprestado, jurosCobrado, quantidadeParcelas);

        BigDecimal valorTotal = valorParcela
                .multiply(BigDecimal.valueOf(quantidadeParcelas))
                .setScale(2, RoundingMode.HALF_UP);

        emprestimo.setValorParcela(valorParcela);
        emprestimo.setValorTotalEmprestimo(valorTotal);
        emprestimo.setValorRecebido(BigDecimal.ZERO);
        emprestimo.setValorAReceber(valorTotal);

        emprestimoRepositorio.save(emprestimo);

        List<Parcelas> parcelas = calculadora.gerarParcelas(emprestimo);

        emprestimo.setParcelas(parcelas);
        parcelaRepositorio.saveAll(parcelas);

        if (!parcelas.isEmpty()) {
            emprestimo.setInicioPagamento(parcelas.get(0).getDataVencimento());
            emprestimo.setFinalPagamento(parcelas.get(parcelas.size() - 1).getDataVencimento());
        }

        return emprestimoRepositorio.save(emprestimo);
    }

    @Transactional
    public void pagarParcela(Long idEmprestimo, Integer numeroParcela) {
        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());
        BigDecimal saldoRestante =
                safe(parcela.getValorParcela()).subtract(valorPagoAtual);

        pagarParcelaParcial(idEmprestimo, numeroParcela, saldoRestante);
    }

    @Transactional
    public void pagarParcelaParcial(Long idEmprestimo,
                                    Integer numeroParcela,
                                    BigDecimal valorPago) {

        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);
        Emprestimo emprestimo = parcela.getEmprestimo();

        if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            throw new RuntimeException("Empréstimo já quitado");
        }

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new ParcelaPagaException();
        }

        if (valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor deve ser maior que zero");
        }

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());
        BigDecimal saldoAtual =
                safe(parcela.getValorParcela()).subtract(valorPagoAtual);

        if (valorPago.compareTo(saldoAtual) > 0) {
            throw new RuntimeException("Valor maior que saldo da parcela");
        }

        parcela.setValorPago(valorPagoAtual.add(valorPago));

        if (parcela.getValorPago().compareTo(parcela.getValorParcela()) == 0) {
            parcela.setStatus(ParcelaStatus.PAGO);
        } else {
            parcela.setStatus(ParcelaStatus.PARCIAL);
        }

        parcelaRepositorio.save(parcela);

        atualizarFinanceiroEmprestimo(emprestimo, valorPago);
    }

    private void atualizarFinanceiroEmprestimo(Emprestimo emprestimo,
                                               BigDecimal valorPago) {

        BigDecimal valorTotal = safe(emprestimo.getValorTotalEmprestimo());
        BigDecimal recebidoAtual = safe(emprestimo.getValorRecebido());

        BigDecimal novoRecebido = recebidoAtual.add(valorPago);

        emprestimo.setValorRecebido(novoRecebido);
        emprestimo.setValorAReceber(valorTotal.subtract(novoRecebido));

        boolean todasPagas = emprestimo.getParcelas() != null &&
                emprestimo.getParcelas()
                        .stream()
                        .allMatch(p -> p.getStatus() == ParcelaStatus.PAGO);

        if (todasPagas) {
            emprestimo.setEmprestimoStatus(EmprestimoStatus.QUITADO);
        }

        emprestimoRepositorio.save(emprestimo);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Parcelas buscarParcela(Long idEmprestimo, Integer numeroParcela) {
        return (Parcelas) parcelaRepositorio
                .findByEmprestimoIdAndNumeroParcela(idEmprestimo, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));
    }

    @Transactional(readOnly = true)
    public List<Emprestimo> listarEmprestimosCliente(Cliente cliente) {
        return emprestimoRepositorio.findAllByCliente(cliente);
    }

    @Transactional(readOnly = true)
    public List<Parcelas> listarParcelas(Long idEmprestimo) {
        Emprestimo emprestimo = emprestimoRepositorio.buscarComParcelas(idEmprestimo)
                .orElseThrow(() -> new RuntimeException("Empréstimo não encontrado"));
        return emprestimo.getParcelas();
    }

    @Transactional(readOnly = true)
    public List<Parcelas> listarParcelasDoCliente(Long idCliente,
                                                  Long idEmprestimo) {

        Emprestimo emprestimo = emprestimoRepositorio.buscarComParcelas(idEmprestimo)
                .orElseThrow(() -> new RuntimeException("Empréstimo não encontrado"));

        if (!emprestimo.getCliente().getId().equals(idCliente)) {
            throw new RuntimeException("Este empréstimo não pertence ao cliente informado");
        }

        return emprestimo.getParcelas();
    }

    @Transactional
    public void deletarEmprestimo(Long idCliente, Long idEmprestimo) {

        Emprestimo emprestimo = emprestimoRepositorio.findById(idEmprestimo)
                .orElseThrow(() -> new RuntimeException("Empréstimo não encontrado"));

        if (!emprestimo.getCliente().getId().equals(idCliente)) {
            throw new RuntimeException("Este empréstimo não pertence ao cliente informado");
        }

        boolean existeParcelaPaga = emprestimo.getParcelas() != null &&
                emprestimo.getParcelas()
                        .stream()
                        .anyMatch(p ->
                                p.getStatus() == ParcelaStatus.PAGO ||
                                        p.getStatus() == ParcelaStatus.PARCIAL
                        );

        if (existeParcelaPaga) {
            throw new RuntimeException("Não é possível excluir empréstimo com parcelas pagas");
        }

        parcelaRepositorio.deleteAll(emprestimo.getParcelas());
        emprestimoRepositorio.delete(emprestimo);
    }

    @Transactional(readOnly = true)
    public List<EmprestimoDetalhadoDTO> listarDetalhado(Cliente cliente) {
        List<Emprestimo> emprestimos = emprestimoRepositorio.findAllByCliente(cliente);

        return emprestimos.stream().map(e -> {

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
        }).toList();
    }
}