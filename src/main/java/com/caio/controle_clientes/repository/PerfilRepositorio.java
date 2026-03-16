package com.caio.controle_clientes.repository;

import com.caio.controle_clientes.models.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PerfilRepositorio extends JpaRepository<Perfil, Long> {
    Optional<Perfil> findByNomeIgnoreCase(String nome);
}
