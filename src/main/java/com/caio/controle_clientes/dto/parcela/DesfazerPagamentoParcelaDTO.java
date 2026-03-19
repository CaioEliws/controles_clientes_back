package com.caio.controle_clientes.dto.parcela;

public record DesfazerPagamentoParcelaDTO(
        Long idEmprestimo,
        Integer numeroParcela
) {
}