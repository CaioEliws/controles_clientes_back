package com.caio.controle_clientes;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.RelatorioService;
import com.caio.controle_clientes.services.contrato.ContratoService;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.services.parcela.ParcelaService;
import com.caio.controle_clientes.services.parcela.ParcelaObservacaoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ContratoIntegrationTests {
    @Autowired ContratoService contratos;
    @Autowired EmprestimoService emprestimos;
    @Autowired RelatorioService relatorios;
    @Autowired ParcelaService parcelaService;
    @Autowired ParcelaObservacaoService observacoes;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoRepositorio repository;
    @Autowired ParcelaRepositorio parcelas;
    @Autowired EntityManager em;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void schemaMantemFiadorNullableComChaveEstrangeiraParaCliente() {
        assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'emprestimos' AND LOWER(COLUMN_NAME) = 'fiador_id'", String.class)).isEqualTo("YES");
        String tabelaReferenciada = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> {
            var metadata = connection.getMetaData();
            try (var keys = metadata.getImportedKeys(null, null,
                    metadata.storesUpperCaseIdentifiers() ? "EMPRESTIMOS" : "emprestimos")) {
                while (keys.next()) {
                    if (keys.getString("FKCOLUMN_NAME").equalsIgnoreCase("fiador_id")) {
                        return keys.getString("PKTABLE_NAME");
                    }
                }
            }
            return null;
        });
        assertThat(tabelaReferenciada).isEqualToIgnoringCase("clientes");
        Fixture f = fixture();
        simularLegado(f);
        em.clear();
        assertThat(repository.buscarDadosContrato(f.emp().getId(), f.perfil().getId()).orElseThrow().getFiador()).isNull();
    }

    @Test
    void montaDtoCompletoComValoresPersistidosEConsultaNaoAlteraFinanceiro() {
        Fixture f = fixture();
        f.emp().getParcelas().get(0).setDataVencimento(LocalDate.now().minusDays(5));
        em.flush();
        Long id = f.emp().getId();
        em.clear();
        Emprestimo persistido = repository.buscarDadosContrato(id, f.perfil().getId()).orElseThrow();
        List<Object> antes = financeiro(persistido);
        DadosContratoDTO dto = dados(f);
        assertThat(dto.emprestimoId()).isEqualTo(id);
        assertThat(dto.credor()).isEqualTo(parte("Credor completo", "Credor", 10, "00000000001"));
        assertThat(dto.devedor()).isEqualTo(parte("Devedor completo", "Devedor", 20, "00000000002"));
        assertThat(dto.fiador()).isEqualTo(parte("Fiador completo", "Fiador", 30, "00000000003"));
        assertThat(dto.emprestimo()).isEqualTo(new EmprestimoContratoDTO(
                persistido.getValorEmprestado(), persistido.getTaxaDeJuros(), persistido.getValorParcela(),
                persistido.getValorTotalEmprestimo(), 3, persistido.getDataDoEmprestimo(),
                persistido.getInicioPagamento(), persistido.getFinalPagamento(), FormaPagamento.PIX,
                FrequenciaPagamento.MENSAL, TipoContrato.FISICO, EmprestimoStatus.EM_ABERTO));
        assertThat(dto.emprestimo().valorParcela()).isEqualByComparingTo("200");
        assertThat(dto.parcelas()).containsExactlyElementsOf(persistido.getParcelas().stream()
                .sorted(java.util.Comparator.comparing(Parcelas::getNumeroParcela))
                .map(p -> new ParcelaContratoDTO(p.getNumeroParcela(), p.getDataVencimento(), p.getValorParcela())).toList());
        assertThat(dto.parcelas()).extracting(ParcelaContratoDTO::numeroParcela).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> dto.parcelas().clear()).isInstanceOf(UnsupportedOperationException.class);
        em.flush();
        em.clear();
        assertThat(financeiro(repository.buscarDadosContrato(id, f.perfil().getId()).orElseThrow())).isEqualTo(antes);
    }

    @Test
    void consultaCarregaRelacionamentosMesmoSemOpenSessionEIgnoraObservacoesInternas() {
        Fixture f = fixture();
        em.flush();
        em.clear();
        Emprestimo e = repository.buscarDadosContrato(f.emp().getId(), f.perfil().getId()).orElseThrow();
        var util = em.getEntityManagerFactory().getPersistenceUnitUtil();
        for (String relacao : List.of("perfil", "cliente", "fiador", "parcelas")) {
            assertThat(util.isLoaded(e, relacao)).as(relacao).isTrue();
        }
        assertThat(util.isLoaded(e.getParcelas().get(0), "observacoes")).isFalse();
        DadosContratoDTO dto = dados(f);
        em.clear();
        assertThat(dto.credor().cpf()).isEqualTo("00000000001");
        assertThat(dto.parcelas()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nomeCompleto", "nacionalidade", "estadoCivil", "profissao", "rg",
            "rgOrgaoExpedidor", "cpf", "enderecoRua", "enderecoNumero", "enderecoBairro",
            "enderecoCep", "enderecoCidade", "enderecoEstado"})
    void identificaCadaCampoObrigatorioNuloOuEmBrancoNasTresPartes(String campo) throws Exception {
        Fixture f = fixture();
        Object[] pessoas = {f.perfil(), f.devedor(), f.fiador()};
        String[] partes = {"credor", "devedor", "fiador"};
        for (int i = 0; i < pessoas.length; i++) {
            String nomeCampo = i > 0 && campo.equals("nomeCompleto") ? "nome" : campo;
            Field field = pessoas[i].getClass().getDeclaredField(nomeCampo);
            field.setAccessible(true);
            Object original = field.get(pessoas[i]);
            field.set(pessoas[i], null);
            String erro = "Dados do " + partes[i] + " incompletos: " + nomeCampo + ".";
            assertThatThrownBy(() -> dados(f)).isInstanceOf(RegraNegocioException.class).hasMessage(erro);
            if (field.getType() == String.class) {
                field.set(pessoas[i], " \t ");
                assertThatThrownBy(() -> dados(f)).hasMessage(erro);
            }
            field.set(pessoas[i], original);
        }
    }

    @Test
    void agregaPendenciasSemExporValoresPessoaisENaoUsaNomeCurtoDoCredor() {
        Fixture f = fixture();
        f.perfil().setNomeCompleto(null);
        f.perfil().setCpf(null);
        f.devedor().setProfissao("  ");
        f.fiador().setEnderecoCep(null);
        assertThatThrownBy(() -> dados(f)).hasMessage("Dados do credor incompletos: nomeCompleto, cpf. "
                + "Dados do devedor incompletos: profissao. Dados do fiador incompletos: enderecoCep.");
    }

    @Test
    void complementoOpcionalENumeroZeroNaoCriamRegraAdicional() {
        Fixture f = fixture();
        f.perfil().setEnderecoComplemento(null);
        f.devedor().setEnderecoComplemento(null);
        f.fiador().setEnderecoComplemento(null);
        f.fiador().setEnderecoNumero(0);
        assertThat(dados(f).fiador().enderecoNumero()).isZero();
        assertThat(dados(f).credor().enderecoComplemento()).isNull();
    }

    @Test
    void rejeitaFiadorNullNoServiceAntesDePersistirEmprestimoOuParcelas() {
        Perfil p = perfil();
        Cliente c = cliente(p, "Devedor", 20, "00000000002");
        assertThatThrownBy(() -> criar(p, c, null)).hasMessage("Fiador é obrigatório.");
        assertThat(repository.findAllByPerfilId(p.getId())).isEmpty();
        assertThat(parcelas.count()).isZero();
    }

    @Test
    void permiteCriacaoComCadastrosIncompletosMasRejeitaDadosContratuais() {
        Fixture f = fixture();
        f.perfil().setNomeCompleto(null);
        f.fiador().setCpf(null);
        Emprestimo novo = criar(f.perfil(), f.devedor(), f.fiador().getId());
        assertThat(novo.getFiador().getId()).isEqualTo(f.fiador().getId());
        assertThatThrownBy(() -> contratos.buscarDadosContrato(f.perfil().getId(), f.devedor().getId(), novo.getId()))
                .hasMessageContaining("credor").hasMessageContaining("fiador");
        emprestimos.pagarParcela(f.perfil().getId(), novo.getId(), 1);
        assertThat(novo.getValorRecebido()).isEqualByComparingTo("200");
    }

    @Test
    void permiteDevedorComoFiadorInclusiveNosDadosContratuais() {
        Fixture f = fixture();
        Emprestimo e = criar(f.perfil(), f.devedor(), f.devedor().getId());
        DadosContratoDTO dto = contratos.buscarDadosContrato(f.perfil().getId(), f.devedor().getId(), e.getId());
        assertThat(dto.fiador()).isEqualTo(dto.devedor());
    }

    @Test
    void legadoSemFiadorContinuaListavelPagavelQuitavelENosRelatoriosMasContratoFalha() {
        Fixture f = fixture();
        simularLegado(f);
        assertThatThrownBy(() -> dados(f)).hasMessage("O empréstimo não possui fiador cadastrado.");
        assertThat(emprestimos.listarDetalhado(f.perfil().getId(), f.devedor())).singleElement()
                .satisfies(d -> assertThat(d.fiadorId()).isNull());
        assertThat(emprestimos.listarEmprestimosCliente(f.perfil().getId(), f.devedor())).hasSize(1);
        assertThat(emprestimos.listarParcelasDoCliente(f.perfil().getId(), f.devedor().getId(), f.emp().getId())).hasSize(3);
        assertThat(relatorios.gerarHistoricoCliente(f.perfil().getId(), f.devedor()).totalEmprestimos()).isEqualTo(1);
        emprestimos.pagarParcelaParcial(f.perfil().getId(), f.emp().getId(), 1, new BigDecimal("50"));
        emprestimos.pagarParcela(f.perfil().getId(), f.emp().getId(), 1);
        emprestimos.quitarEmprestimo(f.perfil().getId(), f.devedor().getId(), f.emp().getId());
        assertThat(f.emp().getEmprestimoStatus()).isEqualTo(EmprestimoStatus.QUITADO);
        assertThat(f.emp().getFiador()).isNull();
    }

    @Test
    void legadoIncompletoMantemEstornoAlteracaoReprogramacaoObservacoesERefinanciamento() {
        Fixture f = fixture();
        simularLegado(f);
        f.perfil().setNomeCompleto(null);
        f.devedor().setCpf(null);
        emprestimos.pagarParcela(f.perfil().getId(), f.emp().getId(), 1);
        parcelaService.desfazerPagamentoParcela(f.perfil().getId(), f.emp().getId(), 1);
        assertThat(f.emp().getValorRecebido()).isEqualByComparingTo("0");
        parcelaService.alterarParcela(f.perfil().getId(), f.emp().getId(), 1,
                LocalDate.now().plusDays(3), new BigDecimal("250"));
        assertThat(f.emp().getValorTotalEmprestimo()).isEqualByComparingTo("650");
        emprestimos.reprogramarParcelas(f.perfil().getId(), f.devedor().getId(), f.emp().getId(),
                LocalDate.now().plusMonths(1), new BigDecimal("210"));
        observacoes.criarObservacao(f.perfil().getId(), f.emp().getId(), 1, "Nota interna");
        assertThat(observacoes.listarObservacoes(f.perfil().getId(), f.emp().getId(), 1)).hasSize(1);
        emprestimos.refinanciarEmprestimo(f.perfil().getId(), f.devedor().getId(), f.emp().getId());
        assertThat(f.emp().getEmprestimoStatus()).isEqualTo(EmprestimoStatus.REFINANCIADO);
        assertThat(f.emp().getValorRecebido()).isEqualByComparingTo("630");
        assertThat(f.emp().getFiador()).isNull();
        assertThatThrownBy(() -> dados(f)).hasMessage("O empréstimo não possui fiador cadastrado.");
    }

    @Test
    void regularizaLegadoSemModificarValoresDatasStatusOuParcelas() {
        Fixture f = fixture();
        simularLegado(f);
        emprestimos.pagarParcelaParcial(f.perfil().getId(), f.emp().getId(), 1, new BigDecimal("50"));
        List<Object> antes = financeiro(f.emp());
        emprestimos.alterarFiador(f.perfil().getId(), f.devedor().getId(), f.emp().getId(), f.fiador().getId());
        em.flush();
        em.clear();
        assertThat(dados(f).fiador().nomeCompleto()).isEqualTo("Fiador completo");
        Emprestimo e = repository.buscarDadosContrato(f.emp().getId(), f.perfil().getId()).orElseThrow();
        assertThat(financeiro(e)).isEqualTo(antes);
        assertThat(e.getFiador().getId()).isEqualTo(f.fiador().getId());
    }

    @Test
    void alteraFiadorDeContratoEncerradoSemInventarRestricaoDeStatus() {
        Fixture f = fixture();
        emprestimos.quitarEmprestimo(f.perfil().getId(), f.devedor().getId(), f.emp().getId());
        List<Object> antes = financeiro(f.emp());
        emprestimos.alterarFiador(f.perfil().getId(), f.devedor().getId(), f.emp().getId(), f.devedor().getId());
        assertThat(financeiro(f.emp())).isEqualTo(antes);
        assertThat(dados(f).fiador()).isEqualTo(dados(f).devedor());
    }

    @Test
    void rejeitaPerfilClienteEFiadorIncompativeisNaConsultaENaAlteracao() {
        Fixture f = fixture();
        Perfil outro = perfil();
        Cliente externo = cliente(outro, "Externo", 40, "00000000004");
        assertThatThrownBy(() -> contratos.buscarDadosContrato(outro.getId(), f.devedor().getId(), f.emp().getId()))
                .hasMessageContaining("Empréstimo não encontrado");
        assertThatThrownBy(() -> contratos.buscarDadosContrato(f.perfil().getId(), f.fiador().getId(), f.emp().getId()))
                .hasMessageContaining("não pertence ao cliente");
        assertThatThrownBy(() -> contratos.buscarDadosContrato(f.perfil().getId(), f.devedor().getId(), Long.MAX_VALUE))
                .hasMessageContaining("Empréstimo não encontrado");
        assertThatThrownBy(() -> emprestimos.alterarFiador(outro.getId(), f.devedor().getId(), f.emp().getId(), externo.getId()))
                .hasMessageContaining("Empréstimo não encontrado");
        assertThatThrownBy(() -> emprestimos.alterarFiador(f.perfil().getId(), f.fiador().getId(), f.emp().getId(), f.fiador().getId()))
                .hasMessageContaining("não pertence ao cliente");
        for (Long id : new Long[]{null, Long.MAX_VALUE, externo.getId()}) {
            assertThatThrownBy(() -> emprestimos.alterarFiador(f.perfil().getId(), f.devedor().getId(), f.emp().getId(), id))
                    .isInstanceOf(RegraNegocioException.class);
            assertThat(f.emp().getFiador().getId()).isEqualTo(f.fiador().getId());
        }
        // Simula uma associação inconsistente preexistente, sem usar o fluxo de criação.
        f.emp().setFiador(externo);
        assertThatThrownBy(() -> dados(f)).hasMessage("Fiador não encontrado para o perfil informado.");
        f.emp().setFiador(f.fiador());
        f.emp().setCliente(externo);
        assertThatThrownBy(() -> contratos.buscarDadosContrato(f.perfil().getId(), externo.getId(), f.emp().getId()))
                .hasMessage("Devedor não encontrado para o perfil informado.");
    }

    private Fixture fixture() {
        Perfil p = perfil();
        Cliente d = cliente(p, "Devedor", 20, "00000000002");
        Cliente f = cliente(p, "Fiador", 30, "00000000003");
        return new Fixture(p, d, f, criar(p, d, f.getId()));
    }

    private Perfil perfil() {
        Perfil p = new Perfil();
        p.setNome("Carteira " + UUID.randomUUID());
        p.setNomeCompleto("Credor completo");
        p.setNacionalidade("Nacionalidade Credor");
        p.setEstadoCivil(EstadoCivil.CASADO);
        p.setProfissao("Profissão Credor");
        p.setRg("RG Credor");
        p.setRgOrgaoExpedidor("Órgão Credor");
        p.setCpf("00000000001");
        p.setEnderecoRua("Rua Credor");
        p.setEnderecoNumero(10);
        p.setEnderecoComplemento("Complemento Credor");
        p.setEnderecoBairro("Bairro Credor");
        p.setEnderecoCep("CEP Credor");
        p.setEnderecoCidade("Cidade Credor");
        p.setEnderecoEstado("SP");
        return perfis.save(p);
    }

    private Cliente cliente(Perfil p, String papel, int numero, String cpf) {
        Cliente c = new Cliente();
        c.setPerfil(p);
        c.setNome(papel + " completo");
        c.setNacionalidade("Nacionalidade " + papel);
        c.setEstadoCivil(EstadoCivil.CASADO);
        c.setProfissao("Profissão " + papel);
        c.setRg("RG " + papel);
        c.setRgOrgaoExpedidor("Órgão " + papel);
        c.setCpf(cpf);
        c.setEnderecoRua("Rua " + papel);
        c.setEnderecoNumero(numero);
        c.setEnderecoComplemento("Complemento " + papel);
        c.setEnderecoBairro("Bairro " + papel);
        c.setEnderecoCep("CEP " + papel);
        c.setEnderecoCidade("Cidade " + papel);
        c.setEnderecoEstado("SP");
        return clientes.save(c);
    }

    private Emprestimo criar(Perfil p, Cliente d, Long fiadorId) {
        return emprestimos.criarEmprestimo(p.getId(), d, new BigDecimal("600"), 3, BigDecimal.ZERO,
                FormaPagamento.PIX, FrequenciaPagamento.MENSAL, TipoContrato.FISICO, fiadorId);
    }

    private DadosContratoDTO dados(Fixture f) {
        return contratos.buscarDadosContrato(f.perfil().getId(), f.devedor().getId(), f.emp().getId());
    }

    private void simularLegado(Fixture f) {
        // Fixture de registro histórico: somente a persistência permite NULL, nunca a nova criação.
        f.emp().setFiador(null);
        em.flush();
    }

    private ParteContratoDTO parte(String nome, String papel, int numero, String cpf) {
        return new ParteContratoDTO(nome, "Nacionalidade " + papel, EstadoCivil.CASADO, "Profissão " + papel,
                "RG " + papel, "Órgão " + papel, cpf, "Rua " + papel, numero, "Complemento " + papel,
                "Bairro " + papel, "CEP " + papel, "Cidade " + papel, "SP");
    }

    private List<Object> financeiro(Emprestimo e) {
        return valores(e.getId(), e.getPerfil().getId(), e.getCliente().getId(), e.getValorEmprestado(),
                e.getQuantidadeParcelas(), e.getTaxaDeJuros(), e.getValorParcela(), e.getValorTotalEmprestimo(),
                e.getValorRecebido(), e.getValorAReceber(), e.getDataDoEmprestimo(), e.getInicioPagamento(),
                e.getFinalPagamento(), e.getEmprestimoStatus(), e.getFormaPagamento(), e.getFrequenciaPagamento(),
                e.getTipoContrato(), e.getParcelas().stream().sorted(java.util.Comparator.comparing(Parcelas::getNumeroParcela))
                        .map(p -> valores(p.getId(), p.getNumeroParcela(), p.getValorParcela(), p.getValorPago(),
                                p.getDataVencimento(), p.getDataPagamento(), p.getStatus(), p.getPerfil().getId())).toList());
    }

    private List<Object> valores(Object... valores) {
        return Arrays.stream(valores).map(v -> v instanceof BigDecimal n ? n.stripTrailingZeros() : v).toList();
    }

    private record Fixture(Perfil perfil, Cliente devedor, Cliente fiador, Emprestimo emp) {}
}
