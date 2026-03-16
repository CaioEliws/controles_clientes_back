package com.caio.controle_clientes.dto.parcela;

import com.caio.controle_clientes.models.ParcelaStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaResponseDTO(
        Long idEmprestimo,
        String nomeCliente,
        Integer numeroParcela,
        BigDecimal valorParcela,
        BigDecimal valorPago,
        ParcelaStatus status,
        LocalDate dataVencimento
) {}
