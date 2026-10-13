package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.contrato.DadosContratoDTO;
import com.caio.controle_clientes.services.contrato.ContratoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clientes/{idCliente}/emprestimos")
@RequiredArgsConstructor
public class ContratoController {
    private final ContratoService contratoService;

    @GetMapping("/{idEmprestimo}/dados-contrato")
    public DadosContratoDTO buscarDadosContrato(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente,
            @PathVariable Long idEmprestimo
    ) {
        return contratoService.buscarDadosContrato(perfilId, idCliente, idEmprestimo);
    }
}
