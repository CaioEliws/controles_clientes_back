package com.caio.controle_clientes.dto.emprestimo;

import com.caio.controle_clientes.models.FormaPagamento;
import com.caio.controle_clientes.models.FrequenciaPagamento;
import com.caio.controle_clientes.models.TipoContrato;

import java.math.BigDecimal;

public record EmprestimoCreateDTO(
        BigDecimal valorEmprestado,
        int quantidadeParcelas,
        BigDecimal jurosCobrado,
        FormaPagamento formaPagamento,
        FrequenciaPagamento frequenciaPagamento,
        TipoContrato tipoContrato
) {}