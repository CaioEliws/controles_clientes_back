package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportacaoClientesCsvHttpTests {
    @Autowired WebApplicationContext context;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoRepositorio emprestimos;
    @Autowired ParcelaRepositorio parcelas;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired ContratoGeracaoService geracao;
    @Autowired EmprestimoService emprestimoService;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager tm;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    Perfil perfil() { var p = new Perfil(); p.setNome("HTTP CSV " + UUID.randomUUID()); return perfis.saveAndFlush(p); }
    MockMultipartFile csv(String texto) { return new MockMultipartFile("file", "a.csv", "text/csv", texto.getBytes(StandardCharsets.UTF_8)); }
    @Test void previewConfirmacaoEResumoMascaradoViaHttp() throws Exception {
        var p = perfil(); var arquivo = csv("\uFEFFnomeCompleto;cpf;statusExtracao;rg\nJoão;00000000002;OK;36956870");
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var preview = mvc.perform(multipart("/import/clientes/preview").file(arquivo).header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(preview.getStatus()).isEqualTo(200);
        assertThat(json.readTree(preview.getContentAsString()).get("criados").asInt()).isEqualTo(1);
        assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
        var resposta = mvc.perform(multipart("/import/clientes").file(arquivo).header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(resposta.getContentAsString()).doesNotContain("00000000002", "000.000.000-02");
        var dto = json.readTree(resposta.getContentAsString());
        assertThat(dto.get("criados").asInt()).isEqualTo(1); assertThat(dto.get("previa").asBoolean()).isFalse();
        assertThat(dto.at("/detalhes/0/cpf").asText()).isEqualTo("***.***.000-**");
        assertThat(clientes.findAllByPerfilId(p.getId()).get(0).getRg()).isEqualTo("36.956.870");
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="csv.referencia", matches=".+")
    void previewDoCsvOriginalPreservaCemClientesEOArquivoOriginal() throws Exception {
        var path = java.nio.file.Path.of(System.getProperty("csv.referencia")); byte[] original = java.nio.file.Files.readAllBytes(path);
        var p = perfil();
        for (int i = 0; i < 100; i++) {
            var c = new Cliente(); c.setPerfil(p); c.setNome("Cliente teste " + i); c.setCpf(String.format(java.util.Locale.ROOT, "%011d", i)); clientes.save(c);
        }
        var antes = jdbc.queryForList("select * from clientes order by id");
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var resposta = mvc.perform(multipart("/import/clientes/preview").file(new MockMultipartFile("file", "clientes_extraidos.csv", "text/csv", original))
            .header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForList("select * from clientes order by id")).isEqualTo(antes);
        assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(100);
        assertThat(java.nio.file.Files.readAllBytes(path)).isEqualTo(original);
        var dto = json.readValue(resposta.getContentAsString(), com.caio.controle_clientes.services.importClientes.ImportacaoClientesResultadoDTO.class);
        assertThat(dto.totalLinhas()).isEqualTo(273); assertThat(dto.detalhesTruncados()).isFalse();
        assertThat(dto.detalhes()).anyMatch(d -> d.nome() != null && d.nome().contains("Eliseu") && d.classificacao().equals("IMPORTAVEL"));
        var dir = java.nio.file.Path.of(".auditoria/qualidade-csv"); java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("preview-original.json"), resposta.getContentAsString(), StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(dir.resolve("original-sha256.txt"), ContratoGeracaoService.hash(original));
    }
    @Test void headerPerfilArquivoEColunaEssencialAusentesRetornam400() throws Exception {
        var p = perfil(); var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var arquivo = csv("nomeCompleto;cpf;statusExtracao\nJoão;00000000002;OK");
        assertThat(mvc.perform(multipart("/import/clientes").file(arquivo)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(multipart("/import/clientes").file(arquivo).header("X-Perfil-Id", Long.MAX_VALUE)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(multipart("/import/clientes").header("X-Perfil-Id", p.getId())).andReturn().getResponse().getStatus()).isEqualTo(400);
        var resposta = mvc.perform(multipart("/import/clientes").file(csv("nomeCompleto;statusExtracao\nJoão;OK"))
            .header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(400); assertThat(resposta.getContentAsString()).contains("coluna cpf não encontrada");
        assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"a.pdf", "a.xlsx", "a.txt"})
    void arquivoNaoCsvRetorna400(String nome) throws Exception {
        var p = perfil(); var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var resposta = mvc.perform(multipart("/import/clientes").file(new MockMultipartFile("file", nome, "text/plain", new byte[]{1}))
            .header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(400); assertThat(resposta.getContentAsString()).contains("Envie um arquivo CSV válido");
    }
    @Test void malformadoNoFimNaoGravaAsLinhasIniciais() throws Exception {
        var p = perfil(); var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var resposta = mvc.perform(multipart("/import/clientes").file(csv("nomeCompleto;cpf;statusExtracao\nJoão;00000000002;OK\n\"Maria;00000000003;OK"))
            .header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(400); assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
    }
    @Test void atualizaClienteComDoisEmprestimosDezParcelasSemTocarHistoricoFinanceiroOuDocumental() throws Exception {
        var f = new TransactionTemplate(tm).execute(t -> {
            var fixture = ContratoDocxFixture.criar(perfis, clientes, emprestimoService);
            emprestimoService.criarEmprestimo(fixture.perfil().getId(), fixture.devedor(), new java.math.BigDecimal("700"), 7,
                java.math.BigDecimal.ZERO, FormaPagamento.PIX, FrequenciaPagamento.MENSAL, TipoContrato.FISICO, fixture.fiador().getId());
            return fixture;
        });
        var meta = geracao.gerar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId());
        var antigo = contratos.findById(meta.id()).orElseThrow();
        byte[] docx = antigo.getDocx(), pdf = antigo.getPdfFinal(), assinado = antigo.getPdfFinalAssinado();
        String snapshot = antigo.getSnapshotJson(), promissoria = antigo.getPromissoriaSnapshotJson();
        String hash = antigo.getHashDocumento(), hashPdf = antigo.getHashPdfFinal(), hashAssinado = antigo.getHashPdfAssinado();
        var financeiro = jdbc.queryForList("select * from emprestimos where perfil_id = ? order by id", f.perfil().getId());
        var cronograma = jdbc.queryForList("select p.* from parcelas p join emprestimos e on p.emprestimo_id = e.id where e.perfil_id = ? order by p.id", f.perfil().getId());
        assertThat(financeiro).hasSize(2); assertThat(cronograma).hasSize(10);
        var atual = clientes.findById(f.devedor().getId()).orElseThrow(); atual.setEnderecoRua(null); atual.setCpf(null); clientes.saveAndFlush(atual);
        String entrada = "nomeCompleto;cpf;statusExtracao;enderecoRua\nDevedor de teste;00000000002;OK;Rua Nova";
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var resposta = mvc.perform(multipart("/import/clientes").file(csv(entrada)).header("X-Perfil-Id", f.perfil().getId())).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200); assertThat(json.readTree(resposta.getContentAsString()).get("atualizados").asInt()).isEqualTo(1);
        assertThat(json.readTree(resposta.getContentAsString()).get("clientesEncontradosPorNome").asInt()).isEqualTo(1);
        assertThat(clientes.findById(f.devedor().getId()).orElseThrow().getCpf()).isEqualTo("000.000.000-02");
        assertThat(clientes.findById(f.devedor().getId()).orElseThrow().getEnderecoRua()).isEqualTo("Rua Nova");
        assertThat(jdbc.queryForList("select * from emprestimos where perfil_id = ? order by id", f.perfil().getId())).isEqualTo(financeiro);
        assertThat(jdbc.queryForList("select p.* from parcelas p join emprestimos e on p.emprestimo_id = e.id where e.perfil_id = ? order by p.id", f.perfil().getId())).isEqualTo(cronograma);
        var depois = contratos.findById(meta.id()).orElseThrow();
        assertThat(depois.getSnapshotJson()).isEqualTo(snapshot); assertThat(depois.getPromissoriaSnapshotJson()).isEqualTo(promissoria);
        assertThat(depois.getHashDocumento()).isEqualTo(hash); assertThat(depois.getHashPdfFinal()).isEqualTo(hashPdf); assertThat(depois.getHashPdfAssinado()).isEqualTo(hashAssinado);
        String base = "/clientes/" + f.devedor().getId() + "/emprestimos/" + f.emprestimo().getId() + "/contratos/" + meta.id();
        for (var formato : java.util.Map.of("download", docx, "download-pdf", pdf, "download-pdf-assinado", assinado).entrySet()) {
            var download = mvc.perform(get(base + "/" + formato.getKey()).header("X-Perfil-Id", f.perfil().getId())).andReturn().getResponse();
            assertThat(download.getStatus()).isEqualTo(200); assertThat(download.getContentAsByteArray()).isEqualTo(formato.getValue());
        }
        assertThat(contratos.listar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId())).hasSize(1);
    }
}
