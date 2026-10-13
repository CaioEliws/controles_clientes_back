package com.caio.controle_clientes.dto.contrato;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.caio.controle_clientes.models.EmprestimoStatus;
import com.caio.controle_clientes.models.FormaPagamento;
import com.caio.controle_clientes.models.FrequenciaPagamento;
import com.caio.controle_clientes.models.TipoContrato;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmprestimoContratoDTO(
        BigDecimal valorEmprestado,
        BigDecimal taxaDeJuros,
        BigDecimal valorParcela,
        BigDecimal valorTotalEmprestimo,
        Integer quantidadeParcelas,
        LocalDate dataDoEmprestimo,
        LocalDate inicioPagamento,
        LocalDate finalPagamento,
        FormaPagamento formaPagamento,
        FrequenciaPagamento frequenciaPagamento,
        TipoContrato tipoContrato,
        EmprestimoStatus emprestimoStatus
) {
    /** Nome explícito documental; valorTotalEmprestimo é mantido para os consumidores e snapshots antigos. */
    @JsonProperty(value = "valorTotalDivida", access = JsonProperty.Access.READ_ONLY)
    public BigDecimal valorTotalDivida() { return valorTotalEmprestimo; }
}
