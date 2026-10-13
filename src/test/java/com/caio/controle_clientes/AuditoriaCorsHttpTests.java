package com.caio.controle_clientes;

import com.caio.controle_clientes.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Import;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(AuditoriaCorsHttpTests.ErrosControlados.class)
class AuditoriaCorsHttpTests {
    @Autowired WebApplicationContext context;
    @Test void parametrosInvalidosOuAusentesTem400() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).build();
        for(var caminho:new String[]{"/parcelas","/parcelas?status=INVALIDO"})
            mvc.perform(get(caminho).header("X-Perfil-Id",1).header("Origin","http://localhost:5173"))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
    @ParameterizedTest @ValueSource(strings={"GET","POST","PUT","PATCH","DELETE"})
    void preflightAutorizaPerfilETodosOsVerbos(String verbo) throws Exception {
        MockMvcBuilders.webAppContextSetup(context).build().perform(options("/clientes/1")
                .header("Origin","http://localhost:5173").header("Access-Control-Request-Method",verbo)
                .header("Access-Control-Request-Headers","X-Perfil-Id,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
    @ParameterizedTest @ValueSource(ints={400,409,500})
    void respostasDeErroTemCorsSemDetalhesInternos(int codigo) throws Exception {
        MockMvcBuilders.webAppContextSetup(context).build().perform(get("/__auditoria/erro/"+codigo).header("Origin","http://localhost:5173"))
                .andExpect(status().is(codigo)).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL confidencial"))));
    }
    @RestController static class ErrosControlados {
        @GetMapping("/__auditoria/runtime") void runtime() { throw new NullPointerException("SQL confidencial java.lang segredo"); }
        @GetMapping("/__auditoria/erro/{codigo}") void erro(@PathVariable int codigo) throws Exception {
            switch(codigo) {
                case 400 -> throw new IllegalArgumentException("Argumento inválido.");
                case 409 -> throw new org.springframework.dao.DataIntegrityViolationException("SQL confidencial");
                default -> throw new Exception("SQL confidencial");
            }
        }
    }
    @Test void runtimeInesperadaTem500SeguroComCors() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).build().perform(get("/__auditoria/runtime").header("Origin","http://localhost:5173"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"))
                .andExpect(jsonPath("$.message").value("Não foi possível concluir a operação. Tente novamente."));
    }
}
