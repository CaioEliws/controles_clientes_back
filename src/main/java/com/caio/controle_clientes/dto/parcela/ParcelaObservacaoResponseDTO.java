package com.caio.controle_clientes.dto.parcela;

import java.time.LocalDateTime;

public record ParcelaObservacaoResponseDTO(
        Long id,
        String observacao,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao
) {
}