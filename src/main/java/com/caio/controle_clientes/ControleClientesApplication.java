package com.caio.controle_clientes;

import com.caio.controle_clientes.principal.Principal;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import com.caio.controle_clientes.services.*;
import com.caio.controle_clientes.services.parcela.ParcelaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ControleClientesApplication {
	public static void main(String[] args) {
        SpringApplication.run(ControleClientesApplication.class, args);
	}
}
