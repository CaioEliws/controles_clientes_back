package com.caio.controle_clientes.dto.Perfil;

import com.caio.controle_clientes.models.EstadoCivil;
import jakarta.validation.constraints.NotBlank;

// PUT substitui os dados cadastrais; campos opcionais ausentes são limpos.
public record AtualizarPerfilDadosDTO(
        @NotBlank(message = "Nome é obrigatório")
        String nome,
        String nomeCompleto,
        String nacionalidade,
        EstadoCivil estadoCivil,
        String profissao,
        @com.caio.controle_clientes.validation.RgValido String rg,
        String rgOrgaoExpedidor,
        String cpf,
        String enderecoRua,
        Integer enderecoNumero,
        String enderecoComplemento,
        String enderecoBairro,
        String enderecoCep,
        String enderecoCidade,
        String enderecoEstado
) {}

