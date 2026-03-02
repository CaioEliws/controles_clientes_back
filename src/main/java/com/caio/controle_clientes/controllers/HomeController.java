package com.caio.controle_clientes.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "API RODANDO!";
    }

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}