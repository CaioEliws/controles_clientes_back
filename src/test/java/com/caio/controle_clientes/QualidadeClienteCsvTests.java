package com.caio.controle_clientes;
import com.caio.controle_clientes.services.importClientes.QualidadeClienteCsv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class QualidadeClienteCsvTests {
    final QualidadeClienteCsv qualidade = new QualidadeClienteCsv();
    static Map<String, String> completo(String status) {
        var csv = new HashMap<String, String>();
        for (String campo : List.of("nomeCompleto", "cpf", "rg", "nacionalidade", "estadoCivil", "profissao", "enderecoRua", "enderecoNumero", "enderecoCidade", "enderecoEstado", "sexoDocumento")) csv.put(campo, "preenchido");
        csv.put("statusExtracao", status); return csv;
    }
    @Test void revisarCompletoComDivergenciaHistoricaEImportavelComAviso() {
        var csv = completo("REVISAR"); csv.put("observacoes", "Trecho de endereço não reconhecido: Residencial Araçá | Divergências: enderecoRua; utilizados dados mais recentes por campo/bloco de endereço.");
        var r = qualidade.avaliar(csv, 1);
        assertThat(r.elegivel()).isTrue(); assertThat(r.aviso()).isTrue(); assertThat(r.classificacao()).isEqualTo("IMPORTAVEL_COM_AVISO");
        assertThat(r.motivo()).contains("Residencial Araçá", "histórica");
    }
    @ParameterizedTest @ValueSource(strings={"estadoCivil", "nacionalidade", "profissao", "enderecoRua", "enderecoNumero", "enderecoCidade", "enderecoEstado", "sexoDocumento", "cpf", "nomeCompleto"})
    void revisarSemObrigatorioNaoImporta(String campo) {
        var csv = completo("REVISAR"); csv.put(campo, ""); var r = qualidade.avaliar(csv, 1);
        assertThat(r.elegivel()).isFalse(); assertThat(r.camposAusentes()).contains(campo); assertThat(r.motivo()).contains(campo);
    }
    @ParameterizedTest @ValueSource(strings={"INCOMPLETO", "SEM_WORD", "ERRO_LEITURA", "DESCONHECIDO"})
    void bloqueiosDoExtratorPermanecem(String status) { assertThat(qualidade.avaliar(completo(status), 1).elegivel()).isFalse(); }
    @Test void duplicidadeConsolidadaComRegistroFinalUnicoEImportavel() {
        var csv = completo("DUPLICIDADE"); csv.put("observacoes", "Documentos considerados: pasta1/a.docx; pasta2/b.docx | Divergências: enderecoRua; utilizados dados mais recentes por campo/bloco de endereço.");
        assertThat(qualidade.avaliar(csv, 1).classificacao()).isEqualTo("IMPORTAVEL_COM_AVISO");
        assertThat(qualidade.avaliar(csv, 2).classificacao()).isEqualTo("BLOQUEADO_DUPLICIDADE");
    }
    @ParameterizedTest @ValueSource(strings={"Mesmo CPF em múltiplas pastas.", "Dados não consolidados; sem decisão.", "Dados consolidados; conflito irresolvido.", "Documentos considerados: a;b | Divergências: nomeCompleto; sem decisão clara."})
    void duplicidadeSemConsolidacaoSeguraPermaneceBloqueada(String obs) {
        var csv = completo("DUPLICIDADE"); csv.put("observacoes", obs);
        assertThat(qualidade.avaliar(csv, 1).classificacao()).isEqualTo("BLOQUEADO_DUPLICIDADE");
    }
    @Test void resumoNaoDevolveQualificacaoOriginalOuCaminhos() {
        String obs = "Qualificação original: CPF 000.000.000-02; RG 52.779.723-X; Rua A | Fonte de nomeCompleto: C:\\privado\\a.docx | Campos ausentes: estadoCivil | Identidade encontrada em múltiplas pastas: C:\\privado\\pasta";
        assertThat(qualidade.resumir(obs)).contains("estadoCivil", "múltiplas pastas").doesNotContain("000.000", "52.779", "Rua A", "C:");
    }
}
