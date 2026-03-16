package com.caio.controle_clientes.dto.parcela;

import java.math.BigDecimal;

public record PagarParcelaParcialDTO(
        Long idEmprestimo,
        Integer numeroParcela,
        BigDecimal valorPago
) {}
