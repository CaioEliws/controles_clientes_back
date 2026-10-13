package com.caio.controle_clientes.dto.cliente;

import com.caio.controle_clientes.models.EstadoCivil;
import com.caio.controle_clientes.models.SexoDocumento;
import jakarta.validation.constraints.NotBlank;

// PUT substitui os dados cadastrais; campos opcionais ausentes são limpos.
public record AtualizarClienteDadosDTO(
        @NotBlank(message = "Nome é obrigatório")
        String nome,
        String nomeIndicador,
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
        String enderecoEstado,
        SexoDocumento sexoDocumento
) {
    public AtualizarClienteDadosDTO(String nome, String nomeIndicador, String nacionalidade, EstadoCivil estadoCivil, String profissao, String rg, String rgOrgaoExpedidor, String cpf, String enderecoRua, Integer enderecoNumero, String enderecoComplemento, String enderecoBairro, String enderecoCep, String enderecoCidade, String enderecoEstado) {
        this(nome, nomeIndicador, nacionalidade, estadoCivil, profissao, rg, rgOrgaoExpedidor, cpf, enderecoRua, enderecoNumero, enderecoComplemento, enderecoBairro, enderecoCep, enderecoCidade, enderecoEstado, null);
    }
}

