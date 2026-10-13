package com.caio.controle_clientes;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.importClientes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ImportacaoClientesMatchingTests {
    @Autowired ImportacaoClientesCsvService service;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired com.caio.controle_clientes.services.ImportService importacaoExcel;
    @Autowired EmprestimoRepositorio emprestimos;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    Perfil perfil(){var p=new Perfil();p.setNome("Matching "+UUID.randomUUID());return perfis.saveAndFlush(p);}
    Cliente cliente(Perfil p,String nome,String cpf){var c=new Cliente();c.setPerfil(p);c.setNome(nome);c.setCpf(cpf);return clientes.saveAndFlush(c);}
    MockMultipartFile csv(String nome,String cpf){return new MockMultipartFile("file","a.csv","text/csv",("nomeCompleto;cpf;statusExtracao;profissao;rg;enderecoRua\n"+nome+";"+(cpf==null?"":cpf)+";OK;motorista;36956870;Rua Nova").getBytes(StandardCharsets.UTF_8));}
    @ParameterizedTest @CsvSource(value={"João da Silva|  joão da silva  ","Maria  Aparecida da Silva|Maria Aparecida da Silva","João da Silva|JOAO DA SILVA","Adriele Aparecida Bento|adriele aparecida bento"},delimiter='|')
    void completaMesmoClienteSemCpfSemAlterarGrafia(String banco,String csvNome){
        var p=perfil();var c=cliente(p,banco,null);
        var antes=service.importar(p.getId(),csv(csvNome,"00000000002"),true);
        assertThat(antes.clientesEncontradosPorNome()).isEqualTo(1);assertThat(antes.criados()).isZero();assertThat(clientes.findById(c.getId()).orElseThrow().getCpf()).isNull();
        var r=service.importar(p.getId(),csv(csvNome,"00000000002"),false);
        assertThat(r.clientesEncontradosPorNome()).isEqualTo(1);assertThat(r.clientesAtualizados()).isEqualTo(1);assertThat(r.camposPreenchidos()).isEqualTo(4);
        var depois=clientes.findById(c.getId()).orElseThrow();assertThat(depois.getNome()).isEqualTo(banco);assertThat(depois.getCpf()).isEqualTo("000.000.000-02");
        assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(1);assertThat(r.detalhes().get(0).match()).isEqualTo("NOME");
    }
    @Test void fallbackCpfPreservaNomeERegistraConflito(){
        var p=perfil();var c=cliente(p,"João C. Silva","00000000002");var r=service.importar(p.getId(),csv("João Carlos Silva","00000000002"),false);
        assertThat(r.clientesEncontradosPorCpf()).isEqualTo(1);assertThat(r.criados()).isZero();assertThat(r.conflitosPreservados()).isEqualTo(1);
        assertThat(r.detalhes().get(0).status()).isEqualTo("CONFLITO_PRESERVADO");assertThat(clientes.findById(c.getId()).orElseThrow().getNome()).isEqualTo("João C. Silva");
    }
    @Test void identidadeDivergenteNaoAtualizaNenhumCadastro(){
        var p=perfil();var a=cliente(p,"João Silva","00000000002");var b=cliente(p,"José Silva","00000000003");
        var r=service.importar(p.getId(),csv("João Silva","00000000003"),false);
        assertThat(r.conflitosIdentidade()).isEqualTo(1);assertThat(r.bloqueados()).isEqualTo(1);assertThat(r.camposPreenchidos()).isZero();
        assertThat(clientes.findById(a.getId()).orElseThrow().getProfissao()).isNull();assertThat(clientes.findById(b.getId()).orElseThrow().getProfissao()).isNull();
    }
    @Test void homonimosSemCpfResolvidoNaoSaoEscolhidos(){
        var p=perfil();cliente(p,"José da Silva",null);cliente(p,"JOSE DA SILVA",null);
        var r=service.importar(p.getId(),csv("José da Silva","00000000002"),false);
        assertThat(r.ambiguos()).isEqualTo(1);assertThat(r.bloqueados()).isEqualTo(1);assertThat(r.detalhes().get(0).status()).isEqualTo("AMBIGUO_NOME");
        assertThat(clientes.findAllByPerfilId(p.getId())).allMatch(c->c.getCpf()==null);
    }
    @Test void cpfResolveHomonimoSemAlterarOutroCliente(){
        var p=perfil();var a=cliente(p,"José da Silva",null);var b=cliente(p,"JOSE DA SILVA","00000000002");
        var r=service.importar(p.getId(),csv("José da Silva","00000000002"),false);
        assertThat(r.clientesEncontradosPorCpf()).isEqualTo(1);assertThat(r.ambiguos()).isZero();
        assertThat(clientes.findById(a.getId()).orElseThrow().getProfissao()).isNull();assertThat(clientes.findById(b.getId()).orElseThrow().getProfissao()).isEqualTo("motorista");
    }
    @Test void nomeEmOutroPerfilNaoEEncontrado(){
        var a=perfil();var b=perfil();var c=cliente(a,"João da Silva",null);
        assertThat(service.importar(b.getId(),csv("João da Silva","00000000002"),false).criados()).isEqualTo(1);
        assertThat(clientes.findById(c.getId()).orElseThrow().getCpf()).isNull();
    }
    @Test void cpfAusentePodeCompletarNomeIdentificadoMasNaoCriarNovo(){
        var p=perfil();var c=cliente(p,"Cliente conhecido",null);
        assertThat(service.importar(p.getId(),csv("Cliente conhecido",null),false).clientesAtualizados()).isEqualTo(1);
        assertThat(clientes.findById(c.getId()).orElseThrow().getCpf()).isNull();
        assertThat(service.importar(p.getId(),csv("Novo desconhecido",null),false).bloqueados()).isEqualTo(1);
    }
    @Test void excelAntigoComTresEmprestimosEEnriquecidoNoMesmoCliente() throws Exception {
        var p=perfil();byte[] excel;
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()){
            var sheet=wb.createSheet("Clientes");sheet.createRow(0).createCell(0).setCellValue("Cabeçalho antigo");
            for(int i=0;i<3;i++){
                var row=sheet.createRow(i+1);row.createCell(0).setCellValue("2026-0"+(i+1)+"-01");row.createCell(1).setCellValue("Adriele Aparecida Bento");
                row.createCell(3).setCellValue("DINHEIRO");row.createCell(4).setCellValue("FISICO");row.createCell(5).setCellValue("MENSAL");
                row.createCell(6).setCellValue(600);row.createCell(7).setCellValue(3);row.createCell(8).setCellValue(1);
                row.createCell(10).setCellValue(400);row.createCell(11).setCellValue(200);row.createCell(13).setCellValue(600);row.createCell(14).setCellValue(200);row.createCell(15).setCellValue(0);
                row.createCell(16).setCellValue("2026-02-01");row.createCell(17).setCellValue("2026-04-01");row.createCell(18).setCellValue("EM_ABERTO");
            }
            wb.write(out);excel=out.toByteArray();
        }
        assertThat(importacaoExcel.importarPlanilha(p.getId(),new java.io.ByteArrayInputStream(excel)).linhasImportadas()).isEqualTo(3);
        var antes=clientes.findAllByPerfilId(p.getId());assertThat(antes).hasSize(1);var id=antes.get(0).getId();assertThat(antes.get(0).getCpf()).isNull();
        var financeiro=jdbc.queryForList("select * from emprestimos where perfil_id = ? order by id",p.getId());
        var parcelas=jdbc.queryForList("select pa.* from parcelas pa join emprestimos e on pa.emprestimo_id=e.id where e.perfil_id=? order by pa.id",p.getId());
        assertThat(financeiro).hasSize(3);assertThat(parcelas).hasSize(9);
        var r=service.importar(p.getId(),csv("adriele aparecida bento","00000000002"),false);
        assertThat(r.clientesEncontradosPorNome()).isEqualTo(1);assertThat(r.criados()).isZero();
        assertThat(clientes.findAllByPerfilId(p.getId())).hasSize(1);assertThat(clientes.findById(id).orElseThrow().getCpf()).isEqualTo("000.000.000-02");
        assertThat(jdbc.queryForList("select * from emprestimos where perfil_id = ? order by id",p.getId())).isEqualTo(financeiro);
        assertThat(jdbc.queryForList("select pa.* from parcelas pa join emprestimos e on pa.emprestimo_id=e.id where e.perfil_id=? order by pa.id",p.getId())).isEqualTo(parcelas);
        assertThat(emprestimos.findAllByPerfilId(p.getId())).allMatch(e->e.getCliente().getId().equals(id));
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="clientes.referencia",matches=".+")
    void calculaPreviewComLeituraDoPerfilEdsonSemImportar(){
        try{
            var p=perfil();var snapshot=json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("clientes.referencia"))));
            assertThat(snapshot.get("perfilNome").asText()).isEqualTo("Edson");
            for(var node:snapshot.get("clientes")){
                var c=new Cliente();c.setPerfil(p);var bean=new org.springframework.beans.BeanWrapperImpl(c);
                for(String campo:"nome nomeIndicador nacionalidade estadoCivil profissao rg rgOrgaoExpedidor cpf enderecoRua enderecoNumero enderecoComplemento enderecoBairro enderecoCep enderecoCidade enderecoEstado sexoDocumento".split(" ")){
                    var value=node.get(campo);if(value!=null&&!value.isNull())bean.setPropertyValue(campo,json.convertValue(value,bean.getPropertyType(campo)));
                }
                clientes.save(c);
            }
            long antes=clientes.findAllByPerfilId(p.getId()).size();
            var arquivo=java.nio.file.Path.of(System.getProperty("csv.referencia"));byte[] bytes=java.nio.file.Files.readAllBytes(arquivo);
            var r=service.importar(p.getId(),new MockMultipartFile("file","clientes_extraidos.csv","text/csv",bytes),true);
            assertThat(clientes.findAllByPerfilId(p.getId())).hasSize((int)antes);assertThat(java.nio.file.Files.readAllBytes(arquivo)).isEqualTo(bytes);
            java.nio.file.Files.writeString(java.nio.file.Path.of(".auditoria/matching-clientes/preview-edson.json"),json.writeValueAsString(r),StandardCharsets.UTF_8);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
