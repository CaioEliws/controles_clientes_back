package com.caio.controle_clientes.services.parcela;

import com.caio.controle_clientes.exceptions.RegraNegocioException;

import com.caio.controle_clientes.dto.parcela.ParcelaResponseDTO;
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
    public void atualizarParcelasAtrasadas(Long perfilId) {
        parcelaRepositorio.atualizarStatusAtrasadas(perfilId, LocalDate.now(),
                List.of(ParcelaStatus.PENDENTE, ParcelaStatus.PARCIAL), ParcelaStatus.ATRASADO);
    }

    @Transactional
    public void desfazerPagamentoParcela(Long perfilId,
                                         Long idEmprestimo,
                                         Integer numeroParcela) {
        bloquearEmprestimo(perfilId, idEmprestimo);
        Parcelas parcela = buscarParcela(perfilId, idEmprestimo, numeroParcela);
        Emprestimo emprestimo = parcela.getEmprestimo();

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());

        if (valorPagoAtual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("A parcela não possui pagamento para desfazer");
        }

        parcela.setValorPago(BigDecimal.ZERO);
        parcela.setDataPagamento(null);

        if (parcela.getDataVencimento().isBefore(LocalDate.now())) {
            parcela.setStatus(ParcelaStatus.ATRASADO);
        } else {
            parcela.setStatus(ParcelaStatus.PENDENTE);
        }

        parcelaRepositorio.save(parcela);

        recalcularTotaisEmprestimo(emprestimo);
    }

    @Transactional
    public void alterarParcela(Long perfilId,
                               Long idEmprestimo,
                               Integer numeroParcela,
                               LocalDate novaData,
                               BigDecimal novoValor) {
        bloquearEmprestimo(perfilId, idEmprestimo);
        Parcelas parcela = buscarParcela(perfilId, idEmprestimo, numeroParcela);
        Emprestimo emprestimo = parcela.getEmprestimo();

        if (novaData == null) {
            throw new RegraNegocioException("Nova data é obrigatória");
        }

        if (novoValor == null) {
            throw new RegraNegocioException("Novo valor é obrigatório");
        }

        if (novoValor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Novo valor deve ser maior que zero");
        }

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());

        if (novoValor.compareTo(valorPagoAtual) < 0) {
            throw new RegraNegocioException("Novo valor não pode ser menor que o valor já pago na parcela");
        }

        validarDataAlteracao(parcela, novaData);

        parcela.setDataVencimento(novaData);
        parcela.setValorParcela(novoValor);

        atualizarStatusParcelaAposAlteracao(parcela, novaData, valorPagoAtual, novoValor);

        parcelaRepositorio.save(parcela);

        recalcularTotaisEmprestimo(emprestimo);
    }

    @Transactional
    public void alterarDataVencimento(Long perfilId,
                                      Long idEmprestimo,
                                      Integer numeroParcela,
                                      LocalDate novaData) {

        Parcelas parcela = buscarParcela(perfilId, idEmprestimo, numeroParcela);

        if (novaData == null) {
            throw new RegraNegocioException("Nova data é obrigatória");
        }

        validarDataAlteracao(parcela, novaData);

        parcela.setDataVencimento(novaData);

        BigDecimal valorPagoAtual = safe(parcela.getValorPago());
        BigDecimal valorParcela = safe(parcela.getValorParcela());

        atualizarStatusParcelaAposAlteracao(parcela, novaData, valorPagoAtual, valorParcela);

        parcelaRepositorio.save(parcela);

        recalcularTotaisEmprestimo(parcela.getEmprestimo());
    }

    private void validarDataAlteracao(Parcelas parcela, LocalDate novaData) {
        boolean parcelaNaoEstaPaga = parcela.getStatus() != ParcelaStatus.PAGO;

        if (parcelaNaoEstaPaga && novaData.isBefore(LocalDate.now())) {
            throw new RegraNegocioException("Data inválida para parcela não paga");
        }
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

    private void bloquearEmprestimo(Long perfilId, Long idEmprestimo) {
        emprestimoRepositorio.bloquearParaAtualizacao(idEmprestimo, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Empréstimo não encontrado"));
    }

    private Parcelas buscarParcela(Long perfilId,
                                   Long idEmprestimo,
                                   Integer numeroParcela) {

        return parcelaRepositorio
                .findByEmprestimoIdAndPerfilIdAndNumeroParcela(
                        idEmprestimo,
                        perfilId,
                        numeroParcela
                )
                .orElseThrow(() -> new RegraNegocioException("Parcela não encontrada"));
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
