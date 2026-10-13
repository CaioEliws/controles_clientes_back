package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.contrato.ContratoGeradoMetadadosDTO;
import com.caio.controle_clientes.dto.contrato.ContratoPreviaDTO;
import com.caio.controle_clientes.services.contrato.ContratoGeracaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/clientes/{idCliente}/emprestimos/{idEmprestimo}/contratos")
@RequiredArgsConstructor
public class ContratoGeradoController {
    private final ContratoGeracaoService service;
    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    @GetMapping("/previa")
    public ContratoPreviaDTO previa(@RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente, @PathVariable Long idEmprestimo) {
        return service.previa(perfilId, idCliente, idEmprestimo);
    }

    @PostMapping
    public ResponseEntity<ContratoGeradoMetadadosDTO> gerar(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente, @PathVariable Long idEmprestimo) {
        var contrato = service.gerar(perfilId, idCliente, idEmprestimo);
        return ResponseEntity.created(URI.create("/clientes/" + idCliente + "/emprestimos/" + idEmprestimo
                + "/contratos/" + contrato.id() + "/download")).body(contrato);
    }

    @GetMapping
    public List<ContratoGeradoMetadadosDTO> listar(@RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente, @PathVariable Long idEmprestimo) {
        return service.listar(perfilId, idCliente, idEmprestimo);
    }

    @GetMapping("/{contratoId}/download")
    public ResponseEntity<byte[]> baixar(@RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente, @PathVariable Long idEmprestimo, @PathVariable Long contratoId) {
        var documento = service.baixar(perfilId, idCliente, idEmprestimo, contratoId);
        return ResponseEntity.ok().contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(documento.nomeArquivo()).build().toString())
                .body(documento.bytes());
    }

    @DeleteMapping("/{contratoId}")
    public ResponseEntity<Void> excluir(@RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente, @PathVariable Long idEmprestimo, @PathVariable Long contratoId) {
        service.excluir(perfilId, idCliente, idEmprestimo, contratoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{contratoId}/download-pdf")
    public ResponseEntity<byte[]> baixarPdf(@RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente, @PathVariable Long idEmprestimo, @PathVariable Long contratoId) {
        var documento = service.baixarPdf(perfilId, idCliente, idEmprestimo, contratoId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(documento.nomeArquivo()).build().toString()).body(documento.bytes());
    }

    @GetMapping("/{contratoId}/download-pdf-assinado")
    public ResponseEntity<byte[]> baixarPdfAssinado(@RequestHeader("X-Perfil-Id") Long perfilId,
            @PathVariable Long idCliente, @PathVariable Long idEmprestimo, @PathVariable Long contratoId) {
        var documento = service.baixarPdfAssinado(perfilId, idCliente, idEmprestimo, contratoId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(documento.nomeArquivo()).build().toString()).body(documento.bytes());
    }
}
