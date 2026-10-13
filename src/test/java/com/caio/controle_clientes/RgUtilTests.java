package com.caio.controle_clientes;

import com.caio.controle_clientes.validation.RgUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class RgUtilTests {
    @ParameterizedTest
    @CsvSource({"409614713,40.961.471-3", "40.961.471-3,40.961.471-3",
        "52779723X,52.779.723-X", "52779723x,52.779.723-X", "52.779.723,52.779.723",
        "1234567,1.234.567", "12345678,12.345.678", "1234567890,1.234.567.890",
        "12345678901,12.345.678.901", "123456789012,123.456.789.012", "1234567890123,1.234.567.890.123",
        "000000002,00.000.000-2", "0000000,0.000.000", "123456X,123.456X",
        "36956870,36.956.870", "01234567,01.234.567", "001234567,00.123.456-7",
        "64.729.511-8,64.729.511-8", "1.234.567,1.234.567", "36.956.870,36.956.870",
        "1.234.567.890,1.234.567.890", "12.345.678.901,12.345.678.901",
        "123.456.789.012,123.456.789.012", "1.234.567.890.123,1.234.567.890.123",
        "123456789X,123.456.789X", "123456789012X,123.456.789.012X",
        "12/345/678,12/345/678", "52 779 723 x,52.779.723-X"})
    void validaEFormataSemInventarPadraoEstadual(String entrada, String esperado) {
        assertThat(RgUtil.validarEstrutura(entrada)).isTrue();
        for (var uf : new String[]{null, "SSP/SP", "SSP/MG", "SSP/RJ", "SSP/XX", "legado"})
            assertThat(RgUtil.formatar(entrada, uf)).isEqualTo(esperado);
        assertThat(RgUtil.normalizar(esperado)).isEqualTo(RgUtil.normalizar(entrada));
    }
    @ParameterizedTest
    @ValueSource(strings={"123456", "12345678901234", "12A345678", "1234X5678", "1234567!", "1234567XX", "ABC123", "123X456", "1234A567", "XXXXXXX"})
    void rejeitaEstruturaInvalida(String valor) { assertThat(RgUtil.validarEstrutura(valor)).isFalse(); }

    @ParameterizedTest
    @CsvSource(value={"SSP/SP|SP", "ssp/rj|RJ", "SP|SP", "SSP-MG|MG", "SSP/XX|NULL", "legado|NULL", "NULL|NULL"}, delimiter='|', nullValues="NULL")
    void extraiUfSemExigirUmaMascaraEstadual(String entrada, String esperado) {
        assertThat(RgUtil.extrairUf(entrada)).isEqualTo(esperado);
    }

    @org.junit.jupiter.api.Test
    void roundTripDeTodosOsTamanhosComZerosEXFinalSemInventarDv() {
        for (int tamanho = 7; tamanho <= 13; tamanho++) {
            for (String valor : new String[]{"0".repeat(tamanho), "0".repeat(tamanho - 1) + "x"}) {
                assertThat(RgUtil.validar(valor)).isTrue();
                String formatado = RgUtil.formatar(valor);
                assertThat(RgUtil.normalizar(formatado)).isEqualTo(RgUtil.normalizar(valor));
                assertThat(RgUtil.formatar(formatado)).isEqualTo(formatado);
                if (tamanho != 9) assertThat(formatado).doesNotContain("-");
            }
        }
        assertThat(RgUtil.formatar(null)).isNull();
        assertThat(RgUtil.formatar(" ")).isEmpty();
        assertThat(RgUtil.validar(null)).isTrue();
        assertThat(RgUtil.formatar("12A345678")).isEqualTo("12A345678");
    }
}
