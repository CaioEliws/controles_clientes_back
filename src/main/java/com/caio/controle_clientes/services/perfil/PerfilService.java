package com.caio.controle_clientes.services.perfil;

import com.caio.controle_clientes.dto.Perfil.CriarPerfilRequest;
import com.caio.controle_clientes.dto.Perfil.PerfilResponse;
import com.caio.controle_clientes.models.Perfil;
import com.caio.controle_clientes.repository.PerfilRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PerfilService {

    private final PerfilRepositorio perfilRepositorio;

    public PerfilService(PerfilRepositorio perfilRepositorio) {
        this.perfilRepositorio = perfilRepositorio;
    }

    public List<PerfilResponse> listarTodos() {
        return perfilRepositorio.findAll()
                .stream()
                .map(PerfilResponse::fromEntity)
                .toList();
    }

    public PerfilResponse criar(CriarPerfilRequest request) {
        String nome = request.getNome().trim();

        perfilRepositorio.findByNomeIgnoreCase(nome)
                .ifPresent(p -> {
                    throw new RuntimeException("Já existe um perfil com esse nome");
                });

        Perfil perfil = new Perfil();
        perfil.setNome(nome);

        Perfil salvo = perfilRepositorio.save(perfil);

        return PerfilResponse.fromEntity(salvo);
    }

    public Perfil buscarPorId(Long perfilId) {
        return perfilRepositorio.findById(perfilId)
                .orElseThrow(() -> new RuntimeException("Perfil não encontrado"));
    }
}