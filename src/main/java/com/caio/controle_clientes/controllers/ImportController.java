package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.services.ImportService;
import com.caio.controle_clientes.services.importService.dto.ImportacaoResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacaoResultado> importar(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ImportacaoResultado(0, 0, 0, 0, java.util.List.of())
            );
        }

        try {
            ImportacaoResultado resultado =
                    importService.importarPlanilha(perfilId, file.getInputStream());

            return ResponseEntity.status(HttpStatus.CREATED).body(resultado);

        } catch (Exception e) {
            if (e instanceof java.io.IOException || e instanceof org.apache.poi.UnsupportedFileFormatException
                    || e instanceof IllegalArgumentException) {
                throw new IllegalArgumentException("Arquivo Excel inválido ou incompatível.");
            }
            throw e;
        }
    }
}
