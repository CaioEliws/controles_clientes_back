package com.caio.controle_clientes;

import com.caio.controle_clientes.services.contrato.ContratoTemplateService;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class ContratoTemplateTests {
    final ContratoTemplateService service = new ContratoTemplateService();

    @Test void substituiRunsQuebradosEmParagrafosTabelaCabecalhoRodapeETextBoxSemMudarEstilos() throws Exception {
        String p = "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Antes {{NO</w:t>"
                + "</w:r><w:r><w:rPr><w:i/></w:rPr><w:t>ME}} depois {{NOME}}</w:t></w:r></w:p>";
        var bytes = zip(Map.of("word/document.xml", "<w:document><w:body><w:tbl><w:tr><w:tc>"+p+"</w:tc></w:tr></w:tbl>"
                + "<w:p><w:r><w:txbxContent>" + p + "</w:txbxContent></w:r></w:p></w:body></w:document>",
                "word/header1.xml", "<w:hdr>"+p+"</w:hdr>", "word/footer1.xml", "<w:ftr>"+p+"</w:ftr>"));
        byte[] result = service.preencher(bytes, Map.of("NOME", "Ana & <B>"));
        try (var in = new ZipInputStream(new ByteArrayInputStream(result))) {
            while (in.getNextEntry() != null) {
                String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(xml).contains("Ana &amp; &lt;B&gt;").contains("<w:b/>").contains("<w:i/>")
                        .contains("<w:jc w:val=\"center\"/>").doesNotContain("{{").doesNotContain("}}");
            }
        }
    }
    @Test void rejeitaPlaceholdersDesconhecidosOuResiduaisEZipSemDocumento() throws Exception {
        assertThatThrownBy(() -> service.preencher(zip(Map.of("word/document.xml", "<w:p><w:r><w:t>{{SEM_DADO}}</w:t></w:r></w:p>")), Map.of()))
                .hasMessageContaining("placeholder não preenchido");
        assertThatThrownBy(() -> service.preencher(zip(Map.of("word/document.xml", "<w:p><w:r><w:t>{{quebrado</w:t></w:r></w:p>")), Map.of()))
                .hasMessageContaining("placeholder não preenchido");
        assertThatThrownBy(() -> service.preencher(zip(Map.of("word/styles.xml", "styles")), Map.of()))
                .hasMessageContaining("documento principal ausente");
    }
    private byte[] zip(Map<String,String> partes) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var z = new ZipOutputStream(bytes)) {
            for (var e : partes.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey())); z.write(e.getValue().getBytes(StandardCharsets.UTF_8)); z.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
