package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PromissoriaService {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    public PromissoriaSnapshotDTO montar(ContratoSnapshotDTO s) {
        if (s.dataContrato() == null || s.devedor() == null || s.fiador() == null) erro();
        for (Object campo : Arrays.asList(s.devedor().nomeCompleto(), s.devedor().cpf(),
                s.fiador().nomeCompleto(), s.fiador().cpf(), s.devedor().enderecoRua(),
                s.devedor().enderecoNumero(), s.devedor().enderecoBairro(), s.devedor().enderecoCep(),
                s.devedor().enderecoCidade(), s.devedor().enderecoEstado())) {
            if (campo == null || campo instanceof String t && t.isBlank()) erro();
        }
        var p = new PromissoriaSnapshotDTO("PROMISSORIA_V1", s.fiador().nomeCompleto(),
                ContratoFormatador.cpf(s.fiador().cpf()), s.devedor().nomeCompleto(),
                ContratoFormatador.cpf(s.devedor().cpf()), ContratoFormatador.endereco(s.devedor()),
                s.camposDocumento().get("VALOR_PROMISSORIA"), s.camposDocumento().get("VALOR_PROMISSORIA_EXTENSO"),
                s.dataContrato(), s.dataContrato().plusMonths(1).plusDays(5),
                "EDSON ANTONIO VIEIRA", "072.848.058-11", "INDAIATUBA/SP");
        for (String texto : Arrays.asList(p.avalistaNome(), p.avalistaCpf(), p.emitenteNome(), p.emitenteCpf(),
                p.emitenteEndereco(), p.valor(), p.valorExtenso())) {
            if (texto == null || texto.isBlank()) erro();
        }
        return p;
    }
    public byte[] gerar(PromissoriaSnapshotDTO p) {
        try (InputStream in = getClass().getResourceAsStream("/templates/promissoria-v1.pdf")) {
            if (in == null) throw new IOException("Template ausente");
            try (PDDocument doc = Loader.loadPDF(in.readAllBytes()); var out = new ByteArrayOutputStream()) {
                var page = doc.getPage(0);
                var normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                try (var c = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // Coordinates in points, derived from the original PDF (595.5 x 842.25).
                    texto(c, normal, p.vencimentoPromissoria().format(DateTimeFormatter.ofPattern("dd")), 361.39f,797.19f,26,10);
                    texto(c, normal, p.vencimentoPromissoria().format(DateTimeFormatter.ofPattern("MMMM", PT_BR)),423.75f,797.19f,65,10);
                    texto(c, normal, Integer.toString(p.vencimentoPromissoria().getYear()),510.36f,797.19f,40,10);
                    centralizado(c, bold, "R$ " + p.valor(), 472.39f,774.17f,136,14);
                    texto(c, normal, ContratoFormatador.dataExtenso(p.vencimentoPromissoria()),158.73f,752.84f,385,10);
                    linhas(c, normal, inicialMaiuscula(p.valorExtenso()),130.38f,684.22f,410,22,10,2,11);
                    String nome = p.emitenteNome().toUpperCase(PT_BR);
                    if (largura(normal, nome) * 10 / 1000 <= 184)
                        texto(c, normal, nome,130.38f,648.87f,184,10);
                    else
                        // Two baselines above the fixed EMITENTE label; the source clips its second line.
                        linhas(c, normal, quebrarNome(normal,nome),130.38f,654.54f,199.26f,19,9,2,9.54f);
                    texto(c, normal, p.emitenteCpf(),213.06f,626.2f,102,10);
                    linhas(c, normal, enderecoVisual(p.emitenteEndereco()),130.16f,613.14f,199.26f,24,8,3,9.54f);
                    texto(c, normal, ContratoFormatador.data(p.dataEmissao()),410.1f,654.54f,90,10);
                    c.saveGraphicsState();
                    c.transform(Matrix.getRotateInstance(Math.PI / 2, 60.71f,702.85f));
                    centralizado(c, normal, p.avalistaNome().toUpperCase(PT_BR) + ", CPF Nº",0,3.23f,188,10);
                    centralizado(c, normal, p.avalistaCpf(),0,-8.23f,188,10);
                    c.restoreGraphicsState();
                }
                doc.getDocumentInformation().setTitle("Nota promissória");
                doc.save(out);
                return out.toByteArray();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new RegraNegocioException("Não foi possível preencher a promissória com legibilidade: " + e.getMessage());
        }
    }
    private static String inicialMaiuscula(String s) { return s.substring(0,1).toUpperCase(PT_BR) + s.substring(1); }
    private static String quebrarNome(PDFont font, String nome) {
        String[] palavras = nome.split("\\s+");
        String linha = palavras[0];
        for (int i=1;i<palavras.length;i++) {
            if (largura(font,linha + " " + palavras[i])*10/1000 > 184)
                return linha + "\n" + String.join(" ", Arrays.copyOfRange(palavras,i,palavras.length));
            linha += " " + palavras[i];
        }
        return nome;
    }
    private static String enderecoVisual(String endereco) {
        // Presentation only: the persisted snapshot retains the original contract address.
        String[] partes = endereco.split(", Cep ", 2);
        String rua = partes[0].replace(", Nº ", " - ").replace(", ", " - ");
        String restante = partes.length == 2 ? "\n" + partes[1].replace(", ", " - ").replace(" — ", " - ") : "";
        return (rua + restante).toUpperCase(PT_BR);
    }
    private static void centralizado(PDPageContentStream c, PDFont font, String value, float centro,float y,float width,float max) throws IOException {
        float size = Math.min(max, width * 1000 / font.getStringWidth(value));
        texto(c,font,value,centro-font.getStringWidth(value)*size/2000,y,width,size);
    }
    private static void texto(PDPageContentStream c, PDFont font, String value, float x,float y,float width,float max) throws IOException {
        float size = Math.min(max, width * 1000 / font.getStringWidth(value));
        if (size < 7) throw new RegraNegocioException("Texto da promissória excede a área disponível com fonte legível.");
        c.beginText(); c.setFont(font,size); c.newLineAtOffset(x,y); c.showText(value); c.endText();
    }
    private static void linhas(PDPageContentStream c, PDFont font, String value, float x,float y,float width,float height,float max,int count,float leading) throws IOException {
        for (float size=max; size>=7; size-=0.5f) {
            List<String> lines = new ArrayList<>(); String line="";
            for (String paragraph : value.split("\n")) {
            for (String word : paragraph.split("\\s+")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(next)*size/1000 > width && !line.isEmpty()) { lines.add(line); line=word; }
                else line=next;
            }
            if (!line.isEmpty()) lines.add(line);
            line="";
            }
            float tamanho = size;
            boolean fit = lines.size()<=count && lines.size()*size<=height
                    && lines.stream().allMatch(t -> largura(font,t)*tamanho/1000 <= width);
            if (fit) {
                for (int i=0;i<lines.size();i++) texto(c,font,lines.get(i),x,y-i*leading,width,size);
                return;
            }
        }
        throw new RegraNegocioException("Nome ou endereço da promissória excede a área disponível com fonte legível.");
    }
    private static float largura(PDFont font, String text) {
        try { return font.getStringWidth(text); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static void erro() { throw new RegraNegocioException("Snapshot incompleto para a promissória."); }
}
