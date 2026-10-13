package com.caio.controle_clientes;

import com.caio.controle_clientes.services.importService.ImportacaoCellReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class ImportacaoNumericaAuditoriaTests {
    @ParameterizedTest @CsvSource(delimiter=';',value={"1234.56;1234.56","R$ 1.234,56;1234.56","1.234;1234","0.01;0.01"})
    void moedaTextualNaoPerdeCentavos(String entrada,String esperado) throws Exception {
        try(var wb=new XSSFWorkbook()) {
            var cell=wb.createSheet().createRow(0).createCell(0);cell.setCellValue(entrada);
            assertThat(new ImportacaoCellReader().getMoneyValue(cell,wb.getCreationHelper().createFormulaEvaluator(),new DataFormatter())).isEqualByComparingTo(esperado);
        }
    }
}
