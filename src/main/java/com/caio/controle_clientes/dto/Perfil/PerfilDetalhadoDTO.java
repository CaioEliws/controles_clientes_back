package com.caio.controle_clientes.dto.Perfil;

import com.caio.controle_clientes.models.EstadoCivil;
import com.caio.controle_clientes.models.Perfil;

public record PerfilDetalhadoDTO(
        Long id,
        String nome,
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
        String enderecoEstado
) {
    public static PerfilDetalhadoDTO fromEntity(Perfil entity) {
        return new PerfilDetalhadoDTO(
                entity.getId(),
                entity.getNome(),
                entity.getNomeCompleto(),
                entity.getNacionalidade(),
                entity.getEstadoCivil(),
                entity.getProfissao(),
                com.caio.controle_clientes.validation.RgUtil.formatar(entity.getRg(),
                        com.caio.controle_clientes.validation.RgUtil.extrairUf(entity.getRgOrgaoExpedidor())),
                entity.getRgOrgaoExpedidor(),
                entity.getCpf(),
                entity.getEnderecoRua(),
                entity.getEnderecoNumero(),
                entity.getEnderecoComplemento(),
                entity.getEnderecoBairro(),
                entity.getEnderecoCep(),
                entity.getEnderecoCidade(),
                entity.getEnderecoEstado()
        );
    }
}

