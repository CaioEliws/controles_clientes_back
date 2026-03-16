package com.caio.controle_clientes.services.importService.dto;

public record ErroImportacao(
        int linha,
        String motivo
) {
}