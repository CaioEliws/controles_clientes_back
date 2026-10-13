package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.importClientes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportacaoClientesCsvTests {
    @Autowired ImportacaoClientesCsvService service;
    @Autowired ClienteRepositorio clientes;
    @Autowired PerfilRepositorio perfis;
    @Autowired EmprestimoRepositorio emprestimos;
    @Autowired ParcelaRepositorio parcelas;
    @Autowired ContratoGeradoRepositorio contratos;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ClienteRepositorio spyClientes;
    Perfil perfil() { var p = new Perfil(); p.setNome("CSV " + UUID.randomUUID()); return perfis.saveAndFlush(p); }
    MockMultipartFile arquivo(String csv) { return new MockMultipartFile("file", "clientes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)); }
    ImportacaoClientesResultadoDTO importar(Perfil p, String linhas, boolean previa) {
        return service.importar(p.getId(), arquivo("nomeCompleto;cpf;statusExtracao;profissao;enderecoRua\n" + linhas), previa);
    }
    Cliente cliente(Perfil p, String cpf) { var c = new Cliente(); c.setNome("João"); c.setCpf(cpf); c.setPerfil(p); return clientes.saveAndFlush(c); }
    @Test void previewNaoGravaENovoClienteNaoCriaFinanceiroNemDocumentos() {
        var p = perfil(); long e = emprestimos.count(), pa = parcelas.count(), co = contratos.count();
        var previa = importar(p, "João;00000000002;OK;motorista;Rua A", true);
        assertThat(previa.previa()).isTrue(); assertThat(previa.criados()).isEqualTo(1);
        assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
        var resultado = importar(p, "João;00000000002;OK;motorista;Rua A", false);
        assertThat(resultado.criados()).isEqualTo(1); assertThat(resultado.erros()).isZero();
        var c = clientes.findAllByPerfilId(p.getId()).get(0);
        assertThat(c.getNome()).isEqualTo("João"); assertThat(c.getCpf()).isEqualTo("000.000.000-02");
        assertThat(c.getProfissao()).isEqualTo("motorista");
        assertThat(emprestimos.count()).isEqualTo(e); assertThat(parcelas.count()).isEqualTo(pa); assertThat(contratos.count()).isEqualTo(co);
    }
    @Test void cpfComMascaraDeduplicaPreencheVaziosEMantemConflitos() {
        var p = perfil(); var c = cliente(p, "000.000.000-02"); c.setEnderecoRua("Rua A"); clientes.saveAndFlush(c);
        var preview = importar(p, "João;00000000002;OK;motorista;Rua B", true);
        assertThat(preview.conflitos()).isEqualTo(1); assertThat(clientes.findById(c.getId()).orElseThrow().getProfissao()).isNull();
        var r = importar(p, "João;00000000002;OK;motorista;Rua B", false);
        assertThat(r.criados()).isZero(); assertThat(r.conflitos()).isEqualTo(1); assertThat(r.existentes()).isEqualTo(1);
        assertThat(r.detalhes().get(0).camposConflitantes()).containsExactly("enderecoRua");
        assertThat(r.detalhes().get(0).camposPreenchidos()).containsExactly("profissao");
        assertThat(r.detalhes().get(0).cpf()).isEqualTo("***.***.000-**");
        c = clientes.findById(c.getId()).orElseThrow(); assertThat(c.getEnderecoRua()).isEqualTo("Rua A"); assertThat(c.getProfissao()).isEqualTo("motorista");
        assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(1);
    }
    @Test void vazioNaoApagaEReimportacaoEIdempotente() {
        var p = perfil(); var c = cliente(p, "00000000002"); c.setEnderecoRua("Rua A"); clientes.saveAndFlush(c);
        var r = importar(p, "João;00000000002;OK;motorista;", false);
        assertThat(r.atualizados()).isEqualTo(1); assertThat(clientes.findById(c.getId()).orElseThrow().getEnderecoRua()).isEqualTo("Rua A");
        assertThat(importar(p, "João;00000000002;OK;motorista;", false).ignorados()).isEqualTo(1);
    }
    @Test void primeiroValidoVenceDuplicidadeArquivoECpfAusenteNuncaUsaNome() {
        var p = perfil(); var r = importar(p, "João;;OK;;\nJoão;00000000002;OK;motorista;\nOutro;000.000.000-02;OK;professor;", false);
        assertThat(r.totalLinhas()).isEqualTo(3); assertThat(r.criados()).isEqualTo(1); assertThat(r.ignorados()).isEqualTo(2);
        assertThat(r.detalhes()).extracting(ImportacaoClientesResultadoDTO.Detalhe::status).containsExactly("IGNORADO", "CRIADO", "DUPLICADO_NO_ARQUIVO");
        assertThat(clientes.findAllByPerfilId(p.getId()).get(0).getNome()).isEqualTo("João");
    }
    @Test void primeiroRegistroValidoExistenteSemMudancaTambemBloqueiaDuplicadoPosterior() {
        var p = perfil(); var c = cliente(p, "00000000002");
        var r = importar(p, "João;00000000002;OK;;\nJoão;00000000002;OK;motorista;", false);
        assertThat(r.ignorados()).isEqualTo(2);
        assertThat(r.detalhes()).extracting(ImportacaoClientesResultadoDTO.Detalhe::status)
            .containsExactly("IGNORADO", "DUPLICADO_NO_ARQUIVO");
        assertThat(clientes.findById(c.getId()).orElseThrow().getProfissao()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"REVISAR", "INCOMPLETO", "ERRO_LEITURA", "DUPLICIDADE", "SEM_WORD", "", "DESCONHECIDO"})
    void statusDiferenteDeOkNaoImporta(String status) {
        var p = perfil(); var r = importar(p, "João;00000000002;" + status + ";motorista;Rua A", false);
        assertThat(r.ignorados()).isEqualTo(1); assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"123", "ABC12345678", "123456789012"})
    void cpfInvalidoReportaErroEContinua(String cpf) {
        var p = perfil(); var r = importar(p, "Inválido;" + cpf + ";OK;;\nVálido;00000000002;OK;;", false);
        assertThat(r.erros()).isEqualTo(1); assertThat(r.criados()).isEqualTo(1); assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(1);
    }
    @ParameterizedTest @CsvSource({"solteira,SOLTEIRO", "casada,CASADO", "viúva,VIUVO", "união estável,UNIAO_ESTAVEL", "divorciada,DIVORCIADO", "SEPARADO,SEPARADO"})
    void enumsRgXECepNaoPerdemZeros(String civil, EstadoCivil esperado) {
        var p = perfil(); String csv = "nomeCompleto;cpf;statusExtracao;estadoCivil;sexoDocumento;rg;rgOrgaoExpedidor;enderecoCep;nacionalidade\n"
            + "João;00000000002;OK;" + civil + ";feminino;52779723x;ssp/sp;00100000;brasileira";
        assertThat(service.importar(p.getId(), arquivo(csv), false).criados()).isEqualTo(1);
        var c = clientes.findAllByPerfilId(p.getId()).get(0);
        assertThat(c.getEstadoCivil()).isEqualTo(esperado); assertThat(c.getSexoDocumento()).isEqualTo(SexoDocumento.FEMININO);
        assertThat(c.getRg()).isEqualTo("52.779.723-X"); assertThat(c.getEnderecoCep()).isEqualTo("00100-000"); assertThat(c.getNacionalidade()).isEqualTo("BRASIL");
    }
    @Test void rgOitoDigitosMetadadosENaoInferenciaDeSexo() {
        var p = perfil(); var r = service.importar(p.getId(), arquivo("nomeCompleto;cpf;statusExtracao;rg;pastaOrigem;observacoes\nJoão;00000000002;OK;36956870;Pasta;Revisão"), false);
        assertThat(r.criados()).isEqualTo(1); var c = clientes.findAllByPerfilId(p.getId()).get(0);
        assertThat(c.getRg()).isEqualTo("36.956.870"); assertThat(c.getSexoDocumento()).isNull();
    }
    @Test void perfisIsoladosPermitemMesmoCpfMasNomeIgualComCpfDiferenteBloqueiaIdentidade() {
        var a = perfil(); var b = perfil(); cliente(a, "00000000002");
        var resultado = importar(b, "João;00000000002;OK;;\nJoão;00000000003;OK;;", false);
        assertThat(resultado.criados()).isEqualTo(1); assertThat(resultado.conflitosIdentidade()).isEqualTo(1);
        assertThat(clientes.findAllByPerfilId(a.getId())).hasSize(1); assertThat(clientes.findAllByPerfilId(b.getId())).hasSize(1);
    }
    @Test void duplicidadeLegadaNoBancoNaoEscolheClienteArbitrario() {
        var p = perfil(); cliente(p, "00000000002"); cliente(p, "000.000.000-02");
        var r = importar(p, "João;00000000002;OK;motorista;", false);
        assertThat(r.ambiguos()).isEqualTo(1); assertThat(r.bloqueados()).isEqualTo(1); assertThat(clientes.findAllByPerfilId(p.getId())).allMatch(c -> c.getProfissao() == null);
    }
    @Test void falhaDePersistenciaReverteSomenteLinhaAtual() {
        var p = perfil(); var primeira = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocacao -> {
            Cliente salvo = spyClientes.save(invocacao.getArgument(0)); spyClientes.flush();
            if (primeira.getAndSet(false)) throw new org.springframework.dao.DataIntegrityViolationException("simulação");
            return salvo;
        }).when(spyClientes).saveAndFlush(org.mockito.ArgumentMatchers.any(Cliente.class));
        try {
            var r = importar(p, "Primeiro;00000000002;OK;;\nSegundo;00000000003;OK;;", false);
            assertThat(r.erros()).isEqualTo(1); assertThat(r.criados()).isEqualTo(1);
            assertThat(clientes.findAllByPerfilId(p.getId())).extracting(Cliente::getNome).containsExactly("Segundo");
        } finally { org.mockito.Mockito.reset(spyClientes); }
    }
    @Test void limitaDetalhesSemPerderTotais() {
        var p = perfil(); StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 505; i++) csv.append("João;;REVISAR;;\n");
        var r = importar(p, csv.toString(), false);
        assertThat(r.totalLinhas()).isEqualTo(505); assertThat(r.ignorados()).isEqualTo(505);
        assertThat(r.detalhes()).hasSize(500); assertThat(r.detalhesTruncados()).isTrue();
    }
    @ParameterizedTest
    @CsvSource(value={"rg|12X34567", "estadoCivil|DESCONHECIDO", "sexoDocumento|OUTRO", "rgOrgaoExpedidor|SSP/XX",
        "enderecoEstado|XX", "enderecoCep|1234", "enderecoNumero|casa", "enderecoNumero|2147483648"}, delimiter='|')
    void campoInvalidoNaoCriaClienteNemInterrompeArquivo(String campo, String valor) {
        var p = perfil();
        var csv = arquivo("nomeCompleto;cpf;statusExtracao;" + campo + "\nJoão;00000000002;OK;" + valor + "\nMaria;00000000003;OK;");
        var r = service.importar(p.getId(), csv, false);
        assertThat(r.erros()).isEqualTo(1); assertThat(r.criados()).isEqualTo(1);
        assertThat(clientes.findAllByPerfilId(p.getId())).extracting(Cliente::getNome).containsExactly("Maria");
    }
    @Test void nomeAusenteIgnoraNovoESomenteAtualizaVaziosDoExistente() {
        var p = perfil(); var c = cliente(p, "00000000002");
        var r = importar(p, ";00000000002;OK;motorista;\n;00000000003;OK;professor;", false);
        assertThat(r.atualizados()).isEqualTo(1); assertThat(r.ignorados()).isEqualTo(1);
        assertThat(clientes.findById(c.getId()).orElseThrow().getNome()).isEqualTo("João");
    }
    @Test void milClientesSaoImportadosSemDuplicarNaRepeticao() {
        var p = perfil(); var csv = new StringBuilder();
        for (int i = 0; i < 1000; i++) csv.append("Cliente ").append(i).append(';').append(String.format(java.util.Locale.ROOT, "%011d", i)).append(";OK;;\n");
        var resultado = importar(p, csv.toString(), false);
        assertThat(resultado.criados()).isEqualTo(1000); assertThat(resultado.erros()).isZero();
        assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(1000);
        assertThat(importar(p, csv.toString(), true).criados()).isZero();
    }
    @Test void duasImportacoesConcorrentesDoMesmoCpfNaoDuplicam() throws Exception {
        var p = perfil(); var inicio = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<ImportacaoClientesResultadoDTO> acao = () -> {
                inicio.await(); return importar(p, "João;00000000002;OK;motorista;", false);
            };
            var a = executor.submit(acao); var b = executor.submit(acao); inicio.countDown();
            assertThat(a.get(20, java.util.concurrent.TimeUnit.SECONDS).criados() + b.get(20, java.util.concurrent.TimeUnit.SECONDS).criados()).isEqualTo(1);
            assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void revisarValidoExigePreviewSemGravarEConfirmaComAviso() {
        var p = perfil(); var csv = arquivo("nomeCompleto;cpf;statusExtracao;rg;nacionalidade;estadoCivil;profissao;enderecoRua;enderecoNumero;enderecoCidade;enderecoEstado;sexoDocumento;rgOrgaoExpedidor;observacoes\n"
            + "Cliente Revisar;00000000002;REVISAR;8610487;brasileiro;casado;motorista;Rua A;1;Indaiatuba;SP;MASCULINO;SDP/PE;Trecho de endereço não reconhecido: Residencial Araçá");
        var previa = service.importar(p.getId(), csv, true);
        assertThat(previa.importaveisComAviso()).isEqualTo(1); assertThat(previa.bloqueados()).isZero();
        assertThat(previa.detalhes().get(0).classificacao()).isEqualTo("IMPORTAVEL_COM_AVISO");
        assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
        var resultado = service.importar(p.getId(), csv, false);
        assertThat(resultado.importadosComAviso()).isEqualTo(1);
        assertThat(clientes.findAllByPerfilId(p.getId()).get(0).getRgOrgaoExpedidor()).isEqualTo("SDP/PE");
    }
    @Test void amasiadoSemDecisaoDeNegocioPermaneceParaRevisao() {
        var p = perfil(); var r = service.importar(p.getId(), arquivo("nomeCompleto;cpf;statusExtracao;estadoCivil\nCliente;00000000002;OK;amasiado"), false);
        assertThat(r.erros()).isEqualTo(1); assertThat(r.bloqueados()).isEqualTo(1); assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
    }
    @Test void cpfAlteradoDepoisDoIndiceNaoAtualizaClienteErrado() {
        var p = perfil(); var anterior = cliente(p, "00000000002");
        org.mockito.Mockito.doAnswer(invocacao -> {
            var cliente = spyClientes.findAllByPerfilId(p.getId()).get(0);
            cliente.setCpf("00000000003"); spyClientes.saveAndFlush(cliente);
            return java.util.Optional.of(cliente);
        }).when(spyClientes).findByIdAndPerfilId(anterior.getId(), p.getId());
        try {
            var r = importar(p, "Novo CPF;00000000002;OK;motorista;Rua Nova", false);
            assertThat(r.criados()).isEqualTo(1); assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(2);
            var antigo = clientes.findAllByPerfilId(p.getId()).stream().filter(c -> c.getId().equals(anterior.getId())).findFirst().orElseThrow();
            assertThat(antigo.getNome()).isEqualTo("João"); assertThat(antigo.getProfissao()).isNull();
        } finally { org.mockito.Mockito.reset(spyClientes); }
    }
}
