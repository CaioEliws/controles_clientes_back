package com.caio.controle_clientes;

import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:contrato-exclusao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ContratoExclusaoHttpTests {
    @LocalServerPort int port;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoRepositorio emprestimos;
    @Autowired EmprestimoService emprestimoService;
    @Autowired ContratoGeradoRepositorio contratos;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();

    ContratoDocxFixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> ContratoDocxFixture.criar(perfis,clientes,emprestimoService));
    }
    String rota(ContratoDocxFixture f) { return "/clientes/"+f.devedor().getId()+"/emprestimos/"+f.emprestimo().getId()+"/contratos"; }
    HttpResponse<String> request(String method,String path,Long perfil) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if (perfil!=null) builder.header("X-Perfil-Id",perfil.toString());
        return http.send(builder.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    long gerar(ContratoDocxFixture f) throws Exception {
        var response=request("POST",rota(f),f.perfil().getId());
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asLong();
    }
    @Test void excluiSomenteContratoSelecionadoEArquivosSemAlterarDadosFinanceirosOuOutrosHistoricos() throws Exception {
        var f=fixture(); long primeiro=gerar(f), segundo=gerar(f), terceiro=gerar(f);
        // Include a paid installment to prove deleting a document does not undo payments.
        jdbc.update("update parcelas set valor_pago=100, data_pagamento=DATE '2026-10-05' where emprestimo_id=? and numero_parcela=1",f.emprestimo().getId());
        var loan=jdbc.queryForList("select * from emprestimos where id=?",f.emprestimo().getId());
        var parcelas=jdbc.queryForList("select * from parcelas where emprestimo_id=? order by id",f.emprestimo().getId());
        var devedor=jdbc.queryForList("select * from clientes where id=?",f.devedor().getId());
        var fiador=jdbc.queryForList("select * from clientes where id=?",f.fiador().getId());
        var antigo=contratos.findById(primeiro).orElseThrow();
        var recente=contratos.findById(terceiro).orElseThrow();
        assertThat(request("GET",rota(f),f.perfil().getId()).body()).contains("\"id\":"+segundo);
        assertThat(request("DELETE",rota(f)+"/"+segundo,f.perfil().getId()).statusCode()).isEqualTo(204);
        assertThat(contratos.findById(segundo)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from contratos_gerados where id=?",Integer.class,segundo)).isZero();
        var lista=json.readTree(request("GET",rota(f),f.perfil().getId()).body());
        assertThat(lista.size()).isEqualTo(2);
        assertThat(lista.get(0).get("id").asLong()).isEqualTo(terceiro);
        assertThat(lista.get(1).get("id").asLong()).isEqualTo(primeiro);
        for(String formato:new String[]{"download","download-pdf","download-pdf-assinado"}) {
            assertThat(request("GET",rota(f)+"/"+segundo+"/"+formato,f.perfil().getId()).statusCode()).isEqualTo(404);
            assertThat(request("GET",rota(f)+"/"+primeiro+"/"+formato,f.perfil().getId()).statusCode()).isEqualTo(200);
        }
        assertThat(jdbc.queryForList("select * from emprestimos where id=?",f.emprestimo().getId())).isEqualTo(loan);
        assertThat(jdbc.queryForList("select * from parcelas where emprestimo_id=? order by id",f.emprestimo().getId())).isEqualTo(parcelas);
        assertThat(jdbc.queryForList("select * from clientes where id=?",f.devedor().getId())).isEqualTo(devedor);
        assertThat(jdbc.queryForList("select * from clientes where id=?",f.fiador().getId())).isEqualTo(fiador);
        for(var salvo:new com.caio.controle_clientes.models.ContratoGerado[]{antigo,recente}) {
            var atual=contratos.findById(salvo.getId()).orElseThrow();
            assertThat(atual.getDocx()).isEqualTo(salvo.getDocx());
            assertThat(atual.getPdfFinal()).isEqualTo(salvo.getPdfFinal());
            assertThat(atual.getSnapshotJson()).isEqualTo(salvo.getSnapshotJson());
            assertThat(atual.getPromissoriaSnapshotJson()).isEqualTo(salvo.getPromissoriaSnapshotJson());
            assertThat(atual.getHashDocumento()).isEqualTo(salvo.getHashDocumento());
            assertThat(atual.getHashPdfFinal()).isEqualTo(salvo.getHashPdfFinal());
        }
    }
    @Test void rejeitaPerfilClienteEmprestimoIncorretosAusenciaDeHeaderEContratoInexistente() throws Exception {
        var f=fixture(); var outro=fixture(); long id=gerar(f);
        assertThat(request("DELETE",rota(f)+"/"+id,null).statusCode()).isEqualTo(400);
        assertThat(request("DELETE",rota(f)+"/"+id,Long.MAX_VALUE).statusCode()).isEqualTo(400);
        assertThat(request("DELETE",rota(f)+"/"+id,outro.perfil().getId()).statusCode()).isEqualTo(404);
        assertThat(request("DELETE",rota(outro)+"/"+id,outro.perfil().getId()).statusCode()).isEqualTo(404);
        String clienteErrado="/clientes/"+f.fiador().getId()+"/emprestimos/"+f.emprestimo().getId()+"/contratos/"+id;
        assertThat(request("DELETE",clienteErrado,f.perfil().getId()).statusCode()).isEqualTo(404);
        Long segundoEmprestimo=new TransactionTemplate(transactionManager).execute(status -> emprestimoService.criarEmprestimo(
                f.perfil().getId(),clientes.findById(f.devedor().getId()).orElseThrow(),new java.math.BigDecimal("100"),
                1,java.math.BigDecimal.ZERO,com.caio.controle_clientes.models.FormaPagamento.PIX,
                com.caio.controle_clientes.models.FrequenciaPagamento.MENSAL,com.caio.controle_clientes.models.TipoContrato.FISICO,f.fiador().getId()).getId());
        assertThat(request("DELETE",rota(f).replace("/emprestimos/"+f.emprestimo().getId(),"/emprestimos/"+segundoEmprestimo)+"/"+id,f.perfil().getId()).statusCode()).isEqualTo(404);
        assertThat(request("DELETE",rota(f)+"/"+Long.MAX_VALUE,f.perfil().getId()).statusCode()).isEqualTo(404);
        assertThat(contratos.findById(id)).isPresent();
    }
    @Test void excluirUltimoContratoERepetirExclusaoMantemHistoricoVazio() throws Exception {
        var f=fixture(); long id=gerar(f);
        assertThat(request("DELETE",rota(f)+"/"+id,f.perfil().getId()).statusCode()).isEqualTo(204);
        assertThat(request("GET",rota(f),f.perfil().getId()).body()).isEqualTo("[]");
        assertThat(request("DELETE",rota(f)+"/"+id,f.perfil().getId()).statusCode()).isEqualTo(404);
        assertThat(emprestimos.findById(f.emprestimo().getId())).isPresent();
    }
}
