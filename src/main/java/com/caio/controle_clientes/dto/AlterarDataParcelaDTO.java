package com.caio.controle_clientes.dto;

import java.time.LocalDate;

public record AlterarDataParcelaDTO(
        Long idEmprestimo,
        Integer numeroParcela,
        LocalDate novaData
) {
}
