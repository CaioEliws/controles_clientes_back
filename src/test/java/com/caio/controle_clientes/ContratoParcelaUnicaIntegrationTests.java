package com.caio.controle_clientes;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class ContratoParcelaUnicaIntegrationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void configurarMvc() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }
    @Autowired ContratoGeracaoService geracao;
    @Autowired ContratoService dados;
    @Autowired ContratoSnapshotService snapshots;
    @Autowired ContratoTemplateService templates;
    @Autowired ContratoPdfService pdfs;
    @Autowired AssinaturasPdfService assinaturas;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired EmprestimoService emprestimos;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;

    ContratoDocxFixture fixture() {
        var f = ContratoDocxFixture.criar(perfis, clientes, emprestimos);
        var e = emprestimos.criarEmprestimo(f.perfil().getId(), f.devedor(), new BigDecimal("2700.00"),
                1, BigDecimal.TEN, FormaPagamento.PIX, FrequenciaPagamento.MENSAL, TipoContrato.FISICO, f.fiador().getId());
        e.setDataDoEmprestimo(LocalDate.of(2026, 10, 7));
        e.setInicioPagamento(LocalDate.of(2026, 11, 7)); e.setFinalPagamento(e.getInicioPagamento());
        e.getParcelas().get(0).setDataVencimento(e.getInicioPagamento());
        return new ContratoDocxFixture(f.perfil(), f.devedor(), f.fiador(), e);
    }
    String base(ContratoDocxFixture f) { return "/clientes/" + f.devedor().getId() + "/emprestimos/" + f.emprestimo().getId(); }
    byte[] baixar(ContratoDocxFixture f, long id, String formato) throws Exception {
        return mvc.perform(get(base(f) + "/contratos/" + id + "/" + formato).header("X-Perfil-Id", f.perfil().getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
    String texto(byte[] pdf, boolean somenteContrato) throws Exception {
        try (var doc = Loader.loadPDF(pdf)) {
            var leitor = new PDFTextStripper();
            if (somenteContrato) leitor.setEndPage(doc.getNumberOfPages() - 1);
            else leitor.setStartPage(doc.getNumberOfPages());
            return leitor.getText(doc).replaceAll("\\s+", " ");
        }
    }

    @Test void endpointsPreviaGeracaoListagemETresDownloadsComValoresCorretosEPdfReal() throws Exception {
        var f = fixture();
        for (String rota : List.of("/dados-contrato", "/contratos/previa")) {
            String prefixo = rota.endsWith("previa") ? "$.dados.emprestimo." : "$.emprestimo.";
            mvc.perform(get(base(f) + rota).header("X-Perfil-Id", f.perfil().getId())).andExpect(status().isOk())
                    .andExpect(jsonPath(prefixo + "valorEmprestado").value(2700))
                    .andExpect(jsonPath(prefixo + "valorParcela").value(2970))
                    .andExpect(jsonPath(prefixo + "valorTotalDivida").value(2970))
                    .andExpect(jsonPath(prefixo + "valorTotalEmprestimo").value(2970));
        }
        var resposta = mvc.perform(post(base(f) + "/contratos").header("X-Perfil-Id", f.perfil().getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(resposta).get("id").asLong();
        mvc.perform(get(base(f) + "/contratos").header("X-Perfil-Id", f.perfil().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        var c = contratos.findById(id).orElseThrow();
        var s = json.readValue(c.getSnapshotJson(), ContratoSnapshotDTO.class);
        assertThat(s.emprestimo().valorEmprestado()).isEqualByComparingTo("2700");
        assertThat(s.emprestimo().valorTotalDivida()).isEqualByComparingTo("2970");
        assertThat(s.dataContrato()).isEqualTo(c.getDataGeracao().atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toLocalDate());
        byte[] word = baixar(f, id, "download"), pdf = baixar(f, id, "download-pdf"), assinado = baixar(f, id, "download-pdf-assinado");
        assertThat(ContratoGeracaoService.hash(word)).isEqualTo(c.getHashDocumento());
        assertThat(ContratoGeracaoService.hash(pdf)).isEqualTo(c.getHashPdfFinal());
        assertThat(ContratoGeracaoService.hash(assinado)).isEqualTo(c.getHashPdfAssinado());
        assertThat(texto(pdf, true)).contains("R$ 2.700,00 (dois mil e setecentos reais)", "1 (uma) parcela",
                "R$ 2.970,00", "no dia 07 de novembro de 2026", "Edson Antonio Vieira", "Ivani Elias Machado Vieira")
                .doesNotContain("Sendo a primeira parcela", "todo dia 7", "a última parcela");
        assertThat(texto(pdf, false)).contains("R$ 2.700,00").doesNotContain("2.970,00");
        assertThat(json.readValue(c.getPromissoriaSnapshotJson(), PromissoriaSnapshotDTO.class).valor()).isEqualTo("2.700,00");
        Path qa = Path.of(".auditoria/parcela-unica/gerado"); Files.createDirectories(qa);
        Files.write(qa.resolve("contrato-2700-parcela-unica.docx"), word);
        Files.write(qa.resolve("contrato-2700-parcela-unica.pdf"), pdf);
        Files.write(qa.resolve("contrato-2700-parcela-unica-assinado.pdf"), assinado);
    }

    @Test void snapshotLegadoETresDownloadsPermanecemIdenticosAposEditarPessoasENovaGeracao() throws Exception {
        var f = fixture();
        var s = snapshots.montar(dados.buscarDadosContratoModeloFixo(f.perfil().getId(), f.devedor().getId(),
                f.emprestimo().getId(), ContratoSnapshotService.CREDOR), LocalDate.of(2026, 10, 5));
        var campos = new LinkedHashMap<>(s.camposDocumento());
        campos.keySet().removeIf(k -> k.startsWith("VALOR_EMPRESTADO") || k.startsWith("VALOR_TOTAL_DIVIDA") || k.equals("VENCIMENTO_UNICO_EXTENSO"));
        campos.put("VALOR_TOTAL", "2.970,00"); campos.put("VALOR_TOTAL_EXTENSO", "dois mil novecentos e setenta reais");
        campos.put("VALOR_PROMISSORIA", "2.970,00"); campos.put("VALOR_PROMISSORIA_EXTENSO", "dois mil novecentos e setenta reais");
        var legado = new ContratoSnapshotDTO(s.emprestimoId(), s.versaoTemplate(), s.dataContrato(), s.devedor(), s.fiador(),
                s.emprestimo(), s.parcelas(), s.credorFixo(), s.testemunhaFixa(), campos);
        byte[] word = templates.gerar(legado); var pdf = pdfs.gerar(word, legado); byte[] assinado = assinaturas.gerar(pdf.pdfFinal());
        ObjectNode tree = (ObjectNode) json.valueToTree(legado); ((ObjectNode) tree.get("emprestimo")).remove("valorTotalDivida");
        String antigoJson = json.writeValueAsString(tree);
        var antigo = contratos.saveAndFlush(new ContratoGerado(f.emprestimo(), f.perfil(), s.versaoTemplate(),
                OffsetDateTime.parse("2026-10-05T15:00:00Z"), antigoJson, ContratoGeracaoService.hash(word), "legado.docx", word,
                json.writeValueAsString(pdf.snapshot()), ContratoGeracaoService.hash(pdf.pdfFinal()), pdf.pdfFinal(),
                ContratoGeracaoService.hash(assinado), assinado));
        assertThat(json.readValue(antigoJson, ContratoSnapshotDTO.class).emprestimo().valorTotalDivida()).isEqualByComparingTo("2970");
        long antigoId = antigo.getId();
        f.devedor().setNome("Nome alterado devedor"); f.fiador().setNome("Nome alterado fiador");
        f.devedor().setRg("36956870"); f.fiador().setRg("52779723x"); em.flush();
        var novo = geracao.gerar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId()); em.flush(); em.clear();
        assertThat(novo.id()).isNotEqualTo(antigoId);
        assertThat(baixar(f, antigoId, "download")).isEqualTo(word);
        assertThat(baixar(f, antigoId, "download-pdf")).isEqualTo(pdf.pdfFinal());
        assertThat(baixar(f, antigoId, "download-pdf-assinado")).isEqualTo(assinado);
        assertThat(contratos.findById(antigoId).orElseThrow().getSnapshotJson()).isEqualTo(antigoJson);
        assertThat(texto(baixar(f, novo.id(), "download-pdf"), true)).contains("Nome Alterado Devedor", "2.700,00")
                .contains("36.956.870", "52.779.723-X")
                .doesNotContain("Sendo a primeira parcela");
    }
}
