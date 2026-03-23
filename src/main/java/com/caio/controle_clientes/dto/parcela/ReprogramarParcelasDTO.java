package com.caio.controle_clientes.dto.parcela;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReprogramarParcelasDTO(
        LocalDate novaDataInicial,
        BigDecimal novoValorParcela
) {}