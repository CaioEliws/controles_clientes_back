package com.caio.controle_clientes.dto.emprestimo;

import com.caio.controle_clientes.models.FormaPagamento;
import com.caio.controle_clientes.models.FrequenciaPagamento;
import com.caio.controle_clientes.models.TipoContrato;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record EmprestimoCreateDTO(
        BigDecimal valorEmprestado,
        int quantidadeParcelas,
        BigDecimal jurosCobrado,
        FormaPagamento formaPagamento,
        FrequenciaPagamento frequenciaPagamento,
        TipoContrato tipoContrato,
        @NotNull(message = "Fiador é obrigatório") Long fiadorId
) {
    public EmprestimoCreateDTO(BigDecimal valorEmprestado, int quantidadeParcelas,
                              BigDecimal jurosCobrado, FormaPagamento formaPagamento,
                              FrequenciaPagamento frequenciaPagamento, TipoContrato tipoContrato) {
        this(valorEmprestado, quantidadeParcelas, jurosCobrado, formaPagamento,
                frequenciaPagamento, tipoContrato, null);
    }
}
