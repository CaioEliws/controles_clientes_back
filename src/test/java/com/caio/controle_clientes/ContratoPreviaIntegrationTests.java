package com.caio.controle_clientes;

import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@Transactional
class ContratoPreviaIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoService emprestimos;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired ObjectMapper json;

    ContratoDocxFixture fixture(boolean incompleto) {
        var f = ContratoDocxFixture.criar(perfis, clientes, emprestimos);
        f.perfil().setNomeCompleto("Credor de Teste");
        if (incompleto) {
            var bean = new BeanWrapperImpl(f.perfil());
            for (String campo : new String[]{"nomeCompleto", "nacionalidade", "estadoCivil", "profissao",
                    "rg", "rgOrgaoExpedidor", "cpf", "enderecoRua", "enderecoNumero", "enderecoBairro",
                    "enderecoCep", "enderecoCidade", "enderecoEstado"}) bean.setPropertyValue(campo, null);
        }
        return f;
    }
    String rota(ContratoDocxFixture f) {
        return "/clientes/" + f.devedor().getId() + "/emprestimos/" + f.emprestimo().getId();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void previaEPostReaisUsamModeloFixoSemExigirPerfilCompleto(boolean incompleto) throws Exception {
        var f = fixture(incompleto);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        long antes = contratos.count();
        var previa = mvc.perform(get(rota(f) + "/contratos/previa")
                .header("X-Perfil-Id", f.perfil().getId())).andReturn().getResponse();
        assertThat(previa.getStatus()).isEqualTo(200);
        var body = json.readTree(previa.getContentAsString());
        assertThat(body.get("validoParaGeracao").asBoolean()).isTrue();
        assertThat(body.at("/dados/credor/nomeCompleto").asText()).isEqualTo("Edson Antonio Vieira");
        assertThat(body.at("/testemunha/nome").asText()).isEqualTo("Ivani Elias Machado Vieira");
        assertThat(contratos.count()).isEqualTo(antes);
        // The generic endpoint retains its original Perfil = Credor contract.
        var generico = mvc.perform(get(rota(f) + "/dados-contrato")
                .header("X-Perfil-Id", f.perfil().getId())).andReturn().getResponse();
        assertThat(generico.getStatus()).isEqualTo(incompleto ? 400 : 200);
        if (incompleto) assertThat(generico.getContentAsString()).contains("Dados do credor incompletos");
        else assertThat(generico.getContentAsString()).contains("Credor de Teste");

        var post = mvc.perform(post(rota(f) + "/contratos")
                .header("X-Perfil-Id", f.perfil().getId())).andReturn().getResponse();
        assertThat(post.getStatus()).isEqualTo(201);
        long id = json.readTree(post.getContentAsString()).get("id").asLong();
        assertThat(contratos.count()).isEqualTo(antes + 1);
        for (String formato : new String[]{"download", "download-pdf"}) {
            var download = mvc.perform(get(rota(f) + "/contratos/" + id + "/" + formato)
                    .header("X-Perfil-Id", f.perfil().getId())).andReturn().getResponse();
            assertThat(download.getStatus()).isEqualTo(200);
            byte[] bytes = download.getContentAsByteArray();
            if (formato.equals("download")) {
                try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    assertThat(doc.getParagraphs().stream().map(p -> p.getText()).toList().toString())
                            .contains("Edson Antonio Vieira", "Ivani Elias Machado Vieira")
                            .doesNotContain("Credor de Teste", "Pessoa do Perfil");
                }
            } else {
                try (var pdf = Loader.loadPDF(bytes)) {
                    var extractor = new PDFTextStripper();
                    assertThat(extractor.getText(pdf)).contains("Edson Antonio Vieira", "Ivani Elias Machado Vieira")
                            .doesNotContain("Credor de Teste", "Pessoa do Perfil");
                    extractor.setStartPage(pdf.getNumberOfPages());
                    assertThat(extractor.getText(pdf)).contains("EDSON ANTONIO VIEIRA", "PROMISS");
                    assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
                }
            }
        }
        assertThat(f.perfil().getNomeCompleto()).isEqualTo(incompleto ? null : "Credor de Teste");
    }

    @ParameterizedTest
    @ValueSource(strings = {"devedorCpf", "fiadorCpf", "fiadorRg", "semFiador", "valor", "parcelas", "frequencia"})
    void previaEPostRejeitamOsMesmosDadosInvalidos(String caso) throws Exception {
        var f = fixture(true);
        switch (caso) {
            case "devedorCpf" -> f.devedor().setCpf(null);
            case "fiadorCpf" -> f.fiador().setCpf(null);
            case "fiadorRg" -> f.fiador().setRg(null);
            case "semFiador" -> f.emprestimo().setFiador(null);
            case "valor" -> f.emprestimo().setValorTotalEmprestimo(BigDecimal.ZERO);
            case "parcelas" -> f.emprestimo().getParcelas().get(1).setDataVencimento(
                    f.emprestimo().getParcelas().get(1).getDataVencimento().plusDays(1));
            case "frequencia" -> f.emprestimo().setFrequenciaPagamento(com.caio.controle_clientes.models.FrequenciaPagamento.SEMANAL);
        }
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        long antes = contratos.count();
        var previa = mvc.perform(get(rota(f) + "/contratos/previa").header("X-Perfil-Id", f.perfil().getId()))
                .andReturn().getResponse();
        var post = mvc.perform(post(rota(f) + "/contratos").header("X-Perfil-Id", f.perfil().getId()))
                .andReturn().getResponse();
        assertThat(previa.getStatus()).isEqualTo(400);
        assertThat(post.getStatus()).isEqualTo(400);
        assertThat(previa.getContentAsString()).isEqualTo(post.getContentAsString()).doesNotContain("Dados do credor");
        if (caso.equals("devedorCpf")) assertThat(previa.getContentAsString()).contains("devedor", "cpf");
        if (caso.startsWith("fiador")) assertThat(previa.getContentAsString()).contains("fiador", caso.endsWith("Cpf") ? "cpf" : "rg");
        if (caso.equals("semFiador")) assertThat(previa.getContentAsString()).contains("fiador");
        assertThat(contratos.count()).isEqualTo(antes);
    }
}
