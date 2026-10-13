package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Emprestimo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmprestimoRepositorio extends JpaRepository<Emprestimo, Long> {
    interface ContagemPorCliente {
        Long getClienteId();
        Long getQuantidade();
    }

    @Query("SELECT e.cliente.id AS clienteId, COUNT(e) AS quantidade FROM Emprestimo e WHERE e.perfil.id = :perfilId GROUP BY e.cliente.id")
    List<ContagemPorCliente> contarPorCliente(@Param("perfilId") Long perfilId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Emprestimo e WHERE e.id = :id AND e.perfil.id = :perfilId")
    Optional<Emprestimo> bloquearParaAtualizacao(@Param("id") Long id, @Param("perfilId") Long perfilId);

    @EntityGraph(attributePaths = {"cliente", "parcelas", "fiador"})
    List<Emprestimo> findAllByPerfilId(Long perfilId);

    @EntityGraph(attributePaths = {"cliente", "parcelas", "fiador"})
    List<Emprestimo> findAllByClienteAndPerfilId(Cliente cliente, Long perfilId);

    List<Emprestimo> findByClienteAndPerfilId(Cliente cliente, Long perfilId);

    Optional<Emprestimo> findByIdAndPerfilId(Long id, Long perfilId);

    @Query("""
        SELECT DISTINCT e FROM Emprestimo e
        JOIN FETCH e.perfil
        LEFT JOIN FETCH e.cliente
        LEFT JOIN FETCH e.fiador
        LEFT JOIN FETCH e.parcelas
        WHERE e.id = :id AND e.perfil.id = :perfilId
    """)
    Optional<Emprestimo> buscarDadosContrato(@Param("id") Long id,
                                           @Param("perfilId") Long perfilId);

    boolean existsByClienteIdAndPerfilId(Long clienteId, Long perfilId);

    boolean existsByFiadorIdAndPerfilId(Long fiadorId, Long perfilId);

    boolean existsByClienteIdAndPerfilIdAndDataDoEmprestimoAndValorEmprestadoAndInicioPagamentoAndQuantidadeParcelas(
            Long clienteId,
            Long perfilId,
            LocalDate dataDoEmprestimo,
            BigDecimal valorEmprestado,
            LocalDate inicioPagamento,
            Integer quantidadeParcelas
    );

    @Query("""
        SELECT e
        FROM Emprestimo e
        JOIN FETCH e.parcelas
        WHERE e.id = :id
          AND e.perfil.id = :perfilId
    """)
    Optional<Emprestimo> buscarComParcelas(@Param("id") Long id,
                                           @Param("perfilId") Long perfilId);
}
