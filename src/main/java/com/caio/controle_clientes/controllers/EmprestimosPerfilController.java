package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.emprestimo.EmprestimoDetalhadoDTO;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Leitura da carteira evita uma requisição por cliente no dashboard. */
@RestController
public class EmprestimosPerfilController {
    private final EmprestimoService service;
    public EmprestimosPerfilController(EmprestimoService service) { this.service = service; }
    @GetMapping("/emprestimos/detalhes")
    public List<EmprestimoDetalhadoDTO> listar(@RequestHeader("X-Perfil-Id") Long perfilId) {
        return service.listarDetalhadoDoPerfil(perfilId);
    }
}
