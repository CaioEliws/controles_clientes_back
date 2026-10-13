package com.caio.controle_clientes;

import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.services.parcela.ParcelaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AuditoriaStatusConcorrenciaTests {
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoRepositorio loans;
    @Autowired EmprestimoService pagamentos;
    @Autowired ParcelaService status;
    @Autowired PlatformTransactionManager transactions;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoSpyBean ParcelaRepositorio parcelas;

    @Test void consultaDeAtrasadasNaoSobrescrevePagamentoConcorrente() throws Exception {
        var tx = new TransactionTemplate(transactions);
        var ids = tx.execute(s -> {
            var f = ContratoDocxFixture.criar(perfis, clientes, pagamentos);
            f.emprestimo().getParcelas().get(0).setDataVencimento(LocalDate.now().minusDays(1));
            return new Long[]{f.perfil().getId(), f.emprestimo().getId()};
        });
        var leuEstadoAntigo = new CountDownLatch(1);
        var pagamentoConcluido = new CountDownLatch(1);
        // Deterministically expose the old read/change/save race, if that path is used.
        doAnswer(invocation -> {
            var resultado = em.createQuery("select p from Parcelas p where p.status = :status and p.perfil.id = :perfil and p.dataVencimento < :hoje", com.caio.controle_clientes.models.Parcelas.class)
                    .setParameter("status", ParcelaStatus.PENDENTE).setParameter("perfil", ids[0])
                    .setParameter("hoje", LocalDate.now()).getResultList();
            leuEstadoAntigo.countDown();
            assertThat(pagamentoConcluido.await(15, TimeUnit.SECONDS)).isTrue();
            return resultado;
        }).when(parcelas).findByStatusAndPerfilIdAndDataVencimentoBefore(
                eq(ParcelaStatus.PENDENTE), eq(ids[0]), any(LocalDate.class));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var consulta = executor.submit(() -> status.atualizarParcelasAtrasadas(ids[0]));
            if (!leuEstadoAntigo.await(2, TimeUnit.SECONDS)) consulta.get(15, TimeUnit.SECONDS);
            pagamentos.pagarParcela(ids[0], ids[1], 1);
            pagamentoConcluido.countDown();
            consulta.get(15, TimeUnit.SECONDS);
            tx.executeWithoutResult(s -> {
                var p = parcelas.findByEmprestimoIdAndPerfilIdAndNumeroParcela(ids[1], ids[0], 1).orElseThrow();
                assertThat(p.getValorPago()).isEqualByComparingTo("1250.50");
                assertThat(p.getDataPagamento()).isNotNull();
                assertThat(p.getStatus()).isEqualTo(ParcelaStatus.PAGO);
                var e = loans.findById(ids[1]).orElseThrow();
                assertThat(e.getValorRecebido()).isEqualByComparingTo(p.getValorPago());
            });
        } finally {
            pagamentoConcluido.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(s -> {
                var e = loans.buscarComParcelas(ids[1], ids[0]).orElseThrow();
                var pessoas = java.util.List.of(e.getCliente().getId(), e.getFiador().getId());
                loans.delete(e);
                loans.flush();
                clientes.deleteAllById(pessoas);
                clientes.flush();
                perfis.deleteById(ids[0]);
            });
        }
    }
}
