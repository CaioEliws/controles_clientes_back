package com.caio.controle_clientes.dto.cliente;

import java.math.BigDecimal;

public record HistoricoClienteDTO(
        BigDecimal totalEmprestado,
        BigDecimal totalPago,
        BigDecimal totalAberto,
        int parcelasAtrasadas,
        int totalEmprestimos,
        int totalParcelas,
        int parcelasPagas,
        int parcelasAVencer
) {
}
