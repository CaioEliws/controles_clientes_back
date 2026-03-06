package com.caio.controle_clientes.services;

import com.caio.controle_clientes.dto.ParcelaResponseDTO;
import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.EmprestimoStatus;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ParcelaService {
    private final ParcelaRepositorio parcelaRepositorio;
    private final EmprestimoRepositorio emprestimoRepositorio;

    public ParcelaService(ParcelaRepositorio parcelaRepositorio,
                          EmprestimoRepositorio emprestimoRepositorio) {
        this.parcelaRepositorio = parcelaRepositorio;
        this.emprestimoRepositorio = emprestimoRepositorio;
    }

    @Transactional
    public void atualizarParcelasAtrasadas() {
        LocalDate hoje = LocalDate.now();

        List<Parcelas> pendentesAtrasadas =
                parcelaRepositorio.findByStatusAndDataVencimentoBefore(
                        ParcelaStatus.PENDENTE, hoje
                );

        List<Parcelas> parciaisAtrasadas =
                parcelaRepositorio.findByStatusAndDataVencimentoBefore(
                        ParcelaStatus.PARCIAL, hoje
                );

        pendentesAtrasadas.forEach(p -> p.setStatus(ParcelaStatus.ATRASADO));
        parciaisAtrasadas.forEach(p -> p.setStatus(ParcelaStatus.ATRASADO));

        pendentesAtrasadas.addAll(parciaisAtrasadas);

        if (!pendentesAtrasadas.isEmpty()) {
            parcelaRepositorio.saveAll(pendentesAtrasadas);
        }
    }

    @Transactional
    public void alterarParcela(Long idEmprestimo,
                               Integer numeroParcela,
                               LocalDate novaData,
                               BigDecimal novoValor) {

        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);
        Emprestimo emprestimo = parcela.getEmprestimo();

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new RuntimeException("Parcela já paga não pode ser alterada");
        }

        if (novaData == null) {
            throw new RuntimeException("Nova data é obrigatória");
        }

        if (novaData.isBefore(LocalDate.now())) {
            throw new RuntimeException("Data inválida");
        }

        if (novoValor == null) {
            throw new RuntimeException("Novo valor é obrigatório");
        }

        if (novoValor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Novo valor deve ser maior que zero");
        }

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());

        if (novoValor.compareTo(valorPagoAtual) < 0) {
            throw new RuntimeException("Novo valor não pode ser menor que o valor já pago na parcela");
        }

        parcela.setDataVencimento(novaData);
        parcela.setValorParcela(novoValor);

        atualizarStatusParcelaAposAlteracao(parcela, novaData, valorPagoAtual, novoValor);

        parcelaRepositorio.save(parcela);

        recalcularTotaisEmprestimo(emprestimo);
    }

    @Transactional
    public void alterarDataVencimento(Long idEmprestimo,
                                      Integer numeroParcela,
                                      LocalDate novaData) {

        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new RuntimeException("Parcela já paga");
        }

        if (novaData == null) {
            throw new RuntimeException("Nova data é obrigatória");
        }

        if (novaData.isBefore(LocalDate.now())) {
            throw new RuntimeException("Data inválida");
        }

        parcela.setDataVencimento(novaData);

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());
        BigDecimal valorParcela = safe(parcela.getValorParcela());

        atualizarStatusParcelaAposAlteracao(parcela, novaData, valorPagoAtual, valorParcela);

        parcelaRepositorio.save(parcela);
    }

    private void atualizarStatusParcelaAposAlteracao(Parcelas parcela,
                                                     LocalDate novaData,
                                                     BigDecimal valorPagoAtual,
                                                     BigDecimal valorParcela) {

        if (valorPagoAtual.compareTo(BigDecimal.ZERO) == 0) {
            if (novaData.isBefore(LocalDate.now())) {
                parcela.setStatus(ParcelaStatus.ATRASADO);
            } else {
                parcela.setStatus(ParcelaStatus.PENDENTE);
            }
            return;
        }

        if (valorPagoAtual.compareTo(valorParcela) >= 0) {
            parcela.setStatus(ParcelaStatus.PAGO);
            return;
        }

        if (novaData.isBefore(LocalDate.now())) {
            parcela.setStatus(ParcelaStatus.ATRASADO);
        } else {
            parcela.setStatus(ParcelaStatus.PARCIAL);
        }
    }

    private void recalcularTotaisEmprestimo(Emprestimo emprestimo) {
        List<Parcelas> parcelas = emprestimo.getParcelas();

        BigDecimal totalEmprestimo = parcelas.stream()
                .map(p -> safe(p.getValorParcela()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRecebido = parcelas.stream()
                .map(p -> safe(p.getValorPago()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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

        emprestimoRepositorio.save(emprestimo);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Parcelas buscarParcela(Long idEmprestimo, Integer numeroParcela) {
        return parcelaRepositorio
                .findByEmprestimoIdAndNumeroParcela(idEmprestimo, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));
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
}