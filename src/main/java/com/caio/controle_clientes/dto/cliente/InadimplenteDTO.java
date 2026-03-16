package com.caio.controle_clientes.dto.cliente;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InadimplenteDTO(
        String nomeCliente,
        long quantidadeParcelasAtrasadas,
        BigDecimal valorTotalEmAtraso,
        LocalDate ultimoVencimento
) {}
