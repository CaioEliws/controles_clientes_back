package com.caio.controle_clientes;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class PadronizacaoDocumentoIntegrationTests {
    @Autowired ContratoGeracaoService service;
    @Autowired ContratoService dados;
    @Autowired ContratoSnapshotService snapshots;
    @Autowired ContratoTemplateService template;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoService emprestimos;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    ContratoDocxFixture fixture() { return ContratoDocxFixture.criar(perfis, clientes, emprestimos); }

    @Test void ireneGeraWordPdfSnapshotHashEHistoricoReais() throws Exception {
        var f = fixture();
        var d = f.devedor();
        d.setNome("IRENE APARECIDA ARAÚJO DA SILVA"); d.setSexoDocumento(SexoDocumento.FEMININO);
        d.setNacionalidade("BRASIL"); d.setEstadoCivil(EstadoCivil.SOLTEIRO); d.setProfissao("vendedora");
        d.setRg("52779723x"); d.setRgOrgaoExpedidor("ssp/sp"); d.setCpf("40886987890");
        d.setEnderecoRua("RUA ALMIR ANTÔNIO ARTONI"); d.setEnderecoNumero(528);
        d.setEnderecoBairro("JARDIM DOS COLIBRIS"); d.setEnderecoCep("13349152");
        d.setEnderecoCidade("INDAIATUBA"); d.setEnderecoEstado("sp");
        f.fiador().setNome("JOÃO CARLOS DA SILVA"); f.fiador().setSexoDocumento(SexoDocumento.MASCULINO);
        f.fiador().setNacionalidade("brasileira"); f.fiador().setEstadoCivil(EstadoCivil.CASADO);
        f.fiador().setRgOrgaoExpedidor("SSP-mg"); f.fiador().setEnderecoEstado("SP");
        var meta = service.gerar(f.perfil().getId(), d.getId(), f.emprestimo().getId());
        var c = contratos.findById(meta.id()).orElseThrow();
        var snapshotJson = c.getSnapshotJson(); var notaJson = c.getPromissoriaSnapshotJson();
        var snapshot = json.readValue(snapshotJson, ContratoSnapshotDTO.class);
        assertThat(snapshot.devedor().nomeCompleto()).isEqualTo("IRENE APARECIDA ARAÚJO DA SILVA");
        assertThat(snapshot.devedor().rg()).isEqualTo("52779723x");
        assertThat(snapshot.devedor().sexoDocumento()).isEqualTo(SexoDocumento.FEMININO);
        assertThat(snapshot.camposDocumento()).containsEntry("DEVEDOR_NOME", "Irene Aparecida Araújo da Silva")
                .containsEntry("DEVEDOR_NACIONALIDADE", "brasileira").containsEntry("DEVEDOR_ESTADO_CIVIL", "solteira")
                .containsEntry("DEVEDOR_PROFISSAO", "vendedora").containsEntry("DEVEDOR_RG", "52.779.723-X")
                .containsEntry("DEVEDOR_RG_ORGAO", "SSP/SP").containsEntry("DEVEDOR_CPF", "408.869.878-90")
                .containsEntry("FIADOR_NACIONALIDADE", "brasileiro").containsEntry("FIADOR_ESTADO_CIVIL", "casado")
                .containsEntry("FIADOR_RG_ORGAO", "SSP/MG");
        String endereco = "Rua Almir Antônio Artoni, Nº 528, Jardim dos Colibris, Cep 13349-152, Indaiatuba — SP";
        assertThat(snapshot.camposDocumento()).containsEntry("DEVEDOR_ENDERECO", endereco);
        byte[] word = c.getDocx(), pdf = c.getPdfFinal();
        String texto = texto(word);
        assertThat(texto).contains("Irene Aparecida Araújo da Silva", "brasileira, solteira", "vendedora",
                "52.779.723-X", "SSP/SP", "408.869.878-90", endereco, "João Carlos da Silva", "brasileiro, casado", "SSP/MG");
        try (var arquivo = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(arquivo)).contains("Irene Aparecida Araújo da Silva", "solteira", "vendedora", "52.779.723-X", "13349-152");
            var nota = new PDFTextStripper(); nota.setStartPage(arquivo.getNumberOfPages());
            assertThat(nota.getText(arquivo)).contains("IRENE APARECIDA", "408.869.878-90", "13349-152", "EDSON ANTONIO VIEIRA");
        }
        try (var original = new XWPFDocument(getClass().getResourceAsStream("/templates/source/contrato-original-v1.docx"));
             var novo = new XWPFDocument(new ByteArrayInputStream(word))) {
            var dinamicos = java.util.Set.of(2,9,12,13,14,23,51,54,60);
            for (int i = 0; i < original.getParagraphs().size(); i++) if (!dinamicos.contains(i)) {
                assertThat(novo.getParagraphs().get(i).getText()).as("texto fixo " + i).isEqualTo(original.getParagraphs().get(i).getText());
            }
        }
        assertThat(texto).contains("Edson Antonio Vieira", "Ivani Elias Machado Vieira", "215.637.868-18", "30.623.886-X");
        assertThat(ContratoGeracaoService.hash(word)).isEqualTo(c.getHashDocumento());
        assertThat(ContratoGeracaoService.hash(pdf)).isEqualTo(c.getHashPdfFinal());
        Path qa = Path.of("target/padronizacao-qa"); Files.createDirectories(qa);
        Files.write(qa.resolve("contrato-irene.docx"), word); Files.write(qa.resolve("contrato-irene-completo.pdf"), pdf);
        Files.writeString(qa.resolve("snapshot-irene.json"), snapshotJson);
        d.setNome("Nome atualizado"); d.setSexoDocumento(SexoDocumento.MASCULINO); f.fiador().setSexoDocumento(SexoDocumento.FEMININO);
        var nova = service.gerar(f.perfil().getId(), d.getId(), f.emprestimo().getId());
        em.flush(); em.clear();
        var antigo = contratos.findById(meta.id()).orElseThrow();
        assertThat(antigo.getSnapshotJson()).isEqualTo(snapshotJson);
        assertThat(antigo.getPromissoriaSnapshotJson()).isEqualTo(notaJson);
        assertThat(service.baixar(f.perfil().getId(), d.getId(), f.emprestimo().getId(), meta.id()).bytes()).isEqualTo(word);
        assertThat(service.baixarPdf(f.perfil().getId(), d.getId(), f.emprestimo().getId(), meta.id()).bytes()).isEqualTo(pdf);
        assertThat(nova.id()).isNotEqualTo(meta.id());
        service.excluir(f.perfil().getId(), d.getId(), f.emprestimo().getId(), nova.id());
        assertThat(service.listar(f.perfil().getId(), d.getId(), f.emprestimo().getId())).extracting(ContratoGeradoMetadadosDTO::id).contains(meta.id()).doesNotContain(nova.id());
    }
    @ParameterizedTest @EnumSource(SexoDocumento.class)
    void mariaUsaSexoExplicitoEFiadorTemFlexaoIndependente(SexoDocumento sexo) throws Exception {
        var f = fixture(); f.devedor().setNome("Maria"); f.devedor().setSexoDocumento(sexo);
        f.devedor().setEstadoCivil(EstadoCivil.CASADO);
        f.fiador().setSexoDocumento(sexo == SexoDocumento.MASCULINO ? SexoDocumento.FEMININO : SexoDocumento.MASCULINO);
        f.fiador().setEstadoCivil(EstadoCivil.SOLTEIRO);
        var previa = service.previa(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId());
        assertThat(previa.dados().camposDocumento()).containsEntry("DEVEDOR_ESTADO_CIVIL", sexo == SexoDocumento.MASCULINO ? "casado" : "casada")
                .containsEntry("FIADOR_ESTADO_CIVIL", sexo == SexoDocumento.MASCULINO ? "solteira" : "solteiro");
    }
    @Test void legadoSemSexoOuSspSemUfTemErroClaroSemInferencia() {
        var f = fixture(); f.devedor().setSexoDocumento(null);
        assertThatThrownBy(() -> service.gerar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId())).hasMessageContaining("Informe o sexo/forma gramatical do devedor.");
        f.devedor().setSexoDocumento(SexoDocumento.FEMININO); f.fiador().setSexoDocumento(null);
        assertThatThrownBy(() -> service.previa(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId())).hasMessageContaining("Informe o sexo/forma gramatical do fiador.");
        f.fiador().setSexoDocumento(SexoDocumento.MASCULINO); f.devedor().setRgOrgaoExpedidor("ssp");
        assertThatThrownBy(() -> service.gerar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId())).hasMessageContaining("UF emissora do RG do devedor");
        for (String orgao : new String[]{"XX", "SSP/XX", "SSP-sp-invalido"}) {
            f.devedor().setRgOrgaoExpedidor(orgao);
            assertThatThrownBy(() -> service.previa(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId())).hasMessageContaining("UF emissora do RG do devedor");
        }
        assertThat(service.listar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId())).isEmpty();
    }
    static String texto(byte[] bytes) throws Exception {
        try (var word = new XWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new XWPFWordExtractor(word)) { return extractor.getText(); }
    }
}
