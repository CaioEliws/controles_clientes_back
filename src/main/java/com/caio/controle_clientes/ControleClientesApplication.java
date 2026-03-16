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
public class ControleClientesApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(ControleClientesApplication.class, args);
	}

    @Autowired
    private EmprestimoService emprestimoService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private ParcelaRepositorio parcelaRepositorio;
    @Autowired
    private ParcelaService parcelaService;
    @Autowired
    private RelatorioService relatorioService;

    @Override
    public void run(String... args) throws Exception {
        Principal principal = new Principal();
        principal.showMenu();

//        Principal principal = new Principal(
//                emprestimoService,
//                clienteService,
//                parcelaRepositorio,
//                parcelaService,
//                relatorioService
//        );
//        principal.showMenu();
    }
}
