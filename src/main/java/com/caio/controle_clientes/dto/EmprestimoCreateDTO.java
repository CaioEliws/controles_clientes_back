package com.caio.controle_clientes.dto;

import com.caio.controle_clientes.models.FormaPagamento;
import com.caio.controle_clientes.models.FrequenciaPagamento;

import java.math.BigDecimal;

public record EmprestimoCreateDTO(
        BigDecimal valorEmprestado,
        Integer quantidadeParcelas,
        BigDecimal jurosCobrado,
        FormaPagamento formaPagamento,
        FrequenciaPagamento frequenciaPagamento
) {}