package com.caio.controle_clientes;

import com.caio.controle_clientes.exceptions.GlobalExceptionHandler;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.contrato.ContratoGeracaoService;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class EmprestimoExclusaoContratosTests {
    @Autowired EmprestimoService emprestimos;
    @Autowired ContratoGeracaoService geracao;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired EmprestimoRepositorio loans;
    @Autowired ClienteRepositorio clientes;
    @Autowired PerfilRepositorio perfis;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;

    private record Dados(long perfil, long cliente, long fiador, long emprestimo, List<Long> contratos) {}

    private Dados preparar(int quantidade) {
        return new TransactionTemplate(transactions).execute(tx -> {
            var f = ContratoDocxFixture.criar(perfis, clientes, emprestimos);
            f.devedor().setNome("Ana"); f.fiador().setNome("João");
            var ids = new ArrayList<Long>();
            for (int i = 0; i < quantidade; i++) {
                var meta = geracao.gerar(f.perfil().getId(), f.devedor().getId(), f.emprestimo().getId());
                assertThat(meta.pdfDisponivel()).isTrue();
                assertThat(meta.pdfAssinadoDisponivel()).isTrue();
                ids.add(meta.id());
            }
            return new Dados(f.perfil().getId(), f.devedor().getId(), f.fiador().getId(),
                    f.emprestimo().getId(), List.copyOf(ids));
        });
    }

    private String rota(Dados d) { return "/clientes/" + d.cliente() + "/emprestimos/" + d.emprestimo(); }
    private int quantidadeContratos(long emprestimo) {
        return jdbc.queryForObject("select count(*) from contratos_gerados where emprestimo_id = ?", Integer.class, emprestimo);
    }
    private void preservado(Dados d) {
        assertThat(loans.existsById(d.emprestimo())).isTrue();
        assertThat(quantidadeContratos(d.emprestimo())).isEqualTo(d.contratos().size());
        for (long id : d.contratos()) assertThat(contratos.existsById(id)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void deleteHttpRemoveTodosContratosEParcelasComCommitPreservandoPessoasEOutroEmprestimo(int quantidade) throws Exception {
        var d = preparar(quantidade);
        var outro = new TransactionTemplate(transactions).execute(tx -> {
            var e = emprestimos.criarEmprestimo(d.perfil(), clientes.findById(d.cliente()).orElseThrow(),
                    new BigDecimal("400"), 2, BigDecimal.ZERO, FormaPagamento.PIX,
                    FrequenciaPagamento.MENSAL, TipoContrato.FISICO, d.fiador());
            long contrato = geracao.gerar(d.perfil(), d.cliente(), e.getId()).id();
            return new Dados(d.perfil(), d.cliente(), d.fiador(), e.getId(), List.of(contrato));
        });
        preservado(d); preservado(outro);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(delete(rota(d)).header("X-Perfil-Id", d.perfil())).andExpect(status().isNoContent());
        // O request terminou a transação: consultas novas verificam os dados commitados.
        assertThat(loans.existsById(d.emprestimo())).isFalse();
        assertThat(quantidadeContratos(d.emprestimo())).isZero();
        for (long id : d.contratos()) assertThat(contratos.existsById(id)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from parcelas where emprestimo_id = ?", Integer.class, d.emprestimo())).isZero();
        assertThat(clientes.existsById(d.cliente())).isTrue();
        assertThat(clientes.existsById(d.fiador())).isTrue();
        assertThat(perfis.existsById(d.perfil())).isTrue();
        preservado(outro);
    }

    @Test void perfilClienteEHeaderInvalidosNaoRemovemContratos() throws Exception {
        var d = preparar(1); var outro = preparar(0);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(delete(rota(d))).andExpect(status().isBadRequest());
        mvc.perform(delete(rota(d)).header("X-Perfil-Id", outro.perfil())).andExpect(status().isBadRequest());
        mvc.perform(delete("/clientes/" + d.fiador() + "/emprestimos/" + d.emprestimo())
                .header("X-Perfil-Id", d.perfil())).andExpect(status().isBadRequest());
        preservado(d);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAGO", "PARCIAL"})
    void bloqueioDeParcelaPagaPreservaContratos(String statusParcela) {
        var d = preparar(1);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var e = loans.findById(d.emprestimo()).orElseThrow();
            e.getParcelas().get(0).setStatus(ParcelaStatus.valueOf(statusParcela));
        });
        assertThatThrownBy(() -> emprestimos.deletarEmprestimo(d.perfil(), d.cliente(), d.emprestimo()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("parcelas pagas");
        preservado(d);
    }

    @Test void rollbackRestauraContratosEParcelasQuandoTransacaoFalha() {
        var d = preparar(1);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            emprestimos.deletarEmprestimo(d.perfil(), d.cliente(), d.emprestimo());
            assertThat(quantidadeContratos(d.emprestimo())).isZero();
            throw new IllegalStateException("Falha simulada antes do commit");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Falha simulada antes do commit");
        preservado(d);
        assertThat(jdbc.queryForObject("select count(*) from parcelas where emprestimo_id = ?", Integer.class, d.emprestimo())).isEqualTo(3);
    }

    @Test void fallbackDeIntegridadeDoDeleteEmprestimoDescreveExclusao() {
        var handler = new GlobalExceptionHandler();
        var ex = new DataIntegrityViolationException("Falha de integridade simulada");
        assertThat(handler.handleDataIntegrity(ex, new MockHttpServletRequest("DELETE", "/clientes/1/emprestimos/2")).getBody())
                .containsEntry("message", "Não foi possível excluir o empréstimo.");
        assertThat(handler.handleDataIntegrity(ex, new MockHttpServletRequest("POST", "/clientes/1/emprestimos")).getBody())
                .containsEntry("message", "Não foi possível salvar os dados.");
    }
}
