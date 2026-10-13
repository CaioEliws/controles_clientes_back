package com.caio.controle_clientes.dto.contrato;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ContratoSnapshotDTO(
        Long emprestimoId, String versaoTemplate, LocalDate dataContrato,
        ParteContratoDTO devedor, ParteContratoDTO fiador,
        EmprestimoContratoDTO emprestimo, List<ParcelaContratoDTO> parcelas,
        ParteContratoDTO credorFixo, TestemunhaFixaDTO testemunhaFixa,
        Map<String, String> camposDocumento
) {
    public ContratoSnapshotDTO {
        parcelas = List.copyOf(parcelas);
        camposDocumento = Map.copyOf(camposDocumento);
    }
}
