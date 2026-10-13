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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class PromissoriaPdfIntegrationTests {
    @Autowired ContratoGeracaoService service;
    @Autowired ContratoService dados;
    @Autowired ContratoSnapshotService snapshots;
    @Autowired PromissoriaService promissorias;
    @Autowired DocxPdfConversor conversor;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoService emprestimos;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    ContratoDocxFixture fixture() { return ContratoDocxFixture.criar(perfis,clientes,emprestimos); }
    ContratoSnapshotDTO snapshot(ContratoDocxFixture f, LocalDate date) {
        return snapshots.montar(dados.buscarDadosContratoModeloFixo(f.perfil().getId(), f.devedor().getId(),
                f.emprestimo().getId(), ContratoSnapshotService.CREDOR), date);
    }
    @ParameterizedTest
    @CsvSource({"2026-10-05,2026-11-10", "2026-10-21,2026-11-26", "2026-11-10,2026-12-15",
            "2026-12-20,2027-01-25", "2026-01-31,2026-03-05", "2027-02-28,2027-04-02"})
    void vencimentoSegueLiteralmenteUmMesMaisCincoDias(String emissao,String vencimento) {
        var p=promissorias.montar(snapshot(fixture(),LocalDate.parse(emissao)));
        assertThat(p.dataEmissao()).isEqualTo(LocalDate.parse(emissao));
        assertThat(p.vencimentoPromissoria()).isEqualTo(LocalDate.parse(vencimento));
    }
    @Test void geraPdfRealComSnapshotMergeHashEFidelidadeDoHistorico() throws Exception {
        var f=fixture();
        f.devedor().setEnderecoCep("00100000");
        f.devedor().setNome("MARINA APARECIDA OLIVEIRA SAMPAIO");
        f.devedor().setEnderecoRua("Rua Músico Pedro Moreira");
        f.devedor().setEnderecoBairro("Jardim das Flores de Primavera");
        byte[] oldPdf; String oldJson; long oldId;
        var instant=OffsetDateTime.parse("2026-10-05T15:00:00Z");
        try (var time=mockStatic(OffsetDateTime.class,CALLS_REAL_METHODS)) {
            time.when(() -> OffsetDateTime.now(ZoneOffset.UTC)).thenReturn(instant);
            var meta=service.gerar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId());
            oldId=meta.id();
            var c=contratos.findById(oldId).orElseThrow();
            oldPdf=service.baixarPdf(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),oldId).bytes();
            oldJson=c.getPromissoriaSnapshotJson();
            var p=json.readValue(oldJson,PromissoriaSnapshotDTO.class);
            assertThat(p.avalistaNome()).isEqualTo(f.fiador().getNome());
            assertThat(p.emitenteNome()).isEqualTo(f.devedor().getNome());
            assertThat(p.dataEmissao()).isEqualTo(LocalDate.of(2026,10,5));
            assertThat(p.vencimentoPromissoria()).isEqualTo(LocalDate.of(2026,11,10));
            assertThat(p.valor()).isEqualTo("3.751,50");
            assertThat(p.emitenteEndereco()).contains("Cep 00100-000").doesNotContain("null");
            byte[] promissoria=promissorias.gerar(p);
            try (var combined=Loader.loadPDF(oldPdf); var converted=Loader.loadPDF(conversor.converter(c.getDocx()))) {
                assertThat(combined.getNumberOfPages()).isEqualTo(converted.getNumberOfPages()+1);
                var last=new PDFTextStripper(); last.setStartPage(combined.getNumberOfPages()); last.setEndPage(combined.getNumberOfPages());
                String text=last.getText(combined);
                assertThat(text).contains("EDSON ANTONIO VIEIRA", "072.848.058-11", "INDAIATUBA/SP",
                        "MARINA APARECIDA OLIVEIRA", "SAMPAIO", "000.000.000-02", "00100-000",
                        "3.751,50", "Três mil setecentos e cinquenta e um reais e cinquenta centavos", "05/10/2026", "novembro")
                        .doesNotContain("SANDRA", "MARCIA", "MÁRCIA", "319.746.898-83", "184.248.818.00", "440,00",
                                "Quatrocentos", "01/10/2026", "28 de outubro de 2026", "Pessoa do Perfil", "null", "undefined");
                // PDFTextStripper emits rotated labels/avalista as separate characters; preserve their order.
                assertThat(text.replaceAll("\\s", "")).contains("FIADORDETESTE", "000.000.000-03");
                assertThat(new PDFTextStripper().getText(combined)).contains("Ivani Elias Machado Vieira", "CLAUSULA 15ª");
            }
            assertThat(sha(oldPdf)).isEqualTo(c.getHashPdfFinal());
            byte[] copy=c.getPdfFinal(); copy[0]=0; assertThat(c.getPdfFinal()).isEqualTo(oldPdf);
            Files.createDirectories(Path.of("target/artifacts"));
            Files.write(Path.of("target/artifacts/contrato-completo-teste.pdf"),oldPdf);
            Files.write(Path.of("target/artifacts/promissoria-teste.pdf"),promissoria);
        }
        f.devedor().setEnderecoRua("Rua B"); f.fiador().setEnderecoRua("Rua Fiador B");
        f.devedor().setCpf("00000000004"); f.fiador().setCpf("00000000005");
        em.flush();
        assertThat(service.baixarPdf(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),oldId).bytes()).isEqualTo(oldPdf);
        var nova=service.gerar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId());
        em.flush(); em.clear();
        var old=contratos.findById(oldId).orElseThrow(); var fresh=contratos.findById(nova.id()).orElseThrow();
        assertThat(old.getPromissoriaSnapshotJson()).isEqualTo(oldJson);
        assertThat(old.getPdfFinal()).isEqualTo(oldPdf); assertThat(sha(old.getPdfFinal())).isEqualTo(old.getHashPdfFinal());
        try(var pdf=Loader.loadPDF(fresh.getPdfFinal())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Rua B", "Rua Fiador B", "000.000.000-04", "000.000.000-05");
        }
        assertThat(fresh.getId()).isNotEqualTo(oldId);
    }
    @Test void rejeitaDadosAusentesETextoQueNaoCabeSemTruncamento() {
        var f=fixture(); var s=snapshot(f,LocalDate.of(2026,10,5));
        var semFiador=new ContratoSnapshotDTO(s.emprestimoId(),s.versaoTemplate(),s.dataContrato(),s.devedor(),null,
                s.emprestimo(),s.parcelas(),s.credorFixo(),s.testemunhaFixa(),s.camposDocumento());
        assertThatThrownBy(() -> promissorias.montar(semFiador)).hasMessageContaining("Snapshot incompleto");
        var p=promissorias.montar(s);
        var longo=new PromissoriaSnapshotDTO(p.versaoTemplate(),"NomeMuitoLongo".repeat(100),p.avalistaCpf(),p.emitenteNome(),p.emitenteCpf(),
                p.emitenteEndereco(),p.valor(),p.valorExtenso(),p.dataEmissao(),p.vencimentoPromissoria(),p.favorecido(),p.favorecidoCpf(),p.localPagamento());
        assertThatThrownBy(() -> promissorias.gerar(longo)).hasMessageContaining("fonte legível");
    }
    @Test void legadoContinuaComWordMasNaoGeraPdfDuranteDownload() {
        var f=fixture();
        var c=new ContratoGerado(f.emprestimo(),f.perfil(),"CONFISSAO_DIVIDA_V1",OffsetDateTime.now(),"{}","hash", "legado.docx",new byte[]{1});
        contratos.saveAndFlush(c);
        assertThat(service.listar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId()).get(0).pdfDisponivel()).isFalse();
        assertThatThrownBy(() -> service.baixarPdf(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),c.getId()))
                .hasMessageContaining("não possui PDF armazenado");
    }
    static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b)); }
    @Test void conversoesParalelasNaoMisturamTemporarios() throws Exception {
        byte[] source;
        try(var in=getClass().getResourceAsStream("/templates/source/contrato-original-v1.docx")) { source=in.readAllBytes(); }
        var one=java.util.concurrent.CompletableFuture.supplyAsync(() -> conversor.converter(source));
        var two=java.util.concurrent.CompletableFuture.supplyAsync(() -> conversor.converter(source));
        try(var a=Loader.loadPDF(one.get(120,java.util.concurrent.TimeUnit.SECONDS));
            var b=Loader.loadPDF(two.get(120,java.util.concurrent.TimeUnit.SECONDS))) {
            assertThat(a.getNumberOfPages()).isEqualTo(b.getNumberOfPages());
            assertThat(new PDFTextStripper().getText(a)).isEqualTo(new PDFTextStripper().getText(b));
        }
    }
}
