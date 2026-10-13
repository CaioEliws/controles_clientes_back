package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.services.parcela.ParcelaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AuditoriaRegressaoTests {
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired EmprestimoRepositorio loans;
    @Autowired EmprestimoService emprestimos;
    @Autowired ParcelaService parcelas;
    @Autowired RelatorioService relatorios;
    @Autowired WebApplicationContext context;
    @Autowired ExportService exportacao;
    @Autowired ImportService importacao;
    @Autowired com.caio.controle_clientes.services.parcela.ParcelaObservacaoService observacoes;
    @Autowired ParcelaObservacaoRepositorio obsRepository;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.caio.controle_clientes.services.contrato.ContratoGeracaoService documentos;
    private ContratoDocxFixture fixture() { return ContratoDocxFixture.criar(perfis,clientes,emprestimos); }

    @Test void pagamentoParcialAtrasadoImpedeExcluirEmprestimo() {
        var f=fixture();
        emprestimos.pagarParcelaParcial(f.perfil().getId(),f.emprestimo().getId(),1,new BigDecimal("10"));
        f.emprestimo().getParcelas().get(0).setDataVencimento(LocalDate.now().minusDays(1));
        parcelas.atualizarParcelasAtrasadas(f.perfil().getId());
        assertThatThrownBy(() -> emprestimos.deletarEmprestimo(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId()))
                .hasMessageContaining("parcelas pagas");
        assertThat(loans.existsById(f.emprestimo().getId())).isTrue();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void historicoContaPagamentoParcialESaldoReal(boolean atraso) {
        var f=fixture();
        emprestimos.pagarParcelaParcial(f.perfil().getId(),f.emprestimo().getId(),1,new BigDecimal("10"));
        if(atraso) { f.emprestimo().getParcelas().get(0).setDataVencimento(LocalDate.now().minusDays(1)); parcelas.atualizarParcelasAtrasadas(f.perfil().getId()); }
        var r=relatorios.gerarHistoricoCliente(f.perfil().getId(),f.devedor());
        assertThat(r.totalPago()).isEqualByComparingTo("10");
        assertThat(r.totalAberto()).isEqualByComparingTo("3741.50");
        assertThat(r.parcelasAtrasadas()).isEqualTo(atraso?1:0);
        assertThat(r.parcelasAVencer()).isEqualTo(atraso?2:3);
    }
    @Test void desfazerLimpaDataPagamentoERestauraFinanceiro() {
        var f=fixture();var p=f.emprestimo().getParcelas().get(0);
        emprestimos.pagarParcelaParcial(f.perfil().getId(),f.emprestimo().getId(),1,new BigDecimal("10"));
        assertThat(p.getDataPagamento()).isNotNull();
        parcelas.desfazerPagamentoParcela(f.perfil().getId(),f.emprestimo().getId(),1);
        assertThat(p.getDataPagamento()).isNull();assertThat(p.getValorPago()).isEqualByComparingTo("0");
        assertThat(f.emprestimo().getValorRecebido()).isEqualByComparingTo("0");
        assertThat(f.emprestimo().getValorAReceber()).isEqualByComparingTo("3751.50");
    }
    @Test void pagamentoNuloTemErroDeNegocioClaro() {
        var f=fixture();
        assertThatThrownBy(() -> emprestimos.pagarParcelaParcial(f.perfil().getId(),f.emprestimo().getId(),1,null))
                .hasMessage("Valor do pagamento é obrigatório.");
    }
    @ParameterizedTest @ValueSource(strings={"0","-1"})
    void naoPersisteEmprestimoComPrincipalNaoPositivo(String valor) {
        var f=fixture();
        assertThatThrownBy(() -> emprestimos.criarEmprestimo(f.perfil().getId(),f.devedor(),new BigDecimal(valor),2,
                BigDecimal.ZERO,FormaPagamento.PIX,FrequenciaPagamento.MENSAL,TipoContrato.FISICO,f.fiador().getId()))
                .hasMessageContaining("Valor emprestado deve ser maior que zero");
    }
    @Test void preservaCompatibilidadeDoFiadorNoBackendSemMudarRegraExistente() {
        var f=fixture();
        emprestimos.alterarFiador(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),f.devedor().getId());
        assertThat(f.emprestimo().getFiador().getId()).isEqualTo(f.devedor().getId());
    }
    @Test void rotaAusenteRetorna404ComCors() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).build().perform(get("/rota-inexistente-auditoria").header("Origin","http://localhost:5173"))
                .andExpect(status().isNotFound()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
    @Test void enumInvalidoRetorna400SemDetalheInterno() throws Exception {
        var f=fixture();
        MockMvcBuilders.webAppContextSetup(context).build().perform(post("/clientes/"+f.devedor().getId()+"/emprestimos")
                .header("X-Perfil-Id",f.perfil().getId()).header("Origin","http://localhost:5173")
                .contentType("application/json").content("{\"formaPagamento\":\"INVALIDO\"}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
    @Test void arquivoQueNaoEExcelRetorna400ComCors() throws Exception {
        var f = fixture();
        var arquivo = new org.springframework.mock.web.MockMultipartFile("file", "arquivo.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "não é uma planilha".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMvcBuilders.webAppContextSetup(context).build().perform(multipart("/import").file(arquivo)
                .header("X-Perfil-Id", f.perfil().getId()).header("Origin", "http://localhost:5173"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
    @Test void erroDeIntegridadeNaoExpoeSqlAoConsumidor() {
        var handler=new com.caio.controle_clientes.exceptions.GlobalExceptionHandler();
        var resposta=handler.handleDataIntegrity(new org.springframework.dao.DataIntegrityViolationException("SQL confidencial"),
                new org.springframework.mock.web.MockHttpServletRequest("DELETE","/clientes/1/emprestimos/2"));
        assertThat(resposta.getBody()).doesNotContainKey("details");
        assertThat(resposta.getBody().toString()).doesNotContain("SQL confidencial");
    }
    @Test void exportacaoPodeSerImportadaSemDeslocarColunas() throws Exception {
        var f=fixture();
        byte[] xlsx=exportacao.exportarExcel(f.perfil().getId());
        Perfil destino=new Perfil();destino.setNome("Destino da auditoria");perfis.save(destino);
        var r=importacao.importarPlanilha(destino.getId(),new java.io.ByteArrayInputStream(xlsx));
        assertThat(r.linhasImportadas()).isEqualTo(1);
        assertThat(r.erros()).isEmpty();
        var restaurado=loans.findAllByPerfilId(destino.getId()).get(0);
        assertThat(restaurado.getValorTotalEmprestimo()).isEqualByComparingTo(f.emprestimo().getValorTotalEmprestimo());
        assertThat(restaurado.getQuantidadeParcelas()).isEqualTo(3);
        assertThat(importacao.importarPlanilha(destino.getId(),new java.io.ByteArrayInputStream(xlsx)).linhasDuplicadas()).isEqualTo(1);
    }
    @Test void importacaoMensalPreservaDiaBaseDepoisDeFevereiro() {
        var emp=new Emprestimo();emp.setInicioPagamento(LocalDate.of(2027,1,31));emp.setQuantidadeParcelas(3);
        emp.setFrequenciaPagamento(FrequenciaPagamento.MENSAL);emp.setValorParcela(BigDecimal.TEN);
        var lista=new com.caio.controle_clientes.services.importService.ImportacaoParcelaFactory().gerar(emp,0);
        assertThat(lista).extracting(Parcelas::getDataVencimento)
                .containsExactly(LocalDate.of(2027,1,31),LocalDate.of(2027,2,28),LocalDate.of(2027,3,31));
    }
    @Test void backupComPagamentoParcialMantemSaldoDasParcelas() throws Exception {
        var f=fixture();emprestimos.pagarParcelaParcial(f.perfil().getId(),f.emprestimo().getId(),1,new BigDecimal("10"));
        var destino=new Perfil();destino.setNome("Backup parcial");perfis.save(destino);
        var r=importacao.importarPlanilha(destino.getId(),new java.io.ByteArrayInputStream(exportacao.exportarExcel(f.perfil().getId())));
        assertThat(r.linhasImportadas()).isEqualTo(1);
        entityManager.flush();entityManager.clear();
        var restored=loans.findAllByPerfilId(destino.getId()).get(0);
        assertThat(restored.getParcelas()).extracting(Parcelas::getValorPago).containsExactly(new BigDecimal("10.00"),new BigDecimal("0.00"),new BigDecimal("0.00"));
        assertThat(restored.getValorRecebido()).isEqualByComparingTo("10");
    }
    @Test void observacoesTemCrudIsolamentoEExclusaoJuntoComEmprestimo() {
        var f=fixture();observacoes.criarObservacao(f.perfil().getId(),f.emprestimo().getId(),1," primeira ");
        var id=observacoes.listarObservacoes(f.perfil().getId(),f.emprestimo().getId(),1).get(0).id();
        assertThatThrownBy(()->observacoes.editarObservacao(Long.MAX_VALUE,id,"Intruso")).hasMessageContaining("não encontrada");
        observacoes.editarObservacao(f.perfil().getId(),id,"segunda");
        assertThat(observacoes.listarObservacoes(f.perfil().getId(),f.emprestimo().getId(),1).get(0).observacao()).isEqualTo("segunda");
        observacoes.removerObservacao(f.perfil().getId(),id);
        assertThat(obsRepository.existsById(id)).isFalse();
        observacoes.criarObservacao(f.perfil().getId(),f.emprestimo().getId(),1,"Apagar com parcela");
        emprestimos.deletarEmprestimo(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId());loans.flush();
        assertThat(clientes.existsById(f.devedor().getId())).isTrue();
        assertThat(clientes.existsById(f.fiador().getId())).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"2028-01-31","2028-02-29","2027-02-28","2027-12-31"})
    void reprogramacaoMensalPreservaDiaBaseEFinanceiro(String data) {
        var f=fixture();var inicio=LocalDate.parse(data);
        emprestimos.reprogramarParcelas(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),inicio,new BigDecimal("12.34"));
        assertThat(f.emprestimo().getParcelas()).extracting(Parcelas::getDataVencimento).containsExactly(inicio,inicio.plusMonths(1),inicio.plusMonths(2));
        assertThat(f.emprestimo().getValorTotalEmprestimo()).isEqualByComparingTo("37.02");
    }
    @Test void quitacaoERefinanciamentoMantemSomaFinanceira() {
        var f=fixture();emprestimos.pagarParcelaParcial(f.perfil().getId(),f.emprestimo().getId(),1,BigDecimal.TEN);
        emprestimos.quitarEmprestimo(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId());
        assertThat(f.emprestimo().getValorAReceber()).isEqualByComparingTo("0");
        parcelas.desfazerPagamentoParcela(f.perfil().getId(),f.emprestimo().getId(),1);
        emprestimos.refinanciarEmprestimo(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId());
        assertThat(f.emprestimo().getEmprestimoStatus()).isEqualTo(EmprestimoStatus.REFINANCIADO);
        assertThat(f.emprestimo().getValorRecebido()).isEqualByComparingTo("3751.50");
    }
    @Test void umCentavoSemJurosEHabilitadoEParcelaZeroBloqueada() {
        var f=fixture();var e=emprestimos.criarEmprestimo(f.perfil().getId(),f.devedor(),new BigDecimal("0.01"),1,BigDecimal.ZERO,FormaPagamento.PIX,FrequenciaPagamento.MENSAL,TipoContrato.FISICO,f.fiador().getId());
        assertThat(e.getValorParcela()).isEqualByComparingTo("0.01");
        assertThatThrownBy(()->emprestimos.criarEmprestimo(f.perfil().getId(),f.devedor(),new BigDecimal("0.01"),3,BigDecimal.ZERO,FormaPagamento.PIX,FrequenciaPagamento.MENSAL,TipoContrato.FISICO,f.fiador().getId())).hasMessageContaining("Valor calculado");
    }
    @Test void importacaoPreservaTaxaFracionariaETextoDecimal() throws Exception {
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var row=wb.createSheet().createRow(0);var taxa=row.createCell(0);taxa.setCellValue(0.005);
            var estilo=wb.createCellStyle();estilo.setDataFormat(wb.createDataFormat().getFormat("0.00%"));taxa.setCellStyle(estilo);
            var dinheiro=row.createCell(1);dinheiro.setCellValue("1234.56");
            var reader=new com.caio.controle_clientes.services.importService.ImportacaoCellReader();
            var evaluator=wb.getCreationHelper().createFormulaEvaluator();var formatter=new org.apache.poi.ss.usermodel.DataFormatter();
            assertThat(reader.getPercentValue(taxa,evaluator,formatter)).isEqualByComparingTo("0.5");
            assertThat(reader.getMoneyValue(dinheiro,evaluator,formatter)).isEqualByComparingTo("1234.56");
        }
    }
    @Test void importaExportacaoLegadaDeDezoitoColunas() throws Exception {
        var f=fixture();var antigo=arquivoLegado(exportacao.exportarExcel(f.perfil().getId()));
        var destino=new Perfil();destino.setNome("Backup legado");perfis.save(destino);
        assertThat(importacao.importarPlanilha(destino.getId(),new java.io.ByteArrayInputStream(antigo)).linhasImportadas()).isEqualTo(1);
        assertThat(loans.findAllByPerfilId(destino.getId()).get(0).getValorEmprestado()).isEqualByComparingTo("3751.50");
    }
    @Test void backupLegadoSemDistribuicaoDoParcialNaoCriaFinanceiroInconsistente() throws Exception {
        var f=fixture();emprestimos.pagarParcelaParcial(f.perfil().getId(),f.emprestimo().getId(),1,BigDecimal.TEN);
        var antigo=arquivoLegado(exportacao.exportarExcel(f.perfil().getId()));
        var destino=new Perfil();destino.setNome("Backup legado parcial");perfis.save(destino);
        var r=importacao.importarPlanilha(destino.getId(),new java.io.ByteArrayInputStream(antigo));
        assertThat(r.linhasImportadas()).isZero();assertThat(r.linhasIgnoradas()).isEqualTo(1);
        assertThat(r.erros().get(0).motivo()).contains("pagamentos parciais");
        assertThat(loans.findAllByPerfilId(destino.getId())).isEmpty();
    }
    private byte[] arquivoLegado(byte[] dados) throws Exception {
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(dados));
            var legado=new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet=legado.createSheet("Backup");
            for(var row:wb.getSheetAt(0)) {
                var destinoRow=sheet.createRow(row.getRowNum());
                for(int i=0;i<18;i++) {
                    var origem=row.getCell(i<4?i:i+1);var cell=destinoRow.createCell(i);
                    if(origem.getCellType()==org.apache.poi.ss.usermodel.CellType.STRING) cell.setCellValue(origem.getStringCellValue());
                    else if(origem.getCellType()==org.apache.poi.ss.usermodel.CellType.NUMERIC) cell.setCellValue(origem.getNumericCellValue());
                    var style=legado.createCellStyle();style.cloneStyleFrom(origem.getCellStyle());cell.setCellStyle(style);
                }
            }
            var out=new java.io.ByteArrayOutputStream();legado.write(out);return out.toByteArray();
        }
    }
    @Test void inadimplentesIncluiPendenteVencidaSemDependerDeOutraTela() {
        var f=fixture();f.emprestimo().getParcelas().get(0).setDataVencimento(LocalDate.now().minusDays(1));
        var r=relatorios.gerarRelatorioInadimplentes(f.perfil().getId());
        assertThat(r).hasSize(1);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(FrequenciaPagamento.class)
    void frequenciasMantemNumeroParcelasEFinanceiro(FrequenciaPagamento freq) {
        var f=fixture();var e=emprestimos.criarEmprestimo(f.perfil().getId(),f.devedor(),new BigDecimal("100.01"),3,new BigDecimal("2.5"),FormaPagamento.DINHEIRO,freq,TipoContrato.DIGITAL,f.fiador().getId());
        assertThat(e.getParcelas()).hasSize(3);
        assertThat(e.getValorTotalEmprestimo()).isEqualByComparingTo(e.getValorParcela().multiply(new BigDecimal("3")));
        emprestimos.pagarParcelaParcial(f.perfil().getId(),e.getId(),1,new BigDecimal("0.01"));
        assertThat(e.getValorRecebido()).isEqualByComparingTo("0.01");
        assertThat(e.getValorRecebido().add(e.getValorAReceber())).isEqualByComparingTo(e.getValorTotalEmprestimo());
    }
    @Test void downloadsDeContratoExcluidoRetornam404ComCors() throws Exception {
        var f=fixture();var c=documentos.gerar(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId());
        documentos.excluir(f.perfil().getId(),f.devedor().getId(),f.emprestimo().getId(),c.id());
        var base="/clientes/"+f.devedor().getId()+"/emprestimos/"+f.emprestimo().getId()+"/contratos/"+c.id();
        var mvc=MockMvcBuilders.webAppContextSetup(context).build();
        for(var formato:new String[]{"download","download-pdf","download-pdf-assinado"})
            mvc.perform(get(base+"/"+formato).header("X-Perfil-Id",f.perfil().getId()).header("Origin","http://localhost:5173"))
                    .andExpect(status().isNotFound()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
    }
}
