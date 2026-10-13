package com.caio.controle_clientes;

import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.services.contrato.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ContratoRgTests {
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoService emprestimos;
    @Autowired ContratoService dados;
    @Autowired ContratoSnapshotService snapshots;
    @Autowired ContratoTemplateService template;
    @org.junit.jupiter.api.Test
    void orgaoDiferenteDeSspMantemSiglaEUfNoDocumento() throws Exception {
        var f = ContratoDocxFixture.criar(perfis, clientes, emprestimos);
        f.devedor().setRg("86.104.87"); f.devedor().setRgOrgaoExpedidor("SDP/PE");
        var s = snapshots.montar(dados.buscarDadosContratoModeloFixo(f.perfil().getId(), f.devedor().getId(),
            f.emprestimo().getId(), ContratoSnapshotService.CREDOR), LocalDate.of(2026, 10, 13));
        assertThat(s.camposDocumento()).containsEntry("DEVEDOR_RG_ORGAO", "SDP/PE").containsEntry("DEVEDOR_RG", "86.104.87");
        try (var doc = new XWPFDocument(new ByteArrayInputStream(template.gerar(s)))) {
            assertThat(doc.getParagraphs().stream().map(p -> p.getText()).collect(java.util.stream.Collectors.joining(" "))).contains("SDP/PE");
        }
    }

    @ParameterizedTest
    @CsvSource({"409614713,40.961.471-3", "52.779.723,52.779.723", "1234567,1.234.567",
        "1234567890123,1.234.567.890.123", "52779723x,52.779.723-X",
        "36956870,36.956.870", "01234567,01.234.567", "1234567890,1.234.567.890",
        "12345678901,12.345.678.901", "123456789012,123.456.789.012", "123456X,123.456X",
        "1.234.567.890.123,1.234.567.890.123"})
    void docxUsaRgSeguroDoSnapshotSemReescreverSnapshotAnterior(String entrada, String esperado) throws Exception {
        var f = ContratoDocxFixture.criar(perfis, clientes, emprestimos);
        f.devedor().setRg(entrada); f.fiador().setRg(entrada);
        var snapshot = snapshots.montar(dados.buscarDadosContratoModeloFixo(f.perfil().getId(), f.devedor().getId(),
            f.emprestimo().getId(), ContratoSnapshotService.CREDOR), LocalDate.of(2026, 10, 6));
        assertThat(snapshot.camposDocumento()).containsEntry("DEVEDOR_RG", esperado).containsEntry("FIADOR_RG", esperado);
        byte[] bytes = template.gerar(snapshot);
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String texto = doc.getParagraphs().stream().map(p -> p.getText()).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(texto).contains(esperado).contains("Edson Antonio Vieira").contains("Ivani Elias Machado Vieira");
        }
        f.devedor().setRg("12345678");
        assertThat(snapshot.camposDocumento().get("DEVEDOR_RG")).isEqualTo(esperado);
        assertThat(template.gerar(snapshot)).isEqualTo(bytes);
    }
}
