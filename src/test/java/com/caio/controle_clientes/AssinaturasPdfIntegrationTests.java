package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@Transactional
class AssinaturasPdfIntegrationTests {
    @Autowired ContratoGeracaoService service;
    @Autowired AssinaturasPdfService assinaturas;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoService emprestimos;
    @Autowired EntityManager em;
    @Autowired WebApplicationContext context;

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void geraPersisteEDownloadHistoricoPreservaPdfNormalEPromissoria(boolean longo) throws Exception {
        var f=ContratoDocxFixture.criar(perfis,clientes,emprestimos);
        f.devedor().setNome(longo ? "MARINA APARECIDA OLIVEIRA SAMPAIO DA SILVA" : "Ana");
        f.fiador().setNome(longo ? "JOÃO CARLOS OLIVEIRA SAMPAIO" : "João");
        var meta=service.gerar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId());
        assertThat(meta.pdfDisponivel()).isTrue(); assertThat(meta.pdfAssinadoDisponivel()).isTrue();
        long id=meta.id(), perfil=f.perfil().getId(), cliente=f.devedor().getId(), emprestimo=f.emprestimo().getId();
        var c=contratos.findById(id).orElseThrow();
        byte[] normal=c.getPdfFinal(), assinado=c.getPdfFinalAssinado(), word=c.getDocx(); String snapshot=c.getSnapshotJson();
        assertThat(ContratoGeracaoService.hash(normal)).isEqualTo(c.getHashPdfFinal());
        assertThat(ContratoGeracaoService.hash(assinado)).isEqualTo(c.getHashPdfAssinado());
        assertThat(assinado).isNotEqualTo(normal);
        byte[] copia=normal.clone(); assinaturas.gerar(normal); assertThat(normal).isEqualTo(copia);
        try(var a=Loader.loadPDF(normal); var b=Loader.loadPDF(assinado)) {
            assertThat(b.getNumberOfPages()).isEqualTo(a.getNumberOfPages());
            assertThat(new PDFTextStripper().getText(b)).isEqualTo(new PDFTextStripper().getText(a));
            int imagensAdicionadas=0, paginasAlteradas=0;
            var ra=new PDFRenderer(a); var rb=new PDFRenderer(b);
            for(int p=0;p<a.getNumberOfPages();p++) {
                int imagensA=0,imagensB=0;
                for(var n:a.getPage(p).getResources().getXObjectNames())
                    if(a.getPage(p).getResources().getXObject(n) instanceof PDImageXObject) imagensA++;
                for(var n:b.getPage(p).getResources().getXObjectNames())
                    if(b.getPage(p).getResources().getXObject(n) instanceof PDImageXObject) imagensB++;
                imagensAdicionadas+=imagensB-imagensA;
                var ia=ra.renderImageWithDPI(p,100); var ib=rb.renderImageWithDPI(p,100);
                int[] pa=ia.getRGB(0,0,ia.getWidth(),ia.getHeight(),null,0,ia.getWidth());
                int[] pb=ib.getRGB(0,0,ib.getWidth(),ib.getHeight(),null,0,ib.getWidth());
                if(!Arrays.equals(pa,pb)) paginasAlteradas++;
                if(p==a.getNumberOfPages()-1) assertThat(pb).isEqualTo(pa); // Promissória idêntica.
            }
            assertThat(imagensAdicionadas).isEqualTo(2); assertThat(paginasAlteradas).isBetween(1,2);
            var out=Path.of("target/assinaturas-qa"); Files.createDirectories(out);
            String nome=longo ? "nomes-longos" : "nomes-curtos";
            Files.write(out.resolve(nome+"-normal.pdf"),normal); Files.write(out.resolve(nome+"-assinaturas.pdf"),assinado);
            Files.writeString(out.resolve(nome+"-paginas.txt"),"Contrato: "+(a.getNumberOfPages()-1)+"; total: "+a.getNumberOfPages());
            if(!longo) {
                assertThat(a.getNumberOfPages()-1).isEqualTo(3);
                // Inserir página neutra antes do contrato real testa a paginação 4 sem modificar o template.
                a.getPages().insertBefore(new org.apache.pdfbox.pdmodel.PDPage(a.getPage(0).getMediaBox()),a.getPage(0));
                var buffer=new java.io.ByteArrayOutputStream(); a.save(buffer);
                byte[] quatro=buffer.toByteArray(), quatroAssinado=assinaturas.gerar(quatro);
                try(var docQuatro=Loader.loadPDF(quatroAssinado)) {
                    assertThat(docQuatro.getNumberOfPages()).isEqualTo(5);
                    var stripper=new PDFTextStripper(); stripper.setStartPage(4); stripper.setEndPage(4);
                    assertThat(stripper.getText(docQuatro)).contains("CREDOR:", "TESTEMUNHA:");
                    int quantidade=0;
                    for(var n:docQuatro.getPage(3).getResources().getXObjectNames())
                        if(docQuatro.getPage(3).getResources().getXObject(n) instanceof PDImageXObject) quantidade++;
                    assertThat(quantidade).isEqualTo(2);
                }
                Files.write(out.resolve("paginacao-4-normal.pdf"),quatro);
                Files.write(out.resolve("paginacao-4-assinaturas.pdf"),quatroAssinado);
            }
        }
        var mvc=MockMvcBuilders.webAppContextSetup(context).build();
        String rota="/clientes/"+cliente+"/emprestimos/"+emprestimo+"/contratos/"+id+"/download-pdf-assinado";
        var resposta=mvc.perform(get(rota).header("X-Perfil-Id",perfil)).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200); assertThat(resposta.getContentType()).isEqualTo("application/pdf");
        assertThat(resposta.getHeader("Content-Disposition")).contains("attachment", "contrato-emprestimo-"+emprestimo+"-"+id+"-assinado.pdf");
        assertThat(resposta.getContentAsByteArray()).isEqualTo(assinado);
        assertThat(mvc.perform(get(rota)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(get(rota).header("X-Perfil-Id",perfil+999)).andReturn().getResponse().getStatus()).isEqualTo(404);
        for(String incorreta:new String[]{rota.replace("/clientes/"+cliente+"/","/clientes/"+(cliente+999)+"/"),
            rota.replace("/emprestimos/"+emprestimo+"/","/emprestimos/"+(emprestimo+999)+"/"),
            rota.replace("/contratos/"+id+"/","/contratos/"+(id+999)+"/")})
            assertThat(mvc.perform(get(incorreta).header("X-Perfil-Id",perfil)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(service.listar(perfil,cliente,emprestimo).get(0).pdfAssinadoDisponivel()).isTrue();
        f.devedor().setNome("Cadastro alterado depois"); em.flush(); em.clear();
        c=contratos.findById(id).orElseThrow();
        assertThat(c.getSnapshotJson()).isEqualTo(snapshot); assertThat(c.getDocx()).isEqualTo(word);
        assertThat(service.baixarPdf(perfil,cliente,emprestimo,id).bytes()).isEqualTo(normal);
        assertThat(service.baixarPdfAssinado(perfil,cliente,emprestimo,id).bytes()).isEqualTo(assinado);
    }

    @Test void contratoAntigoPermaneceDisponivelSemRegeneracao() {
        var f=ContratoDocxFixture.criar(perfis,clientes,emprestimos);
        byte[] normal={1,2,3},word={4,5,6};
        var c=contratos.saveAndFlush(new ContratoGerado(f.emprestimo(),f.perfil(),"V1",OffsetDateTime.now(),"{}",
            ContratoGeracaoService.hash(word),"legado.docx",word,"{}",ContratoGeracaoService.hash(normal),normal));
        assertThat(service.listar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId()).get(0).pdfAssinadoDisponivel()).isFalse();
        assertThat(service.baixarPdf(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),c.getId()).bytes()).isEqualTo(normal);
        assertThatThrownBy(()->service.baixarPdfAssinado(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),c.getId())).hasMessageContaining("contrato antigo");
    }

    @Test void documentoSemAreasConfiaveisNaoRecebeAssinaturasAleatorias() {
        assertThatThrownBy(()->assinaturas.gerar(new byte[]{1,2,3})).hasMessageContaining("posicionar");
    }

    @Test void hashIncorretoBloqueiaDownloadAssinadoSemModificarPdfNormal() {
        var f=ContratoDocxFixture.criar(perfis,clientes,emprestimos);
        byte[] normal={1,2,3},word={4,5,6},assinado={7,8,9};
        var c=contratos.saveAndFlush(new ContratoGerado(f.emprestimo(),f.perfil(),"V1",OffsetDateTime.now(),"{}",
            ContratoGeracaoService.hash(word),"teste.docx",word,"{}",ContratoGeracaoService.hash(normal),normal,
            "hash-incorreto",assinado));
        assinado[0]=99;
        assertThat(c.getPdfFinalAssinado()[0]).isEqualTo((byte)7);
        byte[] copia=c.getPdfFinalAssinado(); copia[0]=99;
        assertThat(c.getPdfFinalAssinado()[0]).isEqualTo((byte)7);
        assertThatThrownBy(()->service.baixarPdfAssinado(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),c.getId()))
            .hasMessageContaining("integridade");
        assertThat(service.baixarPdf(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),c.getId()).bytes()).isEqualTo(normal);
    }
}
