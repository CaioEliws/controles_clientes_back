package com.caio.controle_clientes.services.importService;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class ImportacaoCellReader {

    private static final DateTimeFormatter BR_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public BigDecimal getMoneyValue(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (cell == null) return BigDecimal.ZERO;

        try {
            CellValue evaluated = evaluator.evaluate(cell);

            if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(evaluated.getNumberValue());
            }

            String s = formatter.formatCellValue(cell, evaluator);
            if (s == null || s.isBlank()) return BigDecimal.ZERO;

            String clean = s.replace("R$", "")
                    .replaceAll("\\s", "")
                    .replace(".", "")
                    .replace(",", ".");

            return new BigDecimal(clean);
        } catch (Exception e) {
            log.debug("Erro ao converter valor monetário da célula: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getPercentValue(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (cell == null) return BigDecimal.ZERO;

        try {
            CellValue evaluated = evaluator.evaluate(cell);
            double value;

            if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                value = evaluated.getNumberValue();
            } else {
                String raw = formatter.formatCellValue(cell, evaluator)
                        .replace("%", "")
                        .replace(",", ".")
                        .trim();

                if (raw.isBlank()) return BigDecimal.ZERO;
                value = Double.parseDouble(raw);
            }

            if (value > 0 && value < 1) {
                value = value * 100;
            }

            return BigDecimal.valueOf(Math.round(value));
        } catch (Exception e) {
            log.debug("Erro ao converter percentual da célula: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public String getString(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    public Integer getIntegerValue(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (cell == null) return 0;

        try {
            CellValue evaluated = evaluator.evaluate(cell);

            if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                return (int) Math.round(evaluated.getNumberValue());
            }

            String value = formatter.formatCellValue(cell, evaluator).trim();
            if (value.isBlank()) return 0;

            value = value.replace(".", "").replace(",", ".");
            return (int) Math.round(Double.parseDouble(value));
        } catch (Exception e) {
            log.debug("Erro ao converter inteiro da célula: {}", e.getMessage());
            return 0;
        }
    }

    public LocalDate getLocalDate(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }

            if (cell.getCellType() == CellType.FORMULA) {
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                    return DateUtil.getLocalDateTime(evaluated.getNumberValue()).toLocalDate();
                }
            }

            String value = formatter.formatCellValue(cell, evaluator).trim();
            if (value.isBlank()) return null;

            if (value.contains("/")) {
                return LocalDate.parse(value, BR_DATE_FORMAT);
            }

            return LocalDate.parse(value);
        } catch (Exception e) {
            log.debug("Erro ao converter data da célula: {}", e.getMessage());
            return null;
        }
    }
}
