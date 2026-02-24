package com.caio.controle_clientes.services;

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
    private final ParcelaService parcelaService;

    public EmprestimoService(EmprestimoRepositorio emprestimoRepositorio,
                             ParcelaRepositorio parcelaRepositorio,
                             CalculadoraEmprestimoService calculadora,
                             ParcelaService parcelaService) {

        this.emprestimoRepositorio = emprestimoRepositorio;
        this.parcelaRepositorio = parcelaRepositorio;
        this.calculadora = calculadora;
        this.parcelaService = parcelaService;
    }

    @Transactional
    public Emprestimo criarEmprestimo(Cliente cliente,
                                      BigDecimal valorEmprestado,
                                      int quantidadeParcelas,
                                      BigDecimal jurosCobrado,
                                      FormaPagamento formaPagamento) {

        Emprestimo emprestimo = new Emprestimo();

        emprestimo.setCliente(cliente);
        emprestimo.setValorEmprestado(valorEmprestado);
        emprestimo.setQuantidadeParcelas(quantidadeParcelas);
        emprestimo.setTaxaDeJuros(jurosCobrado);
        emprestimo.setFormaPagamento(formaPagamento);

        emprestimo.setDataDoEmprestimo(LocalDate.now());
        emprestimo.setInicioPagamento(LocalDate.now().plusMonths(1));
        emprestimo.setFinalPagamento(LocalDate.now().plusMonths(quantidadeParcelas));

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

        return emprestimo;
    }

    @Transactional
    public void pagarParcela(Long idEmprestimo, Integer numeroParcela) {

        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);

        pagarParcelaParcial(
                idEmprestimo,
                numeroParcela,
                parcela.getValorParcela().subtract(parcela.getValorPago())
        );
    }

    // ==========================
    // PAGAMENTO PARCIAL
    // ==========================

    @Transactional
    public void pagarParcelaParcial(Long idEmprestimo,
                                    Integer numeroParcela,
                                    BigDecimal valorPago) {

        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new ParcelaPagaException();
        }

        if (valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor deve ser maior que zero");
        }

        BigDecimal saldoAtual =
                parcela.getValorParcela().subtract(parcela.getValorPago());

        if (valorPago.compareTo(saldoAtual) > 0) {
            throw new RuntimeException("Valor maior que o saldo da parcela");
        }

        // Atualiza parcela
        parcela.setValorPago(
                parcela.getValorPago().add(valorPago)
        );

        if (parcela.getValorPago()
                .compareTo(parcela.getValorParcela()) == 0) {

            parcela.setStatus(ParcelaStatus.PAGO);

        } else {
            parcela.setStatus(ParcelaStatus.PARCIAL);
        }

        parcelaRepositorio.save(parcela);

        // Atualiza empréstimo
        atualizarFinanceiroEmprestimo(parcela.getEmprestimo(), valorPago);
    }

    // ==========================
    // ATUALIZA SALDO DO EMPRÉSTIMO
    // ==========================

    private void atualizarFinanceiroEmprestimo(Emprestimo emprestimo,
                                               BigDecimal valorPago) {

        emprestimo.setValorRecebido(
                emprestimo.getValorRecebido().add(valorPago)
        );

        emprestimo.setValorAReceber(
                emprestimo.getValorTotalEmprestimo()
                        .subtract(emprestimo.getValorRecebido())
        );

        // Fecha empréstimo se tudo pago
        boolean todasPagas = emprestimo.getParcelas()
                .stream()
                .allMatch(p -> p.getStatus() == ParcelaStatus.PAGO);

        if (todasPagas) {
            emprestimo.setEmprestimoStatus(EmprestimoStatus.QUITADO);
        }

        emprestimoRepositorio.save(emprestimo);
    }

    // ==========================
    // BUSCA PARCELA (REUTILIZÁVEL)
    // ==========================

    private Parcelas buscarParcela(Long idEmprestimo, Integer numeroParcela) {
        return (Parcelas) parcelaRepositorio
                .findByEmprestimoIdAndNumeroParcela(idEmprestimo, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));
    }

    // ==========================
    // LISTAGENS
    // ==========================

    @Transactional
    public List<Emprestimo> listarEmprestimosCliente(Cliente cliente) {
        return emprestimoRepositorio.findAllByCliente(cliente);
    }

    @Transactional
    public List<Parcelas> listarParcelas(Long idEmprestimo) {

        Emprestimo emprestimo = emprestimoRepositorio.buscarComParcelas(idEmprestimo)
                .orElseThrow(() -> new RuntimeException("Emprestimo não encontrado"));

        return emprestimo.getParcelas();
    }

    @Transactional
    public void deletarEmprestimo(Long idCliente, Long idEmprestimo) {
        Emprestimo emprestimo = emprestimoRepositorio.findById(idEmprestimo)
                .orElseThrow(() -> new RuntimeException("Empréstimo não encontrado"));

        if (!emprestimo.getCliente().getId().equals(idCliente)) {
            throw new RuntimeException("Este empréstimo não pertence ao cliente informado");
        }

        boolean existeParcelaPaga = emprestimo.getParcelas()
                .stream()
                .anyMatch(p -> p.getStatus() == ParcelaStatus.PAGO
                        || p.getStatus() == ParcelaStatus.PARCIAL);

        if (existeParcelaPaga) {
            throw new RuntimeException("Não é possível excluir empréstimo com parcelas pagas");
        }

        parcelaRepositorio.deleteAll(emprestimo.getParcelas());

        emprestimoRepositorio.delete(emprestimo);
    }
}