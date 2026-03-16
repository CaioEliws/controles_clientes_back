package com.caio.controle_clientes.services.importService;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.services.importService.dto.LinhaImportacaoDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
@RequiredArgsConstructor
public class ImportacaoLinhaMapper {

    private final ImportacaoCellReader cellReader;

    private static final int COL_DATA_EMPRESTIMO = 0;
    private static final int COL_CLIENTE = 1;
    private static final int COL_INDICACAO = 2;
    private static final int COL_FORMA_PGTO = 3;
    private static final int COL_CONTRATO = 4;
    private static final int COL_PAGAMENTO_FREQUENCIA = 5;
    private static final int COL_VALOR_EMPRESTIMO = 6;
    private static final int COL_QTDE_PARCELAS = 7;
    private static final int COL_QTDE_PARCELAS_PAGAS = 8;
    private static final int COL_VALOR_A_RECEBER = 10;
    private static final int COL_VALOR_RECEBIDO = 11;
    private static final int COL_VALOR_TOTAL_EMPRESTIMOS = 13;
    private static final int COL_VALOR_PARCELA = 14;
    private static final int COL_TX_JUROS = 15;
    private static final int COL_INICIO_PGTO = 16;
    private static final int COL_FINAL_PGTO = 17;
    private static final int COL_SITUACAO = 18;

    public LinhaImportacaoDTO mapear(Row row, FormulaEvaluator evaluator, DataFormatter formatter) {
        String nomeClienteOriginal = cellReader.getString(row.getCell(COL_CLIENTE), evaluator, formatter);

        return new LinhaImportacaoDTO(
                cellReader.getLocalDate(row.getCell(COL_DATA_EMPRESTIMO), evaluator, formatter),
                nomeClienteOriginal,
                limparNomeCliente(nomeClienteOriginal),
                cellReader.getString(row.getCell(COL_INDICACAO), evaluator, formatter),
                converterFormaPagamento(cellReader.getString(row.getCell(COL_FORMA_PGTO), evaluator, formatter)),
                converterTipoContrato(cellReader.getString(row.getCell(COL_CONTRATO), evaluator, formatter)),
                converterFrequencia(cellReader.getString(row.getCell(COL_PAGAMENTO_FREQUENCIA), evaluator, formatter)),
                cellReader.getMoneyValue(row.getCell(COL_VALOR_EMPRESTIMO), evaluator, formatter),
                cellReader.getIntegerValue(row.getCell(COL_QTDE_PARCELAS), evaluator, formatter),
                cellReader.getIntegerValue(row.getCell(COL_QTDE_PARCELAS_PAGAS), evaluator, formatter),
                cellReader.getMoneyValue(row.getCell(COL_VALOR_A_RECEBER), evaluator, formatter),
                cellReader.getMoneyValue(row.getCell(COL_VALOR_RECEBIDO), evaluator, formatter),
                cellReader.getMoneyValue(row.getCell(COL_VALOR_TOTAL_EMPRESTIMOS), evaluator, formatter),
                cellReader.getMoneyValue(row.getCell(COL_VALOR_PARCELA), evaluator, formatter),
                cellReader.getPercentValue(row.getCell(COL_TX_JUROS), evaluator, formatter),
                cellReader.getLocalDate(row.getCell(COL_INICIO_PGTO), evaluator, formatter),
                cellReader.getLocalDate(row.getCell(COL_FINAL_PGTO), evaluator, formatter),
                converterStatus(cellReader.getString(row.getCell(COL_SITUACAO), evaluator, formatter))
        );
    }

    public boolean isRowEmpty(Row row, FormulaEvaluator evaluator, DataFormatter formatter) {
        for (int i = 0; i <= COL_SITUACAO; i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                String value = formatter.formatCellValue(cell, evaluator);
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String limparNomeCliente(String nomeClienteOriginal) {
        if (nomeClienteOriginal == null || nomeClienteOriginal.isBlank()) {
            return "";
        }
        return nomeClienteOriginal.split("\\(")[0].trim();
    }

    private FrequenciaPagamento converterFrequencia(String raw) {
        if (raw == null || raw.isBlank()) return FrequenciaPagamento.MENSAL;

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase();

        if (normalized.startsWith("DIA")) return FrequenciaPagamento.DIARIO;
        if (normalized.startsWith("SEM")) return FrequenciaPagamento.SEMANAL;
        if (normalized.startsWith("QUI")) return FrequenciaPagamento.QUINZENAL;
        if (normalized.startsWith("MEN") || normalized.startsWith("MES")) return FrequenciaPagamento.MENSAL;

        return FrequenciaPagamento.MENSAL;
    }

    private TipoContrato converterTipoContrato(String raw) {
        if (raw == null || raw.isBlank()) return TipoContrato.FISICO;

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase();

        if (normalized.startsWith("DIG")) return TipoContrato.DIGITAL;
        if (normalized.startsWith("FIS")) return TipoContrato.FISICO;

        return TipoContrato.FISICO;
    }

    private FormaPagamento converterFormaPagamento(String raw) {
        if (raw == null || raw.isBlank()) return FormaPagamento.DINHEIRO;

        try {
            return FormaPagamento.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return FormaPagamento.DINHEIRO;
        }
    }

    private EmprestimoStatus converterStatus(String s) {
        if (s == null || s.isBlank()) return EmprestimoStatus.EM_ABERTO;

        return switch (s.toUpperCase()) {
            case "QUITADO" -> EmprestimoStatus.QUITADO;
            case "REFINANCIADO" -> EmprestimoStatus.REFINANCIADO;
            case "QUITADO TERCEIRO", "QUITADO_TERCEIRO" -> EmprestimoStatus.QUITADO_TERCEIRO;
            default -> EmprestimoStatus.EM_ABERTO;
        };
    }
}