package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;
import java.io.*;

@Service
@RequiredArgsConstructor
public class ContratoPdfService {
    private final DocxPdfConversor conversor;
    private final PromissoriaService promissorias;
    public Resultado gerar(byte[] docxHistorico, ContratoSnapshotDTO snapshot) {
        var promissoria=promissorias.montar(snapshot);
        byte[] contratoPdf=conversor.converter(docxHistorico);
        byte[] promissoriaPdf=promissorias.gerar(promissoria);
        try (var contrato=Loader.loadPDF(contratoPdf); var nota=Loader.loadPDF(promissoriaPdf);
                var out=new ByteArrayOutputStream()) {
            if (contrato.getNumberOfPages()<1 || nota.getNumberOfPages()!=1) throw new IOException("PDF sem páginas válidas");
            var merger=new PDFMergerUtility();
            merger.addSource(new RandomAccessReadBuffer(contratoPdf));
            merger.addSource(new RandomAccessReadBuffer(promissoriaPdf));
            merger.setDestinationStream(out); merger.mergeDocuments(null);
            return new Resultado(promissoria,out.toByteArray());
        } catch (IOException e) { throw new RegraNegocioException("Falha ao validar ou unir contrato PDF e promissória."); }
    }
    public record Resultado(PromissoriaSnapshotDTO snapshot, byte[] pdfFinal) {}
}
