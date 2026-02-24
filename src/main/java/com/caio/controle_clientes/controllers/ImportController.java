package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.services.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping
    public ResponseEntity<Map<String, String>> importar(@RequestParam("file") MultipartFile file) {

        Map<String, String> response = new HashMap<>();

        try {

            importService.importarPlanilha(file.getInputStream());

            response.put("message", "Importação concluída com sucesso.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {

            e.printStackTrace();

            response.put("error", "Erro ao importar arquivo.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}