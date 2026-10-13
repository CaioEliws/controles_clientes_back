package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties={"spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.caio.controle_clientes.FechamentoVolumeTests$SqlCapture"})
class FechamentoVolumeTests {
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;
    @Autowired WebApplicationContext context;
    public static class SqlCapture implements StatementInspector {
        static final List<String> statements = Collections.synchronizedList(new ArrayList<>());
        public String inspect(String sql) { statements.add(sql); return sql; }
    }

    @Test void medirEndpointsCom500Clientes1000Emprestimos6000Parcelas() throws Exception {
        Perfil perfil=new Perfil();perfil.setNome("Volume "+UUID.randomUUID());perfil=perfis.save(perfil);
        long id=perfil.getId();
        List<Cliente> pessoas=new ArrayList<>();
        for(int i=0;i<500;i++){var c=new Cliente();c.setPerfil(perfil);c.setNome("Pessoa volume "+i);pessoas.add(c);}
        pessoas=clientes.saveAll(pessoas);long cliente=pessoas.get(0).getId(),fiador=pessoas.get(499).getId();
        List<Object[]> loanRows=new ArrayList<>();
        for(int i=0;i<1000;i++) loanRows.add(new Object[]{id,cliente,fiador,LocalDate.now(),"PIX",100,6,0,100,0,100,16.67,LocalDate.now().minusMonths(1),LocalDate.now().plusMonths(4),"MENSAL","FISICO","EM_ABERTO"});
        jdbc.batchUpdate("insert into emprestimos(perfil_id,cliente_id,fiador_id,data_do_emprestimo,forma_pagamento,valor_emprestado,quantidade_parcelas,taxa_de_juros,valorareceber,valor_recebido,valor_total_emprestimo,valor_parcela,inicio_pagamento,final_pagamento,frequencia_pagamento,tipo_contrato,emprestimo_status) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",loanRows);
        List<Long> loans=jdbc.queryForList("select id from emprestimos where perfil_id=? order by id",Long.class,id);
        List<Object[]> parcelas=new ArrayList<>();
        for(long e:loans)for(int n=1;n<=6;n++)parcelas.add(new Object[]{id,e,n,16.67,0,LocalDate.now().plusMonths(n-2),"PENDENTE"});
        jdbc.batchUpdate("insert into parcelas(perfil_id,emprestimo_id,numero_parcela,valor_parcela,valor_pago,data_vencimento,status) values(?,?,?,?,?,?,?)",parcelas);
        long loan=loans.get(0);
        byte[] grande=new byte[1024*1024];
        jdbc.update("insert into contratos_gerados(perfil_id,emprestimo_id,data_geracao,versao_template,nome_arquivo,hash_documento,snapshot_json,promissoria_snapshot_json,docx,pdf_final,pdf_final_assinado,hash_pdf_final,hash_pdf_assinado) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",id,loan,java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()),"V1","volume.docx","h","s".repeat(1000000),"p".repeat(1000000),grande,grande,grande,"pdf","signed");
        var mvc=MockMvcBuilders.webAppContextSetup(context).build();
        var stats=emf.unwrap(SessionFactory.class).getStatistics();
        List<Map<String,Object>> measurements=new ArrayList<>();
        for(String path:List.of("/clientes","/emprestimos/detalhes","/clientes/"+cliente+"/emprestimos/detalhes","/parcelas?status=PENDENTE","/parcelas/vencendo-hoje","/parcelas/mes-atual","/relatorios/clientes/"+cliente,"/relatorios/inadimplentes","/clientes/"+cliente+"/emprestimos/"+loan+"/contratos")){
            stats.clear();SqlCapture.statements.clear();long start=System.nanoTime();
            var response=mvc.perform(get(path).header("X-Perfil-Id",id)).andReturn().getResponse();
            assertEquals(200,response.getStatus(),path+" "+response.getContentAsString());
            Map<String,Object> measurement=new LinkedHashMap<>();measurement.put("endpoint",path);measurement.put("ms",(System.nanoTime()-start)/1_000_000);measurement.put("queries",stats.getPrepareStatementCount());measurement.put("bytes",response.getContentAsByteArray().length);
            if (!System.getProperty("fechamento.volume.fase", "medicao").startsWith("antes")) {
                assertTrue(stats.getPrepareStatementCount() <= 5, path + " voltou a fazer N+1");
                if (path.equals("/clientes")) {
                    var json = new tools.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
                    assertEquals(500, json.size());
                    long total = 0;
                    for (var pessoa : json) total += pessoa.get("quantidadeEmprestimos").asLong();
                    assertEquals(1000, total);
                }
            }
            if(path.endsWith("/contratos")){
                assertTrue(SqlCapture.statements.stream().noneMatch(sql->sql.contains("snapshot_json")||sql.contains(".docx")||sql.contains(".pdf_final")),SqlCapture.statements.toString());
                assertTrue(response.getContentAsByteArray().length<2000);
                measurement.put("blobsSelecionados",false);
            }
            measurements.add(measurement);
        }
        var outro=new Perfil();outro.setNome("Volume vazio "+UUID.randomUUID());outro=perfis.save(outro);
        assertEquals("[]",mvc.perform(get("/emprestimos/detalhes").header("X-Perfil-Id",outro.getId())).andReturn().getResponse().getContentAsString());
        assertEquals(400,mvc.perform(get("/emprestimos/detalhes")).andReturn().getResponse().getStatus());
        String run=System.getProperty("fechamento.volume.fase","medicao");
        Path out=Path.of(".auditoria/fechamento/volume-"+run+".json");Files.createDirectories(out.getParent());
        Files.writeString(out,new tools.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("clientes",500,"emprestimos",1000,"parcelas",6000,"perfil",id,"measurements",measurements)));
    }
}

