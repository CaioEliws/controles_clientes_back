package com.caio.controle_clientes.dto.Perfil;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CriarPerfilRequest {
    @NotBlank(message = "O nome do perfil é obrigatório")
    private String nome;
}