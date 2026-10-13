package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.services.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    private final HealthService service;
    public HealthController(HealthService service) { this.service = service; }
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> verificar() {
        var states = service.verificar();
        return ResponseEntity.status("DOWN".equals(states.get("database")) ? 503 : 200).body(states);
    }
}
