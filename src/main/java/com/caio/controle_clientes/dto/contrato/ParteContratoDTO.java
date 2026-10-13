package com.caio.controle_clientes.dto.contrato;

import com.caio.controle_clientes.models.EstadoCivil;
import com.caio.controle_clientes.models.SexoDocumento;

public record ParteContratoDTO(
        String nomeCompleto,
        String nacionalidade,
        EstadoCivil estadoCivil,
        String profissao,
        String rg,
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
    public ParteContratoDTO(String nomeCompleto, String nacionalidade, EstadoCivil estadoCivil, String profissao, String rg, String rgOrgaoExpedidor, String cpf, String enderecoRua, Integer enderecoNumero, String enderecoComplemento, String enderecoBairro, String enderecoCep, String enderecoCidade, String enderecoEstado) {
        this(nomeCompleto, nacionalidade, estadoCivil, profissao, rg, rgOrgaoExpedidor, cpf, enderecoRua, enderecoNumero, enderecoComplemento, enderecoBairro, enderecoCep, enderecoCidade, enderecoEstado, null);
    }

}
