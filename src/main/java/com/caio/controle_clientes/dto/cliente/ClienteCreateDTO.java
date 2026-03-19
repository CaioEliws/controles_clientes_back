package com.caio.controle_clientes.dto.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClienteCreateDTO(
        @NotBlank(message = "Nome é obrigatório")
        String nome,

        String nomeIndicador,

        @NotBlank(message = "Rua é obrigatória")
        String enderecoRua,

        @NotBlank(message = "Bairro é obrigatório")
        String enderecoBairro,

        @NotNull(message = "Número é obrigatório")
        Integer enderecoNumero
) {}