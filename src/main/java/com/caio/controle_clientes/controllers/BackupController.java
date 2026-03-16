package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.services.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BackupController {

    private final ExportService exportService;

    @GetMapping(
            value = "/export",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportar(
            @RequestHeader("X-Perfil-Id") Long perfilId
    ) throws Exception {
        byte[] file = exportService.exportarExcel(perfilId);
        String filename = "backup-perfil-" + perfilId + "-" + java.time.LocalDate.now() + ".xlsx";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(file);
    }
}