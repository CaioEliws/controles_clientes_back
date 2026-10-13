package com.caio.controle_clientes;

import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:contrato-falha-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ContratoGeracaoFalhaTests {
    @Autowired ContratoGeracaoService service;
    @MockitoBean ContratoTemplateService template;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoService emprestimos;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired PlatformTransactionManager tx;

    @Test void falhaDoDocxNaoPersisteContratoSemDocumento() {
        var fixture = new TransactionTemplate(tx).execute(s -> ContratoDocxFixture.criar(perfis,clientes,emprestimos));
        when(template.gerar(any())).thenThrow(new RegraNegocioException("Modelo Word contém placeholder não preenchido."));
        assertThatThrownBy(() -> service.gerar(fixture.perfil().getId(),fixture.devedor().getId(),fixture.emprestimo().getId()))
                .hasMessageContaining("placeholder não preenchido");
        assertThat(contratos.count()).isZero();
    }
}
