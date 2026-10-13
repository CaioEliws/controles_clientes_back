package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParcelaRepositorio extends JpaRepository<Parcelas, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Parcelas p SET p.status = :atrasado
        WHERE p.perfil.id = :perfilId AND p.dataVencimento < :hoje
          AND p.status IN :statuses
          AND COALESCE(p.valorPago, 0) < p.valorParcela
    """)
    int atualizarStatusAtrasadas(@Param("perfilId") Long perfilId, @Param("hoje") LocalDate hoje,
                               @Param("statuses") List<ParcelaStatus> statuses,
                               @Param("atrasado") ParcelaStatus atrasado);

    Optional<Parcelas> findByIdAndPerfilId(Long id, Long perfilId);

    Optional<Parcelas> findByEmprestimoIdAndPerfilIdAndNumeroParcela(Long idEmprestimo,
                                                                     Long perfilId,
                                                                     Integer numeroParcela);

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.status = :status
          AND p.perfil.id = :perfilId
        ORDER BY p.dataVencimento ASC
    """)
    List<Parcelas> findParcelasComEmprestimoEClientePorStatus(@Param("status") ParcelaStatus status,
                                                              @Param("perfilId") Long perfilId);

    List<Parcelas> findByStatusAndPerfilIdAndDataVencimentoBefore(ParcelaStatus status,
                                                                  Long perfilId,
                                                                  LocalDate data);

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.dataVencimento < :data
          AND p.status IN :statuses
          AND p.perfil.id = :perfilId
        ORDER BY p.dataVencimento ASC
    """)
    List<Parcelas> findParcelasEmAbertoVencidas(@Param("data") LocalDate data,
                                                @Param("statuses") List<ParcelaStatus> statuses,
                                                @Param("perfilId") Long perfilId);

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.dataVencimento = :data
          AND p.status IN :statuses
          AND p.perfil.id = :perfilId
        ORDER BY p.numeroParcela ASC
    """)
    List<Parcelas> findParcelasVencendoHoje(@Param("data") LocalDate data,
                                            @Param("statuses") List<ParcelaStatus> statuses,
                                            @Param("perfilId") Long perfilId);

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.dataVencimento BETWEEN :inicio AND :fim
          AND p.perfil.id = :perfilId
        ORDER BY
          CASE
            WHEN p.status = 'ATRASADO' THEN 1
            WHEN p.status = 'PARCIAL' THEN 2
            WHEN p.status = 'PENDENTE' THEN 3
            WHEN p.status = 'PAGO' THEN 4
          END,
          p.dataVencimento ASC
    """)
    List<Parcelas> findParcelasDoMes(@Param("inicio") LocalDate inicio,
                                     @Param("fim") LocalDate fim,
                                     @Param("perfilId") Long perfilId);

    List<Parcelas> findByEmprestimoAndPerfilId(Emprestimo emprestimo, Long perfilId);

    @Query("""
        SELECT p
        FROM Parcelas p
        JOIN FETCH p.emprestimo e
        JOIN FETCH e.cliente c
        WHERE p.dataVencimento < :hoje
          AND p.status IN :statuses
          AND p.perfil.id = :perfilId
        ORDER BY p.dataVencimento ASC
    """)
    List<Parcelas> findParcelasAtrasadasComCliente(@Param("hoje") LocalDate hoje,
                                                   @Param("statuses") List<ParcelaStatus> statuses,
                                                   @Param("perfilId") Long perfilId);
}
