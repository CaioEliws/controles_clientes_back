package com.caio.controle_clientes.dto.parcela;

public record CriarObservacaoParcelaDTO(
        Long idEmprestimo,
        Integer numeroParcela,
        String observacao
) {
}