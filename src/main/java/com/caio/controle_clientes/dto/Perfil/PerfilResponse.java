package com.caio.controle_clientes.dto.Perfil;

import com.caio.controle_clientes.models.Perfil;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PerfilResponse {
    private Long id;
    private String nome;

    public static PerfilResponse fromEntity(Perfil perfil) {
        return PerfilResponse.builder()
                .id(perfil.getId())
                .nome(perfil.getNome())
                .build();
    }
}