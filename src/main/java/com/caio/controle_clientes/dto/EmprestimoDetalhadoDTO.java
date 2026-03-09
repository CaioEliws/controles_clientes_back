package com.caio.controle_clientes.dto;

import com.caio.controle_clientes.models.EmprestimoStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmprestimoDetalhadoDTO(
        Long id,
        String nomeCliente,
        LocalDate dataEmprestimo,
        String formaPagamento,
        String frequenciaPagamento,
        String tipoContrato,
        BigDecimal valorEmprestado,
        BigDecimal valorTotalEmprestimo,
        BigDecimal valorParcela,
        BigDecimal valorRecebido,
        BigDecimal valorAReceber,
        LocalDate inicioPagamento,
        LocalDate finalPagamento,
        EmprestimoStatus status,
        Integer quantidadeParcelas,
        Long parcelasPagas,
        Long parcelasPendentes,
        Long parcelasAtrasadas
) {}