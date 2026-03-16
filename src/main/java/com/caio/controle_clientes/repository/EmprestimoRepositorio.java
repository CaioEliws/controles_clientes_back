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

    @EntityGraph(attributePaths = {"cliente", "parcelas"})
    List<Emprestimo> findAllByPerfilId(Long perfilId);

    @EntityGraph(attributePaths = {"cliente", "parcelas"})
    List<Emprestimo> findAllByClienteAndPerfilId(Cliente cliente, Long perfilId);

    List<Emprestimo> findByClienteAndPerfilId(Cliente cliente, Long perfilId);

    Optional<Emprestimo> findByIdAndPerfilId(Long id, Long perfilId);

    boolean existsByClienteIdAndPerfilId(Long clienteId, Long perfilId);

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