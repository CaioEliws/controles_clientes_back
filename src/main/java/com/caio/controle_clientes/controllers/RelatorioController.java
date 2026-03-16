package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.InadimplenteDTO;
import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.services.ClienteService;
import com.caio.controle_clientes.services.RelatorioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final ClienteService clienteService;

    public RelatorioController(RelatorioService relatorioService,
                               ClienteService clienteService) {
        this.relatorioService = relatorioService;
        this.clienteService = clienteService;
    }

    @GetMapping("/clientes/{idCliente}")
    public ResponseEntity<?> historicoCliente(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente
    ) {
        Cliente cliente = clienteService.getClienteById(perfilId, idCliente);
        return ResponseEntity.ok(
                relatorioService.gerarHistoricoCliente(perfilId, cliente)
        );
    }

    @GetMapping("/inadimplentes")
    public ResponseEntity<List<InadimplenteDTO>> inadimplentes(
            @RequestHeader("X-Perfil-Id") Long perfilId
    ) {
        return ResponseEntity.ok(relatorioService.gerarRelatorioInadimplentes(perfilId));
    }
}