package com.caio.controle_clientes.dto.cliente;

import com.caio.controle_clientes.models.EstadoCivil;
import com.caio.controle_clientes.models.SexoDocumento;
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
        Integer enderecoNumero,

        String nacionalidade,
        EstadoCivil estadoCivil,
        String profissao,
        @com.caio.controle_clientes.validation.RgValido String rg,
        String rgOrgaoExpedidor,
        String cpf,
        String enderecoComplemento,
        String enderecoCep,
        String enderecoCidade,
        String enderecoEstado,
        SexoDocumento sexoDocumento
) {
    public ClienteCreateDTO(String nome, String nomeIndicador, String enderecoRua, String enderecoBairro, Integer enderecoNumero, String nacionalidade, EstadoCivil estadoCivil, String profissao, String rg, String rgOrgaoExpedidor, String cpf, String enderecoComplemento, String enderecoCep, String enderecoCidade, String enderecoEstado) {
        this(nome, nomeIndicador, enderecoRua, enderecoBairro, enderecoNumero, nacionalidade, estadoCivil, profissao, rg, rgOrgaoExpedidor, cpf, enderecoComplemento, enderecoCep, enderecoCidade, enderecoEstado, null);
    }

    public ClienteCreateDTO(String nome, String nomeIndicador, String enderecoRua,
                            String enderecoBairro, Integer enderecoNumero) {
        this(nome, nomeIndicador, enderecoRua, enderecoBairro, enderecoNumero,
                null, null, null, null, null, null, null, null, null, null);
    }
}
