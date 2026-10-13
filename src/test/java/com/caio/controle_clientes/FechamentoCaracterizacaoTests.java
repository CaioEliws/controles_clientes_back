package com.caio.controle_clientes;

import com.caio.controle_clientes.services.emprestimo.CalculadoraEmprestimoService;
import com.caio.controle_clientes.services.importService.ImportacaoCellReader;
import com.caio.controle_clientes.models.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class FechamentoCaracterizacaoTests {
    @Test void cemDivididoPorTresMantemResiduoAtualSemAlterarRegra() {
        var e=new Emprestimo();e.setValorEmprestado(new BigDecimal("100.00"));
        e.setTaxaDeJuros(BigDecimal.ZERO);e.setQuantidadeParcelas(3);e.setFrequenciaPagamento(FrequenciaPagamento.MENSAL);
        var parcelas=new CalculadoraEmprestimoService().gerarParcelas(e);
        assertEquals(3,parcelas.size());
        for(var p:parcelas)assertEquals(new BigDecimal("33.33"),p.getValorParcela());
        var soma=parcelas.stream().map(Parcelas::getValorParcela).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(new BigDecimal("99.99"),soma);
        assertEquals(new BigDecimal("0.01"),e.getValorEmprestado().subtract(soma));
    }
    @Test void documentaDiferencaEntreTaxaNumericaTextoEFormatoPercentual() throws Exception {
        try(var wb=new XSSFWorkbook()){
            var row=wb.createSheet().createRow(0);var num=row.createCell(0);num.setCellValue(0.5);
            var text=row.createCell(1);text.setCellValue("0,5");var percent=row.createCell(2);percent.setCellValue(0.005);
            var style=wb.createCellStyle();style.setDataFormat(wb.createDataFormat().getFormat("0.0%"));percent.setCellStyle(style);
            var reader=new ImportacaoCellReader();var evaluator=wb.getCreationHelper().createFormulaEvaluator();var formatter=new DataFormatter();
            assertEquals(0,new BigDecimal("50").compareTo(reader.getPercentValue(num,evaluator,formatter)));
            assertEquals(0,new BigDecimal("0.5").compareTo(reader.getPercentValue(text,evaluator,formatter)));
            assertEquals(0,new BigDecimal("0.5").compareTo(reader.getPercentValue(percent,evaluator,formatter)));
        }
    }
}
