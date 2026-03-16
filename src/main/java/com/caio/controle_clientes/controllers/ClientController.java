package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.cliente.ClienteCreateDTO;
import com.caio.controle_clientes.dto.cliente.UpdateEnderecoDTO;
import com.caio.controle_clientes.dto.cliente.UpdateNomeClienteDTO;
import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.services.ClienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
public class ClientController {

    private final ClienteService clienteService;

    public ClientController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    public ResponseEntity<Void> criarCliente(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestBody ClienteCreateDTO dto
    ) {
        clienteService.criarCliente(
                perfilId,
                dto.nome(),
                dto.nomeIndicador(),
                dto.enderecoRua(),
                dto.enderecoBairro(),
                dto.enderecoNumero()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCliente(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long id
    ) {
        clienteService.deletarCliente(perfilId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/name")
    public ResponseEntity<Void> updateName(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long id,
            @RequestBody UpdateNomeClienteDTO dto
    ) {
        clienteService.updateNameCliente(perfilId, id, dto.getNome());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/endereco")
    public ResponseEntity<Void> updateEndereco(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long id,
            @RequestBody UpdateEnderecoDTO dto
    ) {
        clienteService.updateEnderecoCliente(
                perfilId,
                id,
                dto.getRua(),
                dto.getBairro(),
                dto.getNumero()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Cliente>> listarClientes(
            @RequestHeader("X-Perfil-Id") Long perfilId
    ) {
        return ResponseEntity.ok(clienteService.listarTodos(perfilId));
    }
}