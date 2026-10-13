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

class ContratoParcelasProfissaoTests {
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void geraWordRealComConcordanciaEProfissoesSemAlterarDadosOriginais(int quantidade) throws Exception {
        var devedor = pessoa("Devedora", "Professora de Educação Física");
        var fiador = pessoa("Fiadora", "Técnico de TI");
        var inicio = LocalDate.of(2026, 11, 5);
        var emprestimo = new EmprestimoContratoDTO(new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.valueOf(100L * quantidade), quantidade,
                LocalDate.of(2026, 10, 5), inicio, inicio.plusMonths(quantidade - 1),
                FormaPagamento.PIX, FrequenciaPagamento.MENSAL, TipoContrato.FISICO, EmprestimoStatus.EM_ABERTO);
        var parcelas = IntStream.range(0, quantidade).mapToObj(i -> new ParcelaContratoDTO(
                i + 1, inicio.plusMonths(i), new BigDecimal("100"))).toList();
        var dados = new DadosContratoDTO(1L, ContratoSnapshotService.CREDOR, devedor, fiador, emprestimo, parcelas);
        var snapshot = new ContratoSnapshotService().montar(dados, LocalDate.of(2026, 10, 7));
        assertThat(snapshot.camposDocumento()).containsEntry("DEVEDOR_PROFISSAO", "professora de educação física")
                .containsEntry("FIADOR_PROFISSAO", "técnico de ti");
        assertThat(snapshot.devedor()).isEqualTo(devedor);
        assertThat(snapshot.fiador()).isEqualTo(fiador);
        assertThat(snapshot.devedor().profissao()).isEqualTo("Professora de Educação Física");
        assertThat(snapshot.fiador().profissao()).isEqualTo("Técnico de TI");
        byte[] word = new ContratoTemplateService().gerar(snapshot);
        try (var doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            var textos = doc.getParagraphs().stream().map(p -> p.getText()).toList();
            String clausula = textos.stream().filter(p -> p.contains("CLAUSULA 3ª")).findFirst().orElseThrow();
            assertThat(clausula).contains(quantidade == 1 ? "1 (uma) parcela," : "2 (duas) parcelas,");
            assertThat(textos.toString()).contains("profissão: professora de educação física", "profissão: técnico de ti")
                    .doesNotContain("{{", "profissão: Professora de Educação Física", "profissão: técnico de TI");
        }
    }
    private ParteContratoDTO pessoa(String nome, String profissao) {
        return new ParteContratoDTO(nome, "brasileira", EstadoCivil.CASADO, profissao, "52.779.723-X",
                "SSP/SP", "00000000002", "Rua A", 10, null, "Centro", "00100000", "Indaiatuba", "SP",
                SexoDocumento.FEMININO);
    }
}
