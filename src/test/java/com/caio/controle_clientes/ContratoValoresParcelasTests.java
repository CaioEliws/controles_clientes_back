package com.caio.controle_clientes;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.services.contrato.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ContratoValoresParcelasTests {
    static DadosContratoDTO dados(int quantidade) {
        var inicio = LocalDate.of(2026, 11, 7);
        var principal = new BigDecimal(quantidade == 1 ? "2700.00" : "2160.00");
        var parcela = new BigDecimal(quantidade == 1 ? "2970.00" : "362.00");
        var total = parcela.multiply(BigDecimal.valueOf(quantidade));
        var pessoa = new ParteContratoDTO("Pessoa de teste", "brasileiro", EstadoCivil.SOLTEIRO,
                "motorista", "52.779.723", "SSP/SP", "00000000002", "Rua A", 84,
                null, "Centro", "00100000", "Indaiatuba", "SP", SexoDocumento.MASCULINO);
        var e = new EmprestimoContratoDTO(principal, BigDecimal.TEN, parcela, total, quantidade,
                LocalDate.of(2026, 10, 7), inicio, inicio.plusMonths(quantidade - 1),
                FormaPagamento.PIX, FrequenciaPagamento.MENSAL, TipoContrato.FISICO, EmprestimoStatus.EM_ABERTO);
        return new DadosContratoDTO(1L, ContratoSnapshotService.CREDOR, pessoa, pessoa, e,
                IntStream.range(0, quantidade).mapToObj(i -> new ParcelaContratoDTO(i + 1,
                        inicio.plusMonths(i), parcela)).toList());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 8, 9})
    void separaPrincipalDividaEParcelaEPreservaModeloPlural(int quantidade) throws Exception {
        var dados = dados(quantidade);
        var s = new ContratoSnapshotService().montar(dados, LocalDate.of(2026, 10, 8));
        byte[] bytes = new ContratoTemplateService().gerar(s);
        try (var word = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var textos = word.getParagraphs().stream().map(p -> p.getText()).toList();
            String primeira = textos.stream().filter(t -> t.startsWith("CLAUSULA 1ª")).findFirst().orElseThrow();
            assertThat(primeira).contains("R$ " + ContratoFormatador.moeda(dados.emprestimo().valorEmprestado()));
            assertThat(s.camposDocumento()).containsEntry("VALOR_EMPRESTADO", quantidade == 1 ? "2.700,00" : "2.160,00")
                    .containsEntry("VALOR_TOTAL_DIVIDA", ContratoFormatador.moeda(dados.emprestimo().valorTotalEmprestimo()))
                    .containsEntry("VALOR_PARCELA", quantidade == 1 ? "2.970,00" : "362,00");
            String pagamento = textos.stream().filter(t -> t.startsWith("CLAUSULA 3ª")).findFirst().orElseThrow();
            if (quantidade == 1) {
                assertThat(pagamento).endsWith("1 (uma) parcela, no valor de R$ 2.970,00 (dois mil novecentos e setenta reais), com vencimento fixo no dia 07 de novembro de 2026.");
                assertThat(textos.toString()).doesNotContain("Sendo a primeira parcela", "a última parcela", "todo dia");
                assertThat(textos).anyMatch(t -> t.startsWith("§1º.") && t.contains("uma nota promissória") && t.contains("2.700,00"));
                var paragrafo = word.getParagraphs().stream().filter(p -> p.getText().startsWith("CLAUSULA 3ª")).findFirst().orElseThrow();
                assertThat(paragrafo.getRuns()).anyMatch(r -> r.text().contains("07 de novembro de 2026") && "FF0000".equalsIgnoreCase(r.getColor()));
            } else {
                assertThat(pagamento).contains(quantidade + " (", "parcelas,", "todo dia 7 de cada mês.");
                assertThat(textos.toString()).contains("Sendo a primeira parcela a ser pago em 07/11/2026 e a última parcela em "
                        + ContratoFormatador.data(dados.parcelas().get(quantidade - 1).dataVencimento()), "§2º.", "1 (uma) promissória");
            }
            String promissoria = textos.stream().filter(t -> t.contains("promissória")).findFirst().orElseThrow();
            assertThat(promissoria).contains("R$ " + s.camposDocumento().get("VALOR_EMPRESTADO"),
                    s.camposDocumento().get("VALOR_EMPRESTADO_EXTENSO"));
            assertThat(new PromissoriaService().montar(s).valor()).isEqualTo(s.camposDocumento().get("VALOR_EMPRESTADO"));
            assertThat(new PromissoriaService().montar(s).valorExtenso()).isEqualTo(s.camposDocumento().get("VALOR_EMPRESTADO_EXTENSO"));
            var promissorias = new PromissoriaService();
            try (var pdf = org.apache.pdfbox.Loader.loadPDF(promissorias.gerar(promissorias.montar(s)))) {
                String textoPdf = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)
                        .replaceAll("\\s+", " ").toLowerCase(java.util.Locale.forLanguageTag("pt-BR"));
                assertThat(textoPdf).contains("r$ " + s.camposDocumento().get("VALOR_EMPRESTADO"),
                        s.camposDocumento().get("VALOR_EMPRESTADO_EXTENSO"))
                        .doesNotContain("r$ " + s.camposDocumento().get("VALOR_TOTAL_DIVIDA"));
            }
        }
    }
}
