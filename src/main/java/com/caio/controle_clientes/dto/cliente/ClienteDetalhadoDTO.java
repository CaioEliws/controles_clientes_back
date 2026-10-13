package com.caio.controle_clientes.dto.cliente;

import com.caio.controle_clientes.models.EstadoCivil;
import com.caio.controle_clientes.models.SexoDocumento;
import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.services.contrato.ContratoFormatador;

public record ClienteDetalhadoDTO(
        Long id,
        String nome,
        String nomeIndicador,
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
        SexoDocumento sexoDocumento,
        Long quantidadeEmprestimos
) {
    public ClienteDetalhadoDTO(Long id, String nome, String nomeIndicador, String nacionalidade, EstadoCivil estadoCivil, String profissao, String rg, String rgOrgaoExpedidor, String cpf, String enderecoRua, Integer enderecoNumero, String enderecoComplemento, String enderecoBairro, String enderecoCep, String enderecoCidade, String enderecoEstado) {
        this(id, nome, nomeIndicador, nacionalidade, estadoCivil, profissao, rg, rgOrgaoExpedidor, cpf, enderecoRua, enderecoNumero, enderecoComplemento, enderecoBairro, enderecoCep, enderecoCidade, enderecoEstado, null);
    }

    public static ClienteDetalhadoDTO fromEntity(Cliente entity) {
        return fromEntity(entity, null);
    }

    public ClienteDetalhadoDTO(Long id, String nome, String nomeIndicador, String nacionalidade, EstadoCivil estadoCivil, String profissao, String rg, String rgOrgaoExpedidor, String cpf, String enderecoRua, Integer enderecoNumero, String enderecoComplemento, String enderecoBairro, String enderecoCep, String enderecoCidade, String enderecoEstado, SexoDocumento sexoDocumento) {
        this(id, nome, nomeIndicador, nacionalidade, estadoCivil, profissao, rg, rgOrgaoExpedidor, cpf, enderecoRua, enderecoNumero, enderecoComplemento, enderecoBairro, enderecoCep, enderecoCidade, enderecoEstado, sexoDocumento, null);
    }

    public static ClienteDetalhadoDTO fromEntity(Cliente entity, Long quantidadeEmprestimos) {
        return new ClienteDetalhadoDTO(
                entity.getId(),
                entity.getNome(),
                entity.getNomeIndicador(),
                entity.getNacionalidade(),
                entity.getEstadoCivil(),
                entity.getProfissao(),
                com.caio.controle_clientes.validation.RgUtil.formatar(entity.getRg(),
                        com.caio.controle_clientes.validation.RgUtil.extrairUf(entity.getRgOrgaoExpedidor())),
                ContratoFormatador.orgaoExpedidor(entity.getRgOrgaoExpedidor()),
                ContratoFormatador.cpf(entity.getCpf()),
                entity.getEnderecoRua(),
                entity.getEnderecoNumero(),
                entity.getEnderecoComplemento(),
                entity.getEnderecoBairro(),
                ContratoFormatador.cep(entity.getEnderecoCep()),
                entity.getEnderecoCidade(),
                ContratoFormatador.uf(entity.getEnderecoEstado()),
                entity.getSexoDocumento(),
                quantidadeEmprestimos
        );
    }
}

