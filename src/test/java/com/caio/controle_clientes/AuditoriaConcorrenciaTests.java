package com.caio.controle_clientes;

import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AuditoriaConcorrenciaTests {
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoRepositorio loans;
    @Autowired EmprestimoService service;
    @Autowired PlatformTransactionManager transactions;

    @Test void pagamentosConcorrentesNaoPerdemRecebimentoNemUltrapassamSaldo() throws Exception {
        var tx=new TransactionTemplate(transactions);
        Long[] ids=tx.execute(s->{
            var f=ContratoDocxFixture.criar(perfis,clientes,service);
            return new Long[]{f.perfil().getId(),f.emprestimo().getId()};
        });
        var start=new CountDownLatch(1);
        var ready=new CountDownLatch(2);
        var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> pagamento=()->{
                ready.countDown(); start.await(10,TimeUnit.SECONDS);
                try { service.pagarParcelaParcial(ids[0],ids[1],1,new BigDecimal("700")); return true; }
                catch(RuntimeException e) {
                    assertThat(e.getMessage()).contains("saldo da parcela");return false;
                }
            };
            var a=executor.submit(pagamento);var b=executor.submit(pagamento);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            boolean primeiro=a.get(30,TimeUnit.SECONDS),segundo=b.get(30,TimeUnit.SECONDS);
            assertThat(primeiro!=segundo).as("Só um pagamento de 700 cabe na parcela de 1250,50").isTrue();
            tx.executeWithoutResult(s->{
                var e=loans.buscarComParcelas(ids[1],ids[0]).orElseThrow();
                assertThat(e.getParcelas().get(0).getValorPago()).isEqualByComparingTo("700");
                assertThat(e.getValorRecebido()).isEqualByComparingTo("700");
                assertThat(e.getValorAReceber()).isEqualByComparingTo("3051.50");
            });
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(s->{
                var e=loans.buscarComParcelas(ids[1],ids[0]).orElseThrow();loans.delete(e);
                clientes.deleteAllById(e.getFiador()==null?java.util.List.of(e.getCliente().getId()):java.util.List.of(e.getCliente().getId(),e.getFiador().getId()));
                perfis.deleteById(ids[0]);
            });
        }
    }
}
