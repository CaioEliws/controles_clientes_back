package com.caio.controle_clientes.dto.contrato;

public record ContratoPreviaDTO(boolean validoParaGeracao, DadosContratoDTO dados,
        TestemunhaFixaDTO testemunha) {}
