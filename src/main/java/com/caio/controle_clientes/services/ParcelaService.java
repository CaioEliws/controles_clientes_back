package com.caio.controle_clientes.services;

import com.caio.controle_clientes.dto.ParcelaResponseDTO;
import com.caio.controle_clientes.exceptions.ParcelaPagaException;
import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.EmprestimoStatus;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ParcelaService {
    @Autowired
    private ParcelaRepositorio parcelaRepositorio;
    private EmprestimoRepositorio emprestimoRepositorio;

    public ParcelaService(ParcelaRepositorio parcelaRepositorio, EmprestimoRepositorio emprestimoRepositorio) {
        this.parcelaRepositorio = parcelaRepositorio;
        this.emprestimoRepositorio = emprestimoRepositorio;
    }

    @Transactional
    public void atualizarParcelasAtrasadas() {
        List<Parcelas> atrasadas = parcelaRepositorio.findByStatusAndDataVencimentoBefore(
                ParcelaStatus.PENDENTE, LocalDate.now()
        );

        atrasadas
                .forEach(p -> p.setStatus(ParcelaStatus.ATRASADO));
        parcelaRepositorio.saveAll(atrasadas);
    }

    @Transactional
    public void pagarParcelaParcial(Long idEmprestimo,
                                    Integer numeroParcela,
                                    BigDecimal valorPago) {

        Parcelas parcela = (Parcelas) parcelaRepositorio
                .findByEmprestimoIdAndNumeroParcela(idEmprestimo, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        Emprestimo emprestimo = parcela.getEmprestimo();

        if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            throw new RuntimeException("Esse empréstimo já está quitado");
        }

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new ParcelaPagaException();
        }

        if (valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor deve ser maior que zero");
        }

        BigDecimal saldoParcela =
                parcela.getValorParcela().subtract(parcela.getValorPago());

        if (valorPago.compareTo(saldoParcela) > 0) {
            throw new RuntimeException("Valor maior que o saldo da parcela");
        }

        parcela.setValorPago(
                parcela.getValorPago().add(valorPago)
        );

        if (parcela.getValorPago()
                .compareTo(parcela.getValorParcela()) == 0) {

            parcela.setStatus(ParcelaStatus.PAGO);

        } else {
            parcela.setStatus(ParcelaStatus.PARCIAL);
        }


        emprestimo.setValorRecebido(
                emprestimo.getValorRecebido().add(valorPago)
        );

        emprestimo.setValorAReceber(
                emprestimo.getValorAReceber().subtract(valorPago)
        );

        // Se zerou o saldo → quita empréstimo
        if (emprestimo.getValorAReceber()
                .compareTo(BigDecimal.ZERO) <= 0) {

            emprestimo.setEmprestimoStatus(EmprestimoStatus.QUITADO);
        }

        parcelaRepositorio.save(parcela);
        emprestimoRepositorio.save(emprestimo);
    }

    private void atualizarEmprestimo(Emprestimo emprestimo) {

        BigDecimal totalPago = emprestimo.getParcelas()
                .stream()
                .map(Parcelas::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        emprestimo.setValorRecebido(totalPago);

        emprestimo.setValorAReceber(
                emprestimo.getValorTotalEmprestimo()
                        .subtract(totalPago)
        );

        boolean todasPagas =
                emprestimo.getParcelas()
                        .stream()
                        .allMatch(p -> p.getStatus() == ParcelaStatus.PAGO);

        if (todasPagas) {
            emprestimo.setEmprestimoStatus(EmprestimoStatus.QUITADO);
        }

        emprestimoRepositorio.save(emprestimo);
    }

    public ParcelaResponseDTO toDTO(Parcelas parcela) {
        return new ParcelaResponseDTO(
                parcela.getEmprestimo().getId(),
                parcela.getEmprestimo().getCliente().getNome(),
                parcela.getNumeroParcela(),
                parcela.getValorParcela(),
                parcela.getValorPago(),
                parcela.getStatus(),
                parcela.getDataVencimento()
        );
    }

    @Transactional
    public void alterarDataVencimento(
            Long idEmprestimo,
            Integer numeroParcela,
            LocalDate novaData
    ) {

        Parcelas parcela = (Parcelas) parcelaRepositorio
                .findByEmprestimoIdAndNumeroParcela(idEmprestimo, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        Emprestimo emprestimo = parcela.getEmprestimo();

        if (emprestimo.getEmprestimoStatus() == EmprestimoStatus.QUITADO) {
            throw new RuntimeException("Empréstimo já está quitado");
        }

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new RuntimeException("Não é possível alterar data de parcela paga");
        }

        if (novaData.isBefore(LocalDate.now())) {
            throw new RuntimeException("Nova data não pode ser anterior a hoje");
        }

        parcela.setDataVencimento(novaData);

        if (parcela.getStatus() == ParcelaStatus.ATRASADO &&
                novaData.isAfter(LocalDate.now())) {

            parcela.setStatus(ParcelaStatus.PENDENTE);
        }

        parcelaRepositorio.save(parcela);
    }
}
