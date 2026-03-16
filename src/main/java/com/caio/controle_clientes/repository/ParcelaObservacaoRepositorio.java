package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.ParcelaObservacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParcelaObservacaoRepositorio extends JpaRepository<ParcelaObservacao, Long> {
    List<ParcelaObservacao> findByParcelaIdAndPerfilIdOrderByDataCriacaoDesc(Long parcelaId, Long perfilId);

    Optional<ParcelaObservacao> findByIdAndPerfilId(Long id, Long perfilId);
}