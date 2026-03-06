package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParcelaRepositorio extends JpaRepository<Parcelas, Long> {

    List<Parcelas> findByEmprestimo(Emprestimo emprestimo);

    List<Parcelas> findByStatusAndDataVencimentoBefore(
            ParcelaStatus status,
            LocalDate data
    );

    Optional<Parcelas> findByEmprestimoIdAndNumeroParcela(
            Long idEmprestimo,
            Integer numeroParcela
    );

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.status = :status
        ORDER BY p.dataVencimento ASC
    """)
    List<Parcelas> findParcelasComEmprestimoEClientePorStatus(
            @Param("status") ParcelaStatus status
    );

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.dataVencimento = :data
          AND p.status IN :statuses
        ORDER BY c.nome ASC, p.numeroParcela ASC
    """)
    List<Parcelas> findParcelasVencendoHoje(
            @Param("statuses") List<ParcelaStatus> statuses,
            @Param("data") LocalDate data
    );

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.dataVencimento BETWEEN :inicio AND :fim
        ORDER BY p.dataVencimento ASC
    """)
    List<Parcelas> findParcelasDoMes(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim
    );

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.status = :status
        ORDER BY c.nome ASC, p.dataVencimento ASC
    """)
    List<Parcelas> findParcelasAtrasadasComCliente(
            @Param("status") ParcelaStatus status
    );
}