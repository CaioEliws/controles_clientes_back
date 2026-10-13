package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.Perfil.CriarPerfilRequest;
import com.caio.controle_clientes.dto.Perfil.AtualizarPerfilDadosDTO;
import com.caio.controle_clientes.dto.Perfil.PerfilDetalhadoDTO;
import com.caio.controle_clientes.dto.Perfil.PerfilResponse;
import com.caio.controle_clientes.services.perfil.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/perfis")
@RequiredArgsConstructor
public class PerfilController {
    private final PerfilService perfilService;

    @GetMapping
    public List<PerfilResponse> listar() {
        return perfilService.listarTodos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PerfilResponse criar(@Valid @RequestBody CriarPerfilRequest request) {
        return perfilService.criar(request);
    }

    @GetMapping("/{id}")
    public PerfilDetalhadoDTO detalhar(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long id
    ) {
        return perfilService.buscarDetalhado(perfilId, id);
    }

    @PutMapping("/{id}")
    public PerfilDetalhadoDTO atualizarDados(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long id,
            @Valid @RequestBody AtualizarPerfilDadosDTO dto
    ) {
        return perfilService.atualizarDados(perfilId, id, dto);
    }
}
