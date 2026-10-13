package com.caio.controle_clientes.dto.contrato;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaContratoDTO(
        Integer numeroParcela,
        LocalDate dataVencimento,
        BigDecimal valorParcela
) {
}
