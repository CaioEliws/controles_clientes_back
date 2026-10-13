package com.caio.controle_clientes;

import com.caio.controle_clientes.services.contrato.*;
import com.caio.controle_clientes.models.EstadoCivil;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class ContratoFormatacaoTests {
    @ParameterizedTest
    @CsvSource({"1,uma", "2,duas", "3,três", "8,oito", "21,vinte e uma", "22,vinte e duas",
            "31,trinta e uma", "32,trinta e duas", "200,duzentas", "202,duzentas e duas",
            "21000,vinte e uma mil", "2000002,dois milhões e duas"})
    void quantidadeDeParcelasConcordaNoFeminino(long quantidade, String esperado) {
        assertThat(NumeroPorExtenso.quantidadeParcelasPorExtenso(quantidade)).isEqualTo(esperado);
    }
    @Test void extensoGenericoEMonetarioPermanecemMasculinos() {
        assertThat(NumeroPorExtenso.inteiro(1)).isEqualTo("um");
        assertThat(NumeroPorExtenso.inteiro(22)).isEqualTo("vinte e dois");
        assertThat(NumeroPorExtenso.inteiro(202)).isEqualTo("duzentos e dois");
        assertThat(NumeroPorExtenso.moeda(new BigDecimal("2.02"))).isEqualTo("dois reais e dois centavos");
    }
    @ParameterizedTest
    @CsvSource({"Vendedora,vendedora", "Professora de Educação Física,professora de educação física",
            "Técnico de TI,técnico de ti", "Analista de Sistemas,analista de sistemas",
            "vendedora,vendedora", "'  Vendedora  ',vendedora",
            "'123 - Técnico de TI','123 - técnico de ti'", "VENDEDORA,vendedora"})
    void profissaoParaContratoConverteTextoInteiroEmMinusculo(String entrada, String esperado) {
        assertThat(ContratoFormatador.formatarProfissaoParaContrato(entrada)).isEqualTo(esperado);
    }
    @Test void profissaoNulaOuVaziaNaoRecebeValorArtificial() {
        assertThat(ContratoFormatador.formatarProfissaoParaContrato(null)).isNull();
        assertThat(ContratoFormatador.formatarProfissaoParaContrato("  ")).isEmpty();
    }
    @ParameterizedTest
    @CsvSource({"00000000002,000.000.000-02", "12345678901,123.456.789-01",
            "000.000.000-02,000.000.000-02"})
    void formataCpfSemPerderZeros(String original, String esperado) {
        assertThat(ContratoFormatador.cpf(original)).isEqualTo(esperado);
    }
    @ParameterizedTest
    @CsvSource({"00100000,00100-000", "12345678,12345-678", "00100-000,00100-000"})
    void formataCepSemPerderZeros(String original, String esperado) {
        assertThat(ContratoFormatador.cep(original)).isEqualTo(esperado);
    }
    @Test void naoRemascaraValoresForaDoFormatoNumericoExato() {
        assertThat(ContratoFormatador.cpf(null)).isNull();
        assertThat(ContratoFormatador.cep(null)).isNull();
        assertThat(ContratoFormatador.cpf("00123-X")).isEqualTo("00123-X");
        assertThat(ContratoFormatador.cep("00100")).isEqualTo("00100");
    }
    @ParameterizedTest
    @CsvSource({"2160.00,dois mil cento e sessenta reais", "362.00,trezentos e sessenta e dois reais",
            "1250.50,mil duzentos e cinquenta reais e cinquenta centavos", "0.00,zero reais", "1.00,um real",
            "1.01,um real e um centavo", "0.50,cinquenta centavos", "100.00,cem reais", "101.00,cento e um reais",
            "1000.00,mil reais", "1000000.00,um milhão de reais", "2000000000.00,dois bilhões de reais",
            "1000000000000.01,um trilhão de reais e um centavo"})
    void converteMoedaSemServicoExterno(String numero, String esperado) {
        assertThat(NumeroPorExtenso.moeda(new BigDecimal(numero))).isEqualTo(esperado);
    }
    @Test void rejeitaValorNegativoPrecisaoExtraEOverflow() {
        for (String numero : new String[]{"-1", "0.001", "1000000000000000"}) {
            assertThatThrownBy(() -> NumeroPorExtenso.moeda(new BigDecimal(numero)))
                    .isInstanceOf(com.caio.controle_clientes.exceptions.RegraNegocioException.class);
        }
    }
    @Test void formataQuantidadeDatasMoedaEEnumsSemInferirGenero() {
        assertThat(NumeroPorExtenso.inteiro(12)).isEqualTo("doze");
        assertThat(NumeroPorExtenso.inteiro(8)).isEqualTo("oito");
        assertThat(ContratoFormatador.dataExtenso(LocalDate.of(2024,6,21))).isEqualTo("21 de junho de 2024");
        assertThat(ContratoFormatador.data(LocalDate.of(2024,6,21))).isEqualTo("21/06/2024");
        assertThat(ContratoFormatador.moeda(new BigDecimal("1250.50"))).isEqualTo("1.250,50");
        assertThat(java.util.Arrays.stream(EstadoCivil.values()).map(ContratoFormatador::estadoCivil))
                .containsExactly("solteiro", "casado", "divorciado", "viúvo", "união estável", "separado");
    }
}
