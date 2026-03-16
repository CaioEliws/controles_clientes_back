package com.caio.controle_clientes.services.importService.dto;

import java.util.List;

public record ImportacaoResultado(
        int totalLinhas,
        int linhasImportadas,
        int linhasIgnoradas,
        int linhasDuplicadas,
        List<ErroImportacao> erros
) {
}
