package com.caio.controle_clientes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AlterarParcelaDTO(
        Long idEmprestimo,
        Integer numeroParcela,
        LocalDate novaData,
        BigDecimal novoValor
) {
}