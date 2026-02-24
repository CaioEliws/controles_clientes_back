package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByNomeIgnoreCase(String nome);

    @Query("""
        SELECT c FROM Cliente c
        LEFT JOIN FETCH c.emprestimos
        WHERE c.id = :id
    """)
    Optional<Cliente> buscarClienteComEmprestimos(@Param("id") Long id);
}
