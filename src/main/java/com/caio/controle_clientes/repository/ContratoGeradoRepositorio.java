package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.ContratoGerado;
import com.caio.controle_clientes.dto.contrato.ContratoGeradoMetadadosDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ContratoGeradoRepositorio extends JpaRepository<ContratoGerado, Long> {
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ContratoGerado c WHERE c.emprestimo.id = :emprestimoId")
    int deleteByEmprestimoId(@Param("emprestimoId") Long emprestimoId);

    @Query("""
        SELECT new com.caio.controle_clientes.dto.contrato.ContratoGeradoMetadadosDTO(
            c.id, c.emprestimo.id, c.versaoTemplate, c.dataGeracao, c.nomeArquivo, c.hashDocumento,
            CASE WHEN c.hashPdfFinal IS NOT NULL THEN true ELSE false END,
            CASE WHEN c.hashPdfAssinado IS NOT NULL THEN true ELSE false END)
        FROM ContratoGerado c WHERE c.perfil.id = :perfilId AND c.emprestimo.id = :emprestimoId
        AND c.emprestimo.cliente.id = :clienteId ORDER BY c.dataGeracao DESC, c.id DESC
    """)
    List<ContratoGeradoMetadadosDTO> listar(@Param("perfilId") Long perfilId,
            @Param("clienteId") Long clienteId, @Param("emprestimoId") Long emprestimoId);

    @Query("""
        SELECT c FROM ContratoGerado c WHERE c.id = :id AND c.perfil.id = :perfilId
        AND c.emprestimo.id = :emprestimoId AND c.emprestimo.perfil.id = :perfilId
        AND c.emprestimo.cliente.id = :clienteId
    """)
    Optional<ContratoGerado> buscar(@Param("id") Long id, @Param("perfilId") Long perfilId,
            @Param("clienteId") Long clienteId, @Param("emprestimoId") Long emprestimoId);
}
