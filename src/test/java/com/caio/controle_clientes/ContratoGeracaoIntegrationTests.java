package com.caio.controle_clientes;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import jakarta.persistence.EntityManager;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class ContratoGeracaoIntegrationTests {
    @Autowired ContratoGeracaoService service;
    @Autowired ContratoService dadosService;
    @Autowired ContratoSnapshotService snapshots;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired EmprestimoService emprestimos;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;

    @Test void geraDoWordOficialPreservandoFixosClausulasEstilosEValoresDoSnapshot() throws Exception {
        var f = fixture();
        var meta = gerar(f);
        var contrato = contratos.findById(meta.id()).orElseThrow();
        var snapshot = json.readValue(contrato.getSnapshotJson(), ContratoSnapshotDTO.class);
        byte[] bytes = service.baixar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId(), meta.id()).bytes();
        String texto = texto(bytes);
        assertThat(texto).contains("Edson Antonio Vieira", "(RG) nº 17.888.629-4", "CPF nº 072.848.058-11",
                "Ivani Elias Machado Vieira", "CPF: 215.637.868-18", "RG: 30.623.886-X",
                "Devedor de Teste", "Fiador de Teste", "brasileira", "união estável", "professor", "00123-X", "SSP/SP",
                "000.000.000-02", "000.000.000-03", "Rua A, Nº 15, Centro, Cep 00100-000, Cidade — SP",
                "Rua Fiador A, Nº 15, Centro, Cep 00100-000, Cidade — SP",
                "R$ 3.751,50", "três mil setecentos e cinquenta e um reais e cinquenta centavos",
                "3 (três) parcelas", "R$ 1.250,50", "mil duzentos e cinquenta reais e cinquenta centavos",
                "todo dia 15 de cada mês.", "15/11/2026", "15/01/2027",
                "1 (uma) promissória", "multa de 2%", "juros mensais de 1%")
                .doesNotContain("Pessoa do Perfil diferente do Edson", "00000000001", "{{", "}}", ", null,");
        assertThat(snapshot.credorFixo()).isEqualTo(ContratoSnapshotService.CREDOR);
        assertThat(snapshot.testemunhaFixa()).isEqualTo(ContratoSnapshotService.TESTEMUNHA);
        assertThat(snapshot.dataContrato()).isEqualTo(meta.dataGeracao()
                .atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toLocalDate());
        assertThat(texto).contains("INDAIATUBA, " + ContratoFormatador.dataExtenso(snapshot.dataContrato()));
        assertThat(snapshot.camposDocumento().get("VALOR_TOTAL"))
                .isEqualTo(snapshot.camposDocumento().get("VALOR_PROMISSORIA"));
        assertThat(meta.hashDocumento()).isEqualTo(ContratoGeracaoService.hash(bytes));
        assertThat(meta.nomeArquivo()).matches("contrato-emprestimo-\\d+-[a-f0-9-]+\\.docx");

        // Preserva o template em uso, incluindo metadados atualizados no Word desde a cópia de origem.
        // As cláusulas fixas continuam comparadas separadamente à cópia oficial abaixo.
        Map<String,byte[]> original = partes(resource("templates/contrato-confissao-divida-v1.docx"));
        Map<String,byte[]> gerado = partes(bytes);
        assertThat(gerado.keySet()).isEqualTo(original.keySet());
        for (String nome : original.keySet()) {
            if (!nome.equals("word/document.xml")) assertThat(gerado.get(nome)).as(nome).isEqualTo(original.get(nome));
        }
        String xmlOriginal = new String(original.get("word/document.xml"), StandardCharsets.UTF_8);
        String xmlGerado = new String(gerado.get("word/document.xml"), StandardCharsets.UTF_8);
        String semTexto = "(?s)(<w:t(?:\\s[^>]*)?>).*?(</w:t>)";
        assertThat(xmlGerado.replaceAll(semTexto,"$1$2")).isEqualTo(xmlOriginal.replaceAll(semTexto,"$1$2"));
        try (var antigo = new XWPFDocument(new ByteArrayInputStream(resource("templates/source/contrato-original-v1.docx")));
             var novo = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            Set<Integer> dinamicos = Set.of(2,9,12,13,14,23,51,54,60);
            assertThat(novo.getParagraphs()).hasSize(66);
            for (int i = 0; i < 66; i++) if (!dinamicos.contains(i)) {
                assertThat(novo.getParagraphs().get(i).getText()).as("parágrafo fixo " + i)
                        .isEqualTo(antigo.getParagraphs().get(i).getText());
            }
        }
        Path qa = Path.of("target/contrato-qa");
        Files.createDirectories(qa);
        Files.write(qa.resolve("contrato-exemplo.docx"), bytes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cpfECepFormatadosNoDocumentoMantemOriginaisESnapshotDosTextosUsados(boolean jaFormatados) throws Exception {
        var f = fixture();
        String cpfDevedor = jaFormatados ? "000.000.000-02" : "00000000002";
        String cpfFiador = jaFormatados ? "000.000.000-03" : "00000000003";
        String cep = jaFormatados ? "00100-000" : "00100000";
        f.devedor().setCpf(cpfDevedor); f.fiador().setCpf(cpfFiador);
        f.devedor().setEnderecoCep(cep); f.fiador().setEnderecoCep(cep);
        f.devedor().setRg("00123-X"); f.fiador().setRg("00.123/AB-9");
        var meta = gerar(f);
        em.flush(); em.clear();
        var c = contratos.findById(meta.id()).orElseThrow();
        var snapshot = json.readValue(c.getSnapshotJson(), ContratoSnapshotDTO.class);
        assertThat(snapshot.devedor().cpf()).isEqualTo(cpfDevedor);
        assertThat(snapshot.fiador().cpf()).isEqualTo(cpfFiador);
        assertThat(snapshot.devedor().enderecoCep()).isEqualTo(cep);
        assertThat(snapshot.fiador().enderecoCep()).isEqualTo(cep);
        assertThat(snapshot.camposDocumento()).containsEntry("DEVEDOR_CPF", "000.000.000-02")
                .containsEntry("FIADOR_CPF", "000.000.000-03")
                .containsEntry("DEVEDOR_RG", "00123-X").containsEntry("FIADOR_RG", "00.123/AB-9");
        for (String papel : List.of("DEVEDOR", "FIADOR")) {
            assertThat(snapshot.camposDocumento().get(papel + "_ENDERECO")).contains("Cep 00100-000");
        }
        assertThat(texto(baixar(f, meta.id()))).contains("000.000.000-02", "000.000.000-03",
                "Cep 00100-000", "00123-X", "00.123/AB-9");
    }

    @Test void dataDaGeracaoPersistidaNaoMudaNoDownloadENovaGeracaoTemSuaPropriaData() throws Exception {
        var f = fixture();
        // 02:30 UTC de 06/10 ainda é 05/10 em São Paulo; não usa data do empréstimo (02/10).
        var primeiroInstante = OffsetDateTime.parse("2026-10-06T02:30:00Z");
        var segundoInstante = OffsetDateTime.parse("2026-10-10T15:00:00Z");
        try (var tempo = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS)) {
            tempo.when(() -> OffsetDateTime.now(ZoneOffset.UTC)).thenReturn(primeiroInstante);
            var um = gerar(f);
            byte[] antigos = baixar(f, um.id());
            String snapshotAntigo = contratos.findById(um.id()).orElseThrow().getSnapshotJson();
            var snapshotUm = json.readValue(snapshotAntigo, ContratoSnapshotDTO.class);
            assertThat(um.dataGeracao()).isEqualTo(primeiroInstante);
            assertThat(snapshotUm.dataContrato()).isEqualTo(LocalDate.of(2026,10,5));
            assertThat(snapshotUm.camposDocumento()).containsEntry("DATA_CONTRATO_EXTENSO", "5 de outubro de 2026");
            assertThat(texto(antigos)).contains("INDAIATUBA, 5 de outubro de 2026");
            tempo.when(() -> OffsetDateTime.now(ZoneOffset.UTC)).thenReturn(segundoInstante);
            var dois = gerar(f);
            em.flush(); em.clear();
            var snapshotDois = json.readValue(contratos.findById(dois.id()).orElseThrow().getSnapshotJson(), ContratoSnapshotDTO.class);
            assertThat(dois.id()).isNotEqualTo(um.id());
            assertThat(dois.dataGeracao()).isEqualTo(segundoInstante);
            assertThat(snapshotDois.dataContrato()).isEqualTo(LocalDate.of(2026,10,10));
            assertThat(texto(baixar(f, dois.id()))).contains("INDAIATUBA, 10 de outubro de 2026");
            assertThat(baixar(f, um.id())).isEqualTo(antigos);
            assertThat(texto(baixar(f, um.id()))).contains("INDAIATUBA, 5 de outubro de 2026")
                    .doesNotContain("INDAIATUBA, 10 de outubro de 2026");
            assertThat(contratos.findById(um.id()).orElseThrow().getSnapshotJson()).isEqualTo(snapshotAntigo);
            for (var meta : List.of(um, dois)) {
                assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(baixar(f, meta.id())))).isEqualTo(meta.hashDocumento());
            }
            assertThatThrownBy(() -> snapshotDois.camposDocumento().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test void persisteBytesBinariosEJsonEProtegeContraMutacaoDosArrays() throws Exception {
        var f = fixture();
        var meta = gerar(f);
        em.flush(); em.clear();
        var c = contratos.findById(meta.id()).orElseThrow();
        byte[] persistido = c.getDocx();
        assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(persistido)))
                .isEqualTo(c.getHashDocumento());
        byte[] copia = c.getDocx(); copia[0] = 0;
        assertThat(c.getDocx()).isEqualTo(persistido);
        var snapshot = json.readValue(c.getSnapshotJson(), ContratoSnapshotDTO.class);
        assertThatThrownBy(() -> snapshot.parcelas().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.camposDocumento().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void mudancasNoDevedorFiadorPerfilEFinanceiroNaoAlteramHistoricoENovaGeracaoCriaVersaoNova() throws Exception {
        var f = fixture();
        var um = gerar(f);
        byte[] antigos = baixar(f, um.id());
        String snapshotAntigo = contratos.findById(um.id()).orElseThrow().getSnapshotJson();
        f.devedor().setEnderecoRua("Rua B");
        f.fiador().setEnderecoRua("Rua Fiador B");
        f.perfil().setCpf("99999999999");
        em.flush();
        assertThat(baixar(f, um.id())).isEqualTo(antigos);
        assertThat(texto(baixar(f, um.id()))).contains("Rua A", "Rua Fiador A")
                .doesNotContain("Rua B", "Rua Fiador B");
        var dois = gerar(f);
        assertThat(dois.id()).isNotEqualTo(um.id());
        assertThat(service.listar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId()))
                .extracting(ContratoGeradoMetadadosDTO::id).containsExactly(dois.id(),um.id());
        assertThat(texto(baixar(f, dois.id()))).contains("Rua B", "Rua Fiador B").doesNotContain("99999999999");
        f.emprestimo().setFiador(null);
        emprestimos.quitarEmprestimo(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId());
        em.flush(); em.clear();
        assertThat(baixar(f, um.id())).isEqualTo(antigos);
        assertThat(texto(baixar(f, um.id()))).contains("Rua A", "Rua Fiador A").doesNotContain("Rua B");
        assertThat(contratos.findById(um.id()).orElseThrow().getSnapshotJson()).isEqualTo(snapshotAntigo);
    }

    @Test void perfilIncompletoNaoSubstituiCredorFixoNemMudaRegraDoEndpointExistente() {
        var f = fixture();
        f.perfil().setNomeCompleto(null); f.perfil().setCpf(null);
        assertThatThrownBy(() -> dadosService.buscarDadosContrato(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId()))
                .hasMessageContaining("Dados do credor incompletos");
        assertThat(gerar(f).id()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value=FrequenciaPagamento.class, names="MENSAL", mode=EnumSource.Mode.EXCLUDE)
    void rejeitaFrequenciasNaoMensaisSemPersistirSucesso(FrequenciaPagamento frequencia) {
        var f = fixture(); f.emprestimo().setFrequenciaPagamento(frequencia);
        assertThatThrownBy(() -> gerar(f)).hasMessageContaining("apenas empréstimos com frequência MENSAL");
        assertThat(contratos.count()).isZero();
    }
    @Test void rejeitaDevedorFiadorIncompletosSemFiadorSemParcelasETotaisDivergentes() {
        var f = fixture();
        f.devedor().setCpf(null);
        assertThatThrownBy(() -> gerar(f)).hasMessageContaining("Dados do devedor incompletos: cpf.");
        f.devedor().setCpf("00000000002"); f.fiador().setRg(null);
        assertThatThrownBy(() -> gerar(f)).hasMessageContaining("Dados do fiador incompletos: rg.");
        f.fiador().setRg("00123-X"); f.emprestimo().setFiador(null);
        assertThatThrownBy(() -> gerar(f)).hasMessage("O empréstimo não possui fiador cadastrado.");
        f.emprestimo().setFiador(f.fiador());
        f.emprestimo().setValorTotalEmprestimo(new java.math.BigDecimal("9999"));
        assertThatThrownBy(() -> gerar(f)).hasMessageContaining("inconsistentes para o contrato");
        f.emprestimo().getParcelas().clear();
        assertThatThrownBy(() -> gerar(f)).hasMessage("O empréstimo não possui parcelas para gerar contrato.");
        assertThat(contratos.count()).isZero();
    }
    @Test void rejeitaEscoposErradosEmGeracaoListagemEDownload() {
        var f = fixture(); var outro = fixture(); var meta = gerar(f);
        assertThatThrownBy(() -> service.gerar(outro.perfil().getId(), f.devedor().getId(), f.emprestimo().getId())).hasMessageContaining("não encontrado");
        assertThatThrownBy(() -> service.listar(f.perfil().getId(), f.fiador().getId(), f.emprestimo().getId())).hasMessageContaining("não pertence ao cliente");
        for (var ids : List.of(new Long[]{outro.perfil().getId(),f.devedor().getId(),f.emprestimo().getId()},
                new Long[]{f.perfil().getId(),f.fiador().getId(),f.emprestimo().getId()},
                new Long[]{f.perfil().getId(),f.devedor().getId(),outro.emprestimo().getId()})) {
            assertThatThrownBy(() -> service.baixar(ids[0],ids[1],ids[2],meta.id())).hasMessageContaining("Contrato não encontrado");
        }
    }
    private ContratoDocxFixture fixture() { return ContratoDocxFixture.criar(perfis,clientes,emprestimos); }
    private ContratoGeradoMetadadosDTO gerar(ContratoDocxFixture f) { return service.gerar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId()); }
    private byte[] baixar(ContratoDocxFixture f, Long id) { return service.baixar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),id).bytes(); }
    private static String texto(byte[] bytes) throws IOException {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes)); var ex = new XWPFWordExtractor(doc)) { return ex.getText(); }
    }
    private static byte[] resource(String name) throws IOException {
        try (var in = ContratoGeracaoIntegrationTests.class.getClassLoader().getResourceAsStream(name)) { return Objects.requireNonNull(in).readAllBytes(); }
    }
    private static Map<String,byte[]> partes(byte[] bytes) throws IOException {
        Map<String,byte[]> out = new LinkedHashMap<>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e; while ((e = in.getNextEntry()) != null) out.put(e.getName(),in.readAllBytes());
        }
        return out;
    }
}
