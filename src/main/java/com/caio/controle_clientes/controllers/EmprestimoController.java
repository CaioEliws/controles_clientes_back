package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.EmprestimoCreateDTO;
import com.caio.controle_clientes.dto.EmprestimoDetalhadoDTO;
import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.Parcelas;
import com.caio.controle_clientes.services.ClienteService;
import com.caio.controle_clientes.services.EmprestimoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/clientes/{idCliente}/emprestimos")
public class EmprestimoController {
    private final EmprestimoService emprestimoService;
    private final ClienteService clienteService;

    public EmprestimoController(EmprestimoService emprestimoService,
                                ClienteService clienteService) {
        this.emprestimoService = emprestimoService;
        this.clienteService = clienteService;
    }

    @PostMapping
    public ResponseEntity<Void> criarEmprestimo(
            @PathVariable Long idCliente,
            @RequestBody EmprestimoCreateDTO dto
    ) {
        Cliente cliente = clienteService.getClienteById(idCliente);

        emprestimoService.criarEmprestimo(
                cliente,
                dto.valorEmprestado(),
                dto.quantidadeParcelas(),
                dto.jurosCobrado(),
                dto.formaPagamento()
        );

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public List<Emprestimo> listar(
            @PathVariable Long idCliente
    ) {
        Cliente cliente = clienteService.getClienteById(idCliente);
        return emprestimoService.listarEmprestimosCliente(cliente);
    }

    @GetMapping("/{idEmprestimo}/parcelas")
    public List<Parcelas> listarParcelas(
            @PathVariable Long idCliente,
            @PathVariable Long idEmprestimo
    ) {
        return emprestimoService.listarParcelasDoCliente(idCliente, idEmprestimo);
    }

    @DeleteMapping("/{idEmprestimo}")
    public ResponseEntity<Void> deletar(
            @PathVariable Long idCliente,
            @PathVariable Long idEmprestimo
    ) {
        emprestimoService.deletarEmprestimo(idCliente, idEmprestimo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/detalhes")
    public List<EmprestimoDetalhadoDTO> listarDetalhado(
            @PathVariable Long idCliente
    ) {
        Cliente cliente = clienteService.getClienteById(idCliente);
        return emprestimoService.listarDetalhado(cliente);
    }
}
