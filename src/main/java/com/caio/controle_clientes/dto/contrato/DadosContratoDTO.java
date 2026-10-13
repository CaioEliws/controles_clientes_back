package com.caio.controle_clientes.dto.contrato;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.caio.controle_clientes.services.contrato.ContratoFormatador;

public record DadosContratoDTO(
        Long emprestimoId,
        ParteContratoDTO credor,
        ParteContratoDTO devedor,
        ParteContratoDTO fiador,
        EmprestimoContratoDTO emprestimo,
        List<ParcelaContratoDTO> parcelas
) {
    public DadosContratoDTO {
        parcelas = List.copyOf(parcelas);
    }

    @JsonProperty("camposDocumento")
    public Map<String, String> camposDocumento() {
        Map<String, String> campos = new LinkedHashMap<>();
        ContratoFormatador.pessoa(campos, "DEVEDOR", devedor);
        ContratoFormatador.pessoa(campos, "FIADOR", fiador);
        return campos;
    }
}
