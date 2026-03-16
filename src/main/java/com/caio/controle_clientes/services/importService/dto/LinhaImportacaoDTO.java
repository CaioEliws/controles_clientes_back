package com.caio.controle_clientes.services.importService.dto;

import com.caio.controle_clientes.models.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LinhaImportacaoDTO(
        LocalDate dataEmprestimo,
        String nomeClienteOriginal,
        String nomeCliente,
        String nomeIndicador,
        FormaPagamento formaPagamento,
        TipoContrato tipoContrato,
        FrequenciaPagamento frequenciaPagamento,
        BigDecimal valorEmprestado,
        Integer totalParcelas,
        Integer parcelasPagas,
        BigDecimal valorAReceber,
        BigDecimal valorRecebido,
        BigDecimal valorTotalEmprestimos,
        BigDecimal valorParcela,
        BigDecimal taxaJurosPercentual,
        LocalDate inicioPgto,
        LocalDate fimPgto,
        EmprestimoStatus situacao
) {
}