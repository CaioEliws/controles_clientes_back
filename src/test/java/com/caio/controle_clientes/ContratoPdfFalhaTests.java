package com.caio.controle_clientes;

import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:contrato-pdf-falha;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional
class ContratoPdfFalhaTests {
    @Autowired ContratoGeracaoService service;
    @MockitoBean DocxPdfConversor conversor;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoService emprestimos;
    @Autowired ContratoGeradoRepositorio contratos;
    @Test void conversaoFalhaNaoPersisteSucessoParcial() {
        var f=ContratoDocxFixture.criar(perfis,clientes,emprestimos);
        when(conversor.converter(any())).thenThrow(new RegraNegocioException("Falha na conversão DOCX para PDF."));
        assertThatThrownBy(() -> service.gerar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId())).hasMessageContaining("Falha na conversão");
        assertThat(contratos.count()).isZero();
    }
    @Test void pdfInvalidoNaoPersisteSucessoParcial() {
        var f=ContratoDocxFixture.criar(perfis,clientes,emprestimos);
        when(conversor.converter(any())).thenReturn(new byte[]{1,2,3});
        assertThatThrownBy(() -> service.gerar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId())).hasMessageContaining("Falha ao validar");
        assertThat(contratos.count()).isZero();
    }
}
