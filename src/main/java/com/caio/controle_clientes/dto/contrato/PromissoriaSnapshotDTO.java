package com.caio.controle_clientes.dto.contrato;

import java.time.LocalDate;

public record PromissoriaSnapshotDTO(String versaoTemplate, String avalistaNome, String avalistaCpf,
        String emitenteNome, String emitenteCpf, String emitenteEndereco, String valor,
        String valorExtenso, LocalDate dataEmissao, LocalDate vencimentoPromissoria,
        String favorecido, String favorecidoCpf, String localPagamento) {}
