package com.caio.controle_clientes.controllers;


import com.caio.controle_clientes.dto.parcela.CriarObservacaoParcelaDTO;
import com.caio.controle_clientes.dto.parcela.EditarObservacaoParcelaDTO;
import com.caio.controle_clientes.dto.parcela.ParcelaObservacaoResponseDTO;
import com.caio.controle_clientes.services.parcela.ParcelaObservacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/parcelas/observacoes")
@RequiredArgsConstructor
public class ParcelaObservacaoController {
    private final ParcelaObservacaoService observacaoService;

    @PostMapping
    public ResponseEntity<Void> criarObservacao(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestBody CriarObservacaoParcelaDTO dto
    ) {
        observacaoService.criarObservacao(
                perfilId,
                dto.idEmprestimo(),
                dto.numeroParcela(),
                dto.observacao()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ParcelaObservacaoResponseDTO>> listarObservacoes(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestParam Long idEmprestimo,
            @RequestParam Integer numeroParcela
    ) {
        return ResponseEntity.ok(
                observacaoService.listarObservacoes(perfilId, idEmprestimo, numeroParcela)
        );
    }

    @PatchMapping
    public ResponseEntity<Void> editarObservacao(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestBody EditarObservacaoParcelaDTO dto
    ) {
        observacaoService.editarObservacao(
                perfilId,
                dto.idObservacao(),
                dto.observacao()
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{idObservacao}")
    public ResponseEntity<Void> removerObservacao(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idObservacao
    ) {
        observacaoService.removerObservacao(perfilId, idObservacao);
        return ResponseEntity.noContent().build();
    }
}