package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Emprestimo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface EmprestimoRepositorio extends JpaRepository<Emprestimo, Long> {
    List<Emprestimo> findAllByCliente(Cliente cliente);

    @Query("SELECT e FROM Emprestimo e JOIN FETCH e.parcelas WHERE e.id = :id")
    Optional<Emprestimo> buscarComParcelas(Long id);

    List<Emprestimo> findByCliente(Cliente cliente);

    boolean existsByClienteId(Long clienteId);
}
