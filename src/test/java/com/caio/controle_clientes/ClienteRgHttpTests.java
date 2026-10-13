package com.caio.controle_clientes;

import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Perfil;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.PerfilRepositorio;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@Transactional
class ClienteRgHttpTests {
    @Autowired WebApplicationContext context;
    @Autowired ClienteRepositorio clientes;
    @Autowired PerfilRepositorio perfis;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired com.caio.controle_clientes.services.emprestimo.EmprestimoService emprestimos;
    @Autowired com.caio.controle_clientes.services.contrato.ContratoGeracaoService geracao;
    @Autowired com.caio.controle_clientes.repository.ContratoGeradoRepositorio contratos;

    @ParameterizedTest
    @CsvSource(value = {
        "409614713|40.961.471-3", "40.961.471-3|40.961.471-3",
        "52779723x|52.779.723-X", "000000002|00.000.000-2",
        "52779723|52.779.723", "52.779.723|52.779.723", "1234567|1.234.567",
        "1234567890|1.234.567.890", "12345678901|12.345.678.901",
        "123456789012|123.456.789.012", "1234567890123|1.234.567.890.123",
        "36956870|36.956.870", "01234567|01.234.567", "001234567|00.123.456-7",
        "123456X|123.456X", "12/345/678|12/345/678", "64.729.511-8|64.729.511-8",
        "NULL|NULL"
    }, delimiter = '|', nullValues = "NULL")
    void consultaLegadoEAtualizacaoPersistemRgComoString(String original, String esperado) throws Exception {
        var perfil = new Perfil(); perfil.setNome("Teste RG"); perfis.save(perfil);
        var cliente = new Cliente(); cliente.setNome("Irene teste"); cliente.setPerfil(perfil); cliente.setRg(original);
        clientes.saveAndFlush(cliente);
        var id = cliente.getId(); var perfilId = perfil.getId(); em.clear();
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var criacao = new LinkedHashMap<String, Object>();
        criacao.put("nome", "Nova RG"); criacao.put("rg", original);
        criacao.put("enderecoRua", "Rua A"); criacao.put("enderecoBairro", "Centro"); criacao.put("enderecoNumero", 1);
        assertThat(mvc.perform(post("/clientes").header("X-Perfil-Id", perfilId).contentType("application/json")
            .content(json.writeValueAsString(criacao))).andReturn().getResponse().getStatus()).isEqualTo(201);
        assertThat(clientes.findByNomeIgnoreCaseAndPerfilId("Nova RG", perfilId).orElseThrow().getRg()).isEqualTo(esperado);
        var consulta = mvc.perform(get("/clientes/" + id).header("X-Perfil-Id", perfilId)).andReturn().getResponse();
        assertThat(consulta.getStatus()).isEqualTo(200);
        var rg = json.readTree(consulta.getContentAsString()).get("rg");
        if (esperado == null) assertThat(rg.isNull()).isTrue();
        else { assertThat(rg.isTextual()).isTrue(); assertThat(rg.asText()).isEqualTo(esperado); }
        assertThat(clientes.findById(id).orElseThrow().getRg()).isEqualTo(original);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("nome", "Irene teste"); payload.put("rg", original);
        var atualizado = mvc.perform(put("/clientes/" + id).header("X-Perfil-Id", perfilId)
            .contentType("application/json").content(json.writeValueAsString(payload))).andReturn().getResponse();
        assertThat(atualizado.getStatus()).isEqualTo(200);
        em.flush(); em.clear();
        assertThat(clientes.findById(id).orElseThrow().getRg()).isEqualTo(esperado);
        var reaberto = mvc.perform(get("/clientes/" + id).header("X-Perfil-Id", perfilId)).andReturn().getResponse();
        assertThat(reaberto.getStatus()).isEqualTo(200);
        rg = json.readTree(reaberto.getContentAsString()).get("rg");
        if (esperado == null) assertThat(rg.isNull()).isTrue();
        else assertThat(rg.asText()).isEqualTo(esperado);
        assertThat(com.caio.controle_clientes.validation.RgUtil.normalizar(clientes.findById(id).orElseThrow().getRg()))
            .isEqualTo(com.caio.controle_clientes.validation.RgUtil.normalizar(original));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"123456", "12345678901234", "12A345678", "1234X5678", "1234567!"})
    void rejeitaRgInvalidoNaCriacaoEEdicaoSemImpedirConsultaLegada(String invalido) throws Exception {
        var perfil = new Perfil(); perfil.setNome("RG inválido"); perfis.save(perfil);
        var cliente = new Cliente(); cliente.setNome("Legado"); cliente.setPerfil(perfil); cliente.setRg(invalido);
        clientes.saveAndFlush(cliente);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        assertThat(mvc.perform(get("/clientes/" + cliente.getId()).header("X-Perfil-Id", perfil.getId()))
            .andReturn().getResponse().getStatus()).isEqualTo(200);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("nome", "Teste"); payload.put("rg", invalido);
        payload.put("enderecoRua", "Rua A"); payload.put("enderecoBairro", "Centro"); payload.put("enderecoNumero", 1);
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
            post("/clientes"), put("/clientes/" + cliente.getId())}) {
            var response = mvc.perform(request.header("X-Perfil-Id", perfil.getId()).contentType("application/json")
                .content(json.writeValueAsString(payload))).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(json.readTree(response.getContentAsString()).at("/fields/rg").asText()).contains("7 a 13");
        }
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getRg()).isEqualTo(invalido);
    }

    @ParameterizedTest
    @CsvSource({"36956870,36.956.870", "01234567,01.234.567"})
    void cadastroEdicaoGeracaoEDownloadsPreservamRgDoDevedorEFiador(String entrada, String esperado) throws Exception {
        var f = ContratoDocxFixture.criar(perfis, clientes, emprestimos);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var payload = (tools.jackson.databind.node.ObjectNode) json.valueToTree(
            com.caio.controle_clientes.dto.cliente.ClienteDetalhadoDTO.fromEntity(f.devedor()));
        payload.remove("id"); payload.remove("quantidadeEmprestimos");
        payload.put("nome", "Devedor RG HTTP"); payload.put("cpf", "00000000004");
        payload.put("rg", entrada); payload.put("rgOrgaoExpedidor", "SSP/SP");
        assertThat(mvc.perform(post("/clientes").header("X-Perfil-Id", f.perfil().getId())
            .contentType("application/json").content(json.writeValueAsString(payload)))
            .andReturn().getResponse().getStatus()).isEqualTo(201);
        var devedor = clientes.findByNomeIgnoreCaseAndPerfilId("Devedor RG HTTP", f.perfil().getId()).orElseThrow();
        var consulta = mvc.perform(get("/clientes/" + devedor.getId()).header("X-Perfil-Id", f.perfil().getId()))
            .andReturn().getResponse();
        assertThat(consulta.getStatus()).isEqualTo(200);
        String rgExibido = json.readTree(consulta.getContentAsString()).get("rg").asText();
        assertThat(rgExibido).isEqualTo(esperado);
        payload.put("rg", rgExibido);
        assertThat(mvc.perform(put("/clientes/" + devedor.getId()).header("X-Perfil-Id", f.perfil().getId())
            .contentType("application/json").content(json.writeValueAsString(payload)))
            .andReturn().getResponse().getStatus()).isEqualTo(200);
        em.flush(); em.clear();
        devedor = clientes.findById(devedor.getId()).orElseThrow();
        assertThat(com.caio.controle_clientes.validation.RgUtil.normalizar(devedor.getRg())).isEqualTo(entrada);
        payload.put("nome", "Fiador RG HTTP"); payload.put("cpf", "00000000005"); payload.put("rg", "52779723x");
        assertThat(mvc.perform(post("/clientes").header("X-Perfil-Id", f.perfil().getId())
            .contentType("application/json").content(json.writeValueAsString(payload)))
            .andReturn().getResponse().getStatus()).isEqualTo(201);
        var fiador = clientes.findByNomeIgnoreCaseAndPerfilId("Fiador RG HTTP", f.perfil().getId()).orElseThrow();
        var emp = emprestimos.criarEmprestimo(f.perfil().getId(), devedor, new java.math.BigDecimal("1000"), 1,
            java.math.BigDecimal.TEN, com.caio.controle_clientes.models.FormaPagamento.PIX,
            com.caio.controle_clientes.models.FrequenciaPagamento.MENSAL,
            com.caio.controle_clientes.models.TipoContrato.FISICO, fiador.getId());
        String base = "/clientes/" + devedor.getId() + "/emprestimos/" + emp.getId() + "/contratos";
        var resposta = mvc.perform(post(base).header("X-Perfil-Id", f.perfil().getId())).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(201);
        long contratoId = json.readTree(resposta.getContentAsString()).get("id").asLong();
        var snapshot = json.readValue(contratos.findById(contratoId).orElseThrow().getSnapshotJson(),
            com.caio.controle_clientes.dto.contrato.ContratoSnapshotDTO.class);
        assertThat(snapshot.camposDocumento()).containsEntry("DEVEDOR_RG", esperado).containsEntry("FIADOR_RG", "52.779.723-X");
        assertThat(mvc.perform(get(base).header("X-Perfil-Id", f.perfil().getId()))
            .andReturn().getResponse().getStatus()).isEqualTo(200);
        for (String formato : new String[]{"download", "download-pdf", "download-pdf-assinado"}) {
            var arquivo = mvc.perform(get(base + "/" + contratoId + "/" + formato).header("X-Perfil-Id", f.perfil().getId()))
                .andReturn().getResponse();
            assertThat(arquivo.getStatus()).isEqualTo(200);
            String texto;
            if (formato.equals("download")) {
                try (var word = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(arquivo.getContentAsByteArray()))) {
                    texto = word.getParagraphs().stream().map(p -> p.getText()).collect(java.util.stream.Collectors.joining(" "));
                }
            } else {
                try (var pdf = org.apache.pdfbox.Loader.loadPDF(arquivo.getContentAsByteArray())) {
                    texto = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                }
            }
            assertThat(texto).contains(esperado, "52.779.723-X");
        }
    }

    @ParameterizedTest
    @CsvSource(value={"36956870|36.956.870|SSP/SP", "01234567|01.234.567|NULL",
        "52779723x|52.779.723-X|legado", "1234567890123|1.234.567.890.123|SSP/XX"}, delimiter='|', nullValues="NULL")
    void perfilTambemUsaRegraCentralSemDependerDeUf(String entrada, String esperado, String orgao) throws Exception {
        var perfil = new Perfil(); perfil.setNome("Perfil RG"); perfis.saveAndFlush(perfil);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("nome", "Perfil RG"); payload.put("rg", entrada); payload.put("rgOrgaoExpedidor", orgao);
        var resposta = mvc.perform(put("/perfis/" + perfil.getId()).header("X-Perfil-Id", perfil.getId())
            .contentType("application/json").content(json.writeValueAsString(payload))).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(json.readTree(resposta.getContentAsString()).get("rg").asText()).isEqualTo(esperado);
        em.flush(); em.clear();
        assertThat(perfis.findById(perfil.getId()).orElseThrow().getRg()).isEqualTo(esperado);
        var consulta = mvc.perform(get("/perfis/" + perfil.getId()).header("X-Perfil-Id", perfil.getId()))
            .andReturn().getResponse();
        assertThat(consulta.getStatus()).isEqualTo(200);
        assertThat(json.readTree(consulta.getContentAsString()).get("rg").asText()).isEqualTo(esperado);
    }

    @org.junit.jupiter.api.Test
    void perfilRejeitaRgInvalidoCom400SemAlterarCadastro() throws Exception {
        var perfil = new Perfil(); perfil.setNome("Perfil RG inválido"); perfil.setRg("01234567"); perfis.saveAndFlush(perfil);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String rg : new String[]{"ABC123", "123X456", "1234A567", "123456", "12345678901234", "1234567XX"}) {
            var resposta = mvc.perform(put("/perfis/" + perfil.getId()).header("X-Perfil-Id", perfil.getId())
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("nome", perfil.getNome(), "rg", rg))))
                .andReturn().getResponse();
            assertThat(resposta.getStatus()).isEqualTo(400);
            assertThat(json.readTree(resposta.getContentAsString()).at("/fields/rg").asText()).contains("7 a 13");
        }
        assertThat(perfis.findById(perfil.getId()).orElseThrow().getRg()).isEqualTo("01234567");
    }
}
