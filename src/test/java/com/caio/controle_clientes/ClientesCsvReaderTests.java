package com.caio.controle_clientes;

import com.caio.controle_clientes.services.importClientes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ClientesCsvReaderTests {
    final ClientesCsvReader reader = new ClientesCsvReader();
    MockMultipartFile arquivo(String texto) { return new MockMultipartFile("file", "clientes.csv", "text/csv", texto.getBytes(StandardCharsets.UTF_8)); }
    @Test void aceitaBomAcentosAspasPontoEVirgulaMultilinhaECRLF() {
        var linhas = reader.ler(arquivo("\uFEFFnomeCompleto;cpf;statusExtracao;observacoes\r\n\"João; da Silva\";00000000002;OK;\"União\n\"\"viúva\"\"\"\r\n"));
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).numero()).isEqualTo(2);
        assertThat(linhas.get(0).campos()).containsEntry("nomeCompleto", "João; da Silva").containsEntry("observacoes", "União\n\"viúva\"");
    }
    @ParameterizedTest @ValueSource(strings={"clientes.pdf", "clientes.xlsx", "clientes.txt"})
    void rejeitaExtensaoNaoCsv(String nome) {
        assertThatThrownBy(() -> reader.ler(new MockMultipartFile("file", nome, "text/plain", "texto".getBytes())))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Envie um arquivo CSV válido.");
    }
    @Test void vazioECabecalhoSomenteSaoControlados() {
        assertThatThrownBy(() -> reader.ler(arquivo(""))).hasMessageContaining("CSV vazio");
        assertThat(reader.ler(arquivo("nomeCompleto;cpf;statusExtracao\n\n"))).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"nomeCompleto;statusExtracao", "nomeCompleto;cpf;cpf;statusExtracao", "nomeCompleto;cpf;statusExtracao;", "nomeCompleto,cpf,statusExtracao"})
    void rejeitaCabecalhoMalformado(String cabecalho) {
        assertThatThrownBy(() -> reader.ler(arquivo(cabecalho))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CSV inválido");
    }
    @ParameterizedTest @ValueSource(strings={"\"João;00000000002;OK", "Jo\"ão;00000000002;OK", "\"João\"extra;00000000002;OK"})
    void rejeitaAspasMalformadasAntesDeImportar(String linha) {
        assertThatThrownBy(() -> reader.ler(arquivo("nomeCompleto;cpf;statusExtracao\n" + linha))).hasMessageContaining("aspas malformadas");
    }
    @Test void linhaComColunasErradasNaoImpedeLeituraDasDemais() {
        var linhas = reader.ler(arquivo("nomeCompleto;cpf;statusExtracao\nJoão;00000000002\nMaria;00000000003;OK"));
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).erro()).isNotNull(); assertThat(linhas.get(1).erro()).isNull();
    }
    @Test void rejeitaEncodingInvalidoETamanhoExcessivo() {
        assertThatThrownBy(() -> reader.ler(new MockMultipartFile("file", "a.csv", "text/csv", new byte[]{(byte) 0xC3, 0x28})))
            .hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> reader.ler(new MockMultipartFile("file", "a.csv", "text/csv", new byte[1_000_001])))
            .hasMessageContaining("1 MB");
    }
    @Test void limiteMultipartRetorna400SomenteNoNovoFluxo() {
        var handler = new com.caio.controle_clientes.exceptions.GlobalExceptionHandler();
        var erro = new org.springframework.web.multipart.MaxUploadSizeExceededException(1_000_000);
        var req = new org.springframework.mock.web.MockHttpServletRequest("POST", "/import/clientes");
        assertThat(handler.handleUploadLimit(erro, req).getStatusCode().value()).isEqualTo(400);
        req.setRequestURI("/import");
        assertThat(handler.handleUploadLimit(erro, req).getStatusCode().value()).isEqualTo(500);
    }
}
