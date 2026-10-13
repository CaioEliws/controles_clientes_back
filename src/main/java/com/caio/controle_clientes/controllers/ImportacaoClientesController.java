package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.services.importClientes.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/import/clientes")
public class ImportacaoClientesController {
    private final ImportacaoClientesCsvService service;
    public ImportacaoClientesController(ImportacaoClientesCsvService service) { this.service = service; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoClientesResultadoDTO importar(@RequestHeader("X-Perfil-Id") Long perfilId, @RequestParam("file") MultipartFile file) {
        return service.importar(perfilId, file, false);
    }
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoClientesResultadoDTO preview(@RequestHeader("X-Perfil-Id") Long perfilId, @RequestParam("file") MultipartFile file) {
        return service.importar(perfilId, file, true);
    }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> arquivoAusente() {
        return java.util.Map.of("message", "Envie um arquivo CSV válido no campo file.");
    }
}
