package com.caio.controle_clientes.dto.contrato;

import java.time.OffsetDateTime;

public record ContratoGeradoMetadadosDTO(Long id, Long emprestimoId, String versaoTemplate,
        OffsetDateTime dataGeracao, String nomeArquivo, String hashDocumento, boolean pdfDisponivel,
        boolean pdfAssinadoDisponivel) {}
