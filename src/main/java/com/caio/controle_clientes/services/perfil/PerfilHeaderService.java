package com.caio.controle_clientes.services.perfil;

import com.caio.controle_clientes.models.Perfil;
import org.springframework.stereotype.Service;

@Service
public class PerfilHeaderService {
    private final PerfilService perfilService;

    public PerfilHeaderService(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    public Perfil resolverPerfil(Long perfilId) {
        if (perfilId == null) {
            throw new RuntimeException("Cabeçalho X-Perfil-Id não enviado.");
        }

        return perfilService.buscarPorId(perfilId);
    }
}
