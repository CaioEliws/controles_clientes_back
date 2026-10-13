package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByNomeIgnoreCaseAndPerfilId(String nome, Long perfilId);

    Optional<Cliente> findByIdAndPerfilId(Long id, Long perfilId);

    List<Cliente> findAllByPerfilId(Long perfilId);
    interface IdentidadeCliente {
        Long getId(); String getNome(); String getCpf();
    }
    @Query("select c.id as id, c.nome as nome, c.cpf as cpf from Cliente c where c.perfil.id = :perfilId")
    List<IdentidadeCliente> listarIdentidades(@Param("perfilId") Long perfilId);

    @Query("select c from Cliente c where c.perfil.id = :perfilId and replace(replace(replace(c.cpf, '.', ''), '-', ''), ' ', '') = :cpf")
    List<Cliente> buscarPorCpfNormalizado(@Param("perfilId") Long perfilId, @Param("cpf") String cpf);

    @Query("""
        SELECT c FROM Cliente c
        LEFT JOIN FETCH c.emprestimos
        WHERE c.id = :id
          AND c.perfil.id = :perfilId
    """)
    Optional<Cliente> buscarClienteComEmprestimos(@Param("id") Long id,
                                                  @Param("perfilId") Long perfilId);
}
