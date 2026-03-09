package com.caio.controle_clientes.services;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final EmprestimoRepositorio emprestimoRepositorio;

    public byte[] exportarExcel() throws Exception {
        List<Emprestimo> emprestimos = emprestimoRepositorio.findAll();

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Backup");

            String[] headers = new String[]{
                    "Data empréstimo",
                    "Cliente",
                    "Indicação",
                    "Forma Pgto",
                    "Pagamento",
                    "Valor empréstimo",
                    "Qtde parcelas",
                    "Qtde parcelas  pagas",
                    "Qtde parcelas a vencer",
                    "Valor a receber",
                    "Valor Recebido",
                    "Juros cobrado",
                    "Valor total empréstimos",
                    "Valor parcela",
                    "Tx juros",
                    "Inicio pgto",
                    "Final pgto",
                    "Situação do empréstimo"
            };

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(wb);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            CellStyle moneyStyle = createMoneyStyle(wb);
            CellStyle percentStyle = createPercentStyle(wb);
            CellStyle dateStyle = createDateStyle(wb);

            emprestimos.sort(Comparator.comparing(Emprestimo::getDataDoEmprestimo,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            int rowIdx = 1;

            for (Emprestimo e : emprestimos) {
                Cliente cliente = e.getCliente();

                int totalParcelas = safeInt(e.getQuantidadeParcelas());

                List<Parcelas> parcelas = e.getParcelas();
                long pagas = parcelas == null ? 0 :
                        parcelas.stream().filter(p -> p.getStatus() == ParcelaStatus.PAGO).count();

                long aVencer = Math.max(0, totalParcelas - pagas);

                BigDecimal valorRecebido = parcelas == null ? BigDecimal.ZERO :
                        parcelas.stream()
                                .map(p -> safeBd(p.getValorPago()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal valorAReceber = parcelas == null ? safeBd(e.getValorAReceber()) :
                        parcelas.stream()
                                .map(p -> {
                                    BigDecimal vp = safeBd(p.getValorParcela());
                                    BigDecimal pago = safeBd(p.getValorPago());
                                    BigDecimal saldo = vp.subtract(pago);
                                    return saldo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : saldo;
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                Row r = sheet.createRow(rowIdx++);

                setDate(r, 0, e.getDataDoEmprestimo(), dateStyle);

                r.createCell(1).setCellValue(cliente != null ? safeStr(cliente.getNome()) : "");

                r.createCell(2).setCellValue(cliente != null ? safeStr(cliente.getNomeIndicador()) : "");

                r.createCell(3).setCellValue(e.getFormaPagamento() != null ? e.getFormaPagamento().name() : "");

                r.createCell(4).setCellValue(e.getFrequenciaPagamento() != null
                        ? toLabelFrequencia(e.getFrequenciaPagamento())
                        : "Mensal");

                setMoney(r, 5, safeBd(e.getValorEmprestado()), moneyStyle);

                r.createCell(6).setCellValue(totalParcelas);

                r.createCell(7).setCellValue((double) pagas);

                r.createCell(8).setCellValue((double) aVencer);

                setMoney(r, 9, valorAReceber, moneyStyle);

                setMoney(r, 10, valorRecebido, moneyStyle);

                BigDecimal taxa = safeBd(e.getTaxaDeJuros());

                double taxaPercentual = taxa
                        .divide(BigDecimal.valueOf(100))
                        .doubleValue();

                Cell jurosCell = r.createCell(11);
                jurosCell.setCellValue(taxaPercentual);
                jurosCell.setCellStyle(percentStyle);

                setMoney(r, 12, safeBd(e.getValorTotalEmprestimo()), moneyStyle);

                setMoney(r, 13, safeBd(e.getValorParcela()), moneyStyle);

                Cell txCell = r.createCell(14);
                txCell.setCellValue(taxaPercentual);
                txCell.setCellStyle(percentStyle);

                setDate(r, 15, e.getInicioPagamento(), dateStyle);

                setDate(r, 16, e.getFinalPagamento(), dateStyle);

                r.createCell(17).setCellValue(e.getEmprestimoStatus() != null ? e.getEmprestimoStatus().name() : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String toLabelFrequencia(FrequenciaPagamento f) {
        return switch (f) {
            case QUINZENAL -> "Quinzenal";
            case MENSAL -> "Mensal";
            case SEMANAL -> "Semanal";
            case DIARIO -> "Diário";
        };
    }

    private static void setMoney(Row r, int col, BigDecimal value, CellStyle style) {
        Cell c = r.createCell(col);
        c.setCellValue(value.doubleValue());
        c.setCellStyle(style);
    }

    private static void setDate(Row r, int col, LocalDate date, CellStyle style) {
        Cell c = r.createCell(col);
        if (date == null) return;
        c.setCellValue(Date.valueOf(date));
        c.setCellStyle(style);
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);

        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createMoneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("\"R$\" #,##0.00"));
        return style;
    }

    private static CellStyle createDateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        return style;
    }

    private static BigDecimal safeBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private static String safeStr(String s) {
        return s == null ? "" : s;
    }

    private static CellStyle createPercentStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        return style;
    }
}