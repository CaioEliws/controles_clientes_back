package com.caio.controle_clientes.services;

import com.caio.controle_clientes.dto.ParcelaResponseDTO;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        List<Parcelas> atrasadas =
                parcelaRepositorio.findByStatusAndDataVencimentoBefore(
                        ParcelaStatus.PENDENTE, LocalDate.now()
                );

        atrasadas.forEach(p -> p.setStatus(ParcelaStatus.ATRASADO));

        parcelaRepositorio.saveAll(atrasadas);
    }

    @Transactional
    public void alterarDataVencimento(Long idEmprestimo,
                                      Integer numeroParcela,
                                      LocalDate novaData) {

        Parcelas parcela = buscarParcela(idEmprestimo, numeroParcela);

        if (parcela.getStatus() == ParcelaStatus.PAGO) {
            throw new RuntimeException("Parcela já paga");
        }

        if (novaData.isBefore(LocalDate.now())) {
            throw new RuntimeException("Data inválida");
        }

        parcela.setDataVencimento(novaData);

        if (parcela.getStatus() == ParcelaStatus.ATRASADO &&
                novaData.isAfter(LocalDate.now())) {
            parcela.setStatus(ParcelaStatus.PENDENTE);
        }

        parcelaRepositorio.save(parcela);
    }

    private Parcelas buscarParcela(Long idEmprestimo, Integer numeroParcela) {
        return (Parcelas) parcelaRepositorio
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