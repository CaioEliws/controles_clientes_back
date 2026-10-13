package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.exceptions.RegraNegocioException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;

/** Acrescenta somente imagens às linhas identificadas do PDF já produzido. */
@Service
public class AssinaturasPdfService {
    public byte[] gerar(byte[] pdfNormal) {
        try (var doc = Loader.loadPDF(pdfNormal.clone()); var out = new ByteArrayOutputStream()) {
            var leitor = new Localizador(); leitor.setSortByPosition(true); leitor.getText(doc);
            var edson = localizar(leitor.linhas, "CREDOR:", "Edson Antonio Vieira");
            var ivani = localizar(leitor.linhas, "TESTEMUNHA:", "Ivani Elias Machado Vieira");
            // Caixas individuais mantêm o tamanho relativo dos manuscritos da folha limpa.
            for (var alvo : List.of(new Alvo(edson, "edson", 155), new Alvo(ivani, "ivani", 206))) {
                var area = alvo.area(); var page = doc.getPage(area.linha().pagina() - 1);
                if (page.getRotation() != 0) throw new IOException("Página de assinatura rotacionada");
                byte[] png;
                try (var in = getClass().getResourceAsStream("/assinaturas/" + alvo.asset() + ".png")) {
                    if (in == null) throw new IOException("Asset de assinatura ausente");
                    png = in.readAllBytes();
                }
                var imagem = PDImageXObject.createFromByteArray(doc, png, alvo.asset());
                imagem.setInterpolate(true); // Suavização no leitor, sem reduzir ou recomprimir o PNG.
                // A base fica 2 pt abaixo da baseline dos underscores; reservar
                // 0,5 pt após a baseline do label evita reduzir a imagem por
                // uma margem extra de 6,5 pt que não existe no espaço visível.
                float alturaDisponivel = area.linha().y() - area.label().y() + 1.5f;
                float largura = Math.min(area.linha().largura() * .92f, alvo.larguraPreferida());
                float escala = Math.min(largura / imagem.getWidth(), alturaDisponivel / imagem.getHeight());
                if (escala <= 0) throw new IOException("Área de assinatura insuficiente");
                float w = imagem.getWidth() * escala, h = imagem.getHeight() * escala;
                float x = page.getCropBox().getLowerLeftX() + area.linha().x() + (area.linha().largura()-w)/2;
                float y = page.getCropBox().getUpperRightY() - area.linha().y() - 2;
                // PDFs convertidos podem compartilhar o dicionário de recursos entre páginas.
                var recursos = new org.apache.pdfbox.cos.COSDictionary(page.getResources().getCOSObject());
                var xobjects = recursos.getCOSDictionary(org.apache.pdfbox.cos.COSName.XOBJECT);
                if (xobjects != null) recursos.setItem(org.apache.pdfbox.cos.COSName.XOBJECT,
                    new org.apache.pdfbox.cos.COSDictionary(xobjects));
                page.setResources(new org.apache.pdfbox.pdmodel.PDResources(recursos));
                try (var stream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.drawImage(imagem, x, y, w, h);
                }
            }
            doc.save(out); return out.toByteArray();
        } catch (IOException e) {
            throw new RegraNegocioException("Não foi possível posicionar as assinaturas fixas no PDF do contrato.");
        }
    }
    private Area localizar(List<Linha> linhas, String label, String nome) throws IOException {
        var encontradas = new ArrayList<Area>();
        for (int i=0; i<linhas.size(); i++) {
            var atual=linhas.get(i);
            if (!atual.texto().strip().equals(label)) continue;
            if (i+2 >= linhas.size()) continue;
            var linha=linhas.get(i+1); var impresso=linhas.get(i+2);
            if (linha.pagina()==atual.pagina() && impresso.pagina()==atual.pagina()
                    && linha.texto().strip().matches("_{5,}") && impresso.texto().strip().equals(nome))
                encontradas.add(new Area(atual,linha));
        }
        if (encontradas.size()!=1) throw new IOException("Área de assinatura ausente ou ambígua");
        return encontradas.get(0);
    }
    private record Linha(int pagina,String texto,float x,float y,float largura) {}
    private record Area(Linha label,Linha linha) {}
    private record Alvo(Area area,String asset,float larguraPreferida) {}
    private static final class Localizador extends PDFTextStripper {
        final List<Linha> linhas = new ArrayList<>();
        Localizador() throws IOException { super(); }
        @Override protected void writeString(String texto,List<TextPosition> posicoes) {
            if (posicoes.isEmpty() || texto.isBlank()) return;
            float x=posicoes.stream().map(TextPosition::getXDirAdj).min(Float::compare).orElseThrow();
            float fim=posicoes.stream().map(p->p.getXDirAdj()+p.getWidthDirAdj()).max(Float::compare).orElseThrow();
            linhas.add(new Linha(getCurrentPageNo(),texto,x,posicoes.get(0).getYDirAdj(),fim-x));
        }
    }
}
