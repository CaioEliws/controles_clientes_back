package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.services.contrato.ContratoFormatador;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class PadronizacaoFormatadorTests {
    @ParameterizedTest
    @CsvSource({"MASCULINO,SOLTEIRO,solteiro", "FEMININO,SOLTEIRO,solteira",
            "MASCULINO,CASADO,casado", "FEMININO,CASADO,casada",
            "MASCULINO,DIVORCIADO,divorciado", "FEMININO,DIVORCIADO,divorciada",
            "MASCULINO,VIUVO,viúvo", "FEMININO,VIUVO,viúva",
            "MASCULINO,SEPARADO,separado", "FEMININO,SEPARADO,separada",
            "MASCULINO,UNIAO_ESTAVEL,união estável", "FEMININO,UNIAO_ESTAVEL,união estável"})
    void flexionaTodosOsEstadosSemDuplicarEnums(SexoDocumento sexo, EstadoCivil estado, String esperado) {
        assertThat(ContratoFormatador.estadoCivil(estado, sexo)).isEqualTo(esperado);
    }
    @ParameterizedTest
    @CsvSource({"IRENE APARECIDA ARAÚJO DA SILVA,Irene Aparecida Araújo da Silva",
            "irene aparecida araújo da silva,Irene Aparecida Araújo da Silva",
            "JOÃO DA SILVA,João da Silva", "MARIA DE SOUZA,Maria de Souza",
            "CARLOS DOS SANTOS,Carlos dos Santos", "ANA DAS GRAÇAS,Ana das Graças",
            "ÁLVARO DO Ó E SÁ,Álvaro do Ó e Sá", "D'Ávila McDonald,D'Ávila McDonald",
            "  João    da Silva  ,João da Silva"})
    void nomesMantemAcentosLigacoesEExcecoes(String entrada, String esperado) {
        assertThat(ContratoFormatador.nome(entrada)).isEqualTo(esperado);
    }
    @ParameterizedTest
    @CsvSource({"409614713,40.961.471-3", "52779723X,52.779.723-X", "52779723x,52.779.723-X",
            "40.961.471-3,40.961.471-3", "00.123/ab-9,00.123/AB-9", "00123-X,00123-X",
            "00000000x,00.000.000-X", "12345678,12.345.678", "1234567890,1.234.567.890", "52 779 723 x,52.779.723-X"})
    void rgDelegaAoFormatadorCentralSemAlterarCaracteres(String entrada, String esperado) {
        assertThat(ContratoFormatador.rg(entrada)).isEqualTo(esperado);
    }
    @ParameterizedTest
    @CsvSource({"SP,SSP/SP", "ssp/sp,SSP/SP", "SSP-sp,SSP/SP", "Ssp Sp,SSP/SP",
            "mg,SSP/MG", "SSP,SSP", "ssp,SSP", "SSP/XX,SSP/XX"})
    void orgaoNormalizaSemInventarUf(String entrada, String esperado) {
        assertThat(ContratoFormatador.orgaoExpedidor(entrada)).isEqualTo(esperado);
    }
    @Test void enderecoProfissaoCpfCepENacionalidade() {
        assertThat(ContratoFormatador.cidade("SÃO JOSÉ DOS CAMPOS")).isEqualTo("São José dos Campos");
        assertThat(ContratoFormatador.bairro("JARDIM DOS COLIBRIS")).isEqualTo("Jardim dos Colibris");
        assertThat(ContratoFormatador.logradouro("RUA ALMIR ANTÔNIO ARTONI")).isEqualTo("Rua Almir Antônio Artoni");
        assertThat(ContratoFormatador.profissao("VENDEDORA")).isEqualTo("Vendedora");
        assertThat(ContratoFormatador.uf(" sp ")).isEqualTo("SP");
        assertThat(ContratoFormatador.cpf("40886987890")).isEqualTo("408.869.878-90");
        assertThat(ContratoFormatador.cpf("00000000002")).isEqualTo("000.000.000-02");
        assertThat(ContratoFormatador.cep("13349152")).isEqualTo("13349-152");
        assertThat(ContratoFormatador.cep("00100000")).isEqualTo("00100-000");
        for (String nacionalidade : new String[]{"BRASIL", "Brasileira", "brasileiro"}) {
            assertThat(ContratoFormatador.nacionalidade(nacionalidade, SexoDocumento.FEMININO)).isEqualTo("brasileira");
            assertThat(ContratoFormatador.nacionalidade(nacionalidade, SexoDocumento.MASCULINO)).isEqualTo("brasileiro");
        }
        assertThat(ContratoFormatador.nacionalidade("Portuguesa", SexoDocumento.MASCULINO)).isEqualTo("portuguesa");
        assertThat(UfBrasil.values()).hasSize(27);
    }
}
