package com.caio.controle_clientes;

import com.caio.controle_clientes.dto.contrato.PromissoriaSnapshotDTO;
import com.caio.controle_clientes.services.contrato.PromissoriaService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PromissoriaVisualTests {
    private PromissoriaSnapshotDTO exemplo(String endereco) {
        return new PromissoriaSnapshotDTO("PROMISSORIA_V1", "CARLA REGINA DE ALMEIDA", "000.000.000-03",
                "MARINA APARECIDA OLIVEIRA SAMPAIO", "000.000.000-02", endereco,
                "3.751,50", "três mil setecentos e cinquenta e um reais e cinquenta centavos",
                LocalDate.of(2026,10,5),LocalDate.of(2026,11,10),"EDSON ANTONIO VIEIRA",
                "072.848.058-11","INDAIATUBA/SP");
    }
    @Test void nomeEEnderecoLongosCabemSemInvadirLabelsCpfAssinaturaOuBorda() throws Exception {
        var p = exemplo("Rua Músico Pedro Moreira, Nº 101, Jardim das Flores de Primavera, Cep 00100-000, Cidade — SP");
        try (var doc = Loader.loadPDF(new PromissoriaService().gerar(p))) {
            var posicoes = new Posicoes();
            String texto = posicoes.getText(doc);
            assertThat(texto).contains("MARINA APARECIDA OLIVEIRA", "SAMPAIO", "RUA MÚSICO PEDRO MOREIRA - 101 -",
                    "JARDIM DAS", "FLORES DE PRIMAVERA", "00100-000 - CIDADE - SP");
            var nome = posicoes.dinamicos.stream().filter(t -> t.getDir()==0 && y(t)>=645 && y(t)<=655 && t.getX()<330).toList();
            assertThat(nome.stream().map(PromissoriaVisualTests::y).distinct()).hasSize(2);
            assertThat(nome).allSatisfy(t -> {
                assertThat(t.getFontSizeInPt()).isGreaterThanOrEqualTo(9);
                assertThat(t.getX()+t.getWidth()).isLessThanOrEqualTo(330);
                assertThat(y(t)).isGreaterThan(644); // Above the fixed EMITENTE label.
            });
            var endereco = posicoes.dinamicos.stream().filter(t -> y(t)<620 && t.getDir()==0).toList();
            assertThat(endereco.stream().map(PromissoriaVisualTests::y).distinct()).hasSize(3);
            assertThat(endereco).allSatisfy(t -> {
                assertThat(t.getX()+t.getWidth()).isLessThanOrEqualTo(328.81f);
                assertThat(y(t)).isBetween(594f,614f);
                assertThat(t.getFontSizeInPt()).isEqualTo(8);
            });
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isEqualTo(595.5f);
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isEqualTo(842.25f);
        }
        assertThat(p.emitenteEndereco()).contains(", Nº 101", ", Cep 00100-000");
        assertThat(p.emitenteNome()).isEqualTo("MARINA APARECIDA OLIVEIRA SAMPAIO");
    }
    @Test void enderecoSemComplementoMantemCepEUsaLinhasSeparadasSemRotulosDoContrato() throws Exception {
        try (var doc = Loader.loadPDF(new PromissoriaService().gerar(exemplo("Rua A, Nº 15, Centro, Cep 00100-000, Cidade — SP")))) {
            String texto = new PDFTextStripper().getText(doc);
            assertThat(texto).contains("RUA A - 15 - CENTRO", "00100-000 - CIDADE - SP")
                    .doesNotContain("Nº 15", "CEP 00100-000", "null", "undefined");
        }
    }
    private static float y(TextPosition t) { return t.getTextMatrix().getTranslateY(); }
    private static class Posicoes extends PDFTextStripper {
        final List<TextPosition> dinamicos = new ArrayList<>();
        Posicoes() throws IOException { super(); }
        @Override protected void processTextPosition(TextPosition text) {
            if (text.getFont().getName().startsWith("Helvetica")) dinamicos.add(text);
            super.processTextPosition(text);
        }
    }
}
