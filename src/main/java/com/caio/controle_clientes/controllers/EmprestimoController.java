package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.emprestimo.EmprestimoCreateDTO;
import com.caio.controle_clientes.dto.emprestimo.EmprestimoDetalhadoDTO;
import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.Parcelas;
import com.caio.controle_clientes.services.ClienteService;
import com.caio.controle_clientes.services.EmprestimoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes/{idCliente}/emprestimos")
@RequiredArgsConstructor
public class EmprestimoController {

    private final EmprestimoService emprestimoService;
    private final ClienteService clienteService;

    @PostMapping
    public ResponseEntity<Void> criarEmprestimo(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente,
            @RequestBody EmprestimoCreateDTO dto
    ) {
        Cliente cliente = clienteService.getClienteById(perfilId, idCliente);

        emprestimoService.criarEmprestimo(
                perfilId,
                cliente,
                dto.valorEmprestado(),
                dto.quantidadeParcelas(),
                dto.jurosCobrado(),
                dto.formaPagamento(),
                dto.frequenciaPagamento(),
                dto.tipoContrato()
        );

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<Emprestimo>> listar(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente
    ) {
        Cliente cliente = clienteService.getClienteById(perfilId, idCliente);
        return ResponseEntity.ok(emprestimoService.listarEmprestimosCliente(perfilId, cliente));
    }

    @GetMapping("/{idEmprestimo}/parcelas")
    public ResponseEntity<List<Parcelas>> listarParcelas(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente,
            @PathVariable Long idEmprestimo
    ) {
        return ResponseEntity.ok(
                emprestimoService.listarParcelasDoCliente(perfilId, idCliente, idEmprestimo)
        );
    }

    @DeleteMapping("/{idEmprestimo}")
    public ResponseEntity<Void> deletar(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente,
            @PathVariable Long idEmprestimo
    ) {
        emprestimoService.deletarEmprestimo(perfilId, idCliente, idEmprestimo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/detalhes")
    public ResponseEntity<List<EmprestimoDetalhadoDTO>> listarDetalhado(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente
    ) {
        Cliente cliente = clienteService.getClienteById(perfilId, idCliente);
        return ResponseEntity.ok(emprestimoService.listarDetalhado(perfilId, cliente));
    }

    @PostMapping("/{idEmprestimo}/refinanciar")
    public ResponseEntity<Void> refinanciar(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente,
            @PathVariable Long idEmprestimo
    ) {
        emprestimoService.refinanciarEmprestimo(perfilId, idCliente, idEmprestimo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{idEmprestimo}/quitar")
    public ResponseEntity<Void> quitar(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente,
            @PathVariable Long idEmprestimo
    ) {
        emprestimoService.quitarEmprestimo(perfilId, idCliente, idEmprestimo);
        return ResponseEntity.ok().build();
    }
}