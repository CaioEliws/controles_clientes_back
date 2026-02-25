package com.caio.controle_clientes.services;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final ClienteRepositorio clienteRepository;
    private final EmprestimoRepositorio emprestimoRepository;
    private final ParcelaRepositorio parcelaRepository;

    public void importarPlanilha(InputStream file) throws Exception {

        Workbook workbook = WorkbookFactory.create(file);
        Sheet sheet = workbook.getSheetAt(0);

        boolean skipHeader = true;

        int totalLinhas = 0;
        int linhasImportadas = 0;
        int linhasIgnoradas = 0;

        for (Row row : sheet) {

            if (skipHeader) {
                skipHeader = false;
                continue;
            }

            if (row == null) continue;

            totalLinhas++;

            try {
                LocalDate dataEmprestimo = getLocalDate(row.getCell(0));
                String nomeClienteOriginal = getString(row.getCell(1));
                String nomeIndicador = getString(row.getCell(2));
                String formaStr = getString(row.getCell(3));

                BigDecimal valorEmprestado = getMoneyValue(row.getCell(4));
                Integer totalParcelas = (int) row.getCell(5).getNumericCellValue();
                Integer parcelasPagas = (int) row.getCell(6).getNumericCellValue();

                BigDecimal valorAReceber = getMoneyValue(row.getCell(8));
                BigDecimal valorRecebido = getMoneyValue(row.getCell(9));
                BigDecimal valorTotalEmprestimos = getMoneyValue(row.getCell(11));

                BigDecimal valorParcela = getMoneyValue(row.getCell(12));
                BigDecimal taxaJurosPercentual = getPercentValue(row.getCell(13));

                LocalDate inicioPgto = getLocalDate(row.getCell(14));
                LocalDate fimPgto = getLocalDate(row.getCell(15));
                EmprestimoStatus situacao = converterStatus(getString(row.getCell(16)));

                String motivo = null;

                if (dataEmprestimo == null) motivo = "Data do empréstimo inválida";
                else if (nomeClienteOriginal.isBlank()) motivo = "Nome cliente vazio";
                else if (formaStr.isBlank()) motivo = "Forma pagamento vazia";
                else if (valorEmprestado.compareTo(BigDecimal.ZERO) <= 0) motivo = "Valor emprestado inválido";
                else if (totalParcelas <= 0) motivo = "Total parcelas inválido";
                else if (valorParcela.compareTo(BigDecimal.ZERO) <= 0) motivo = "Valor parcela inválido";
                else if (inicioPgto == null) motivo = "Data início pagamento inválida";

                if (motivo != null) {
                    linhasIgnoradas++;
                    System.out.println("❌ Linha ignorada " + row.getRowNum() + " - " + motivo);
                    continue;
                }

                String nomeCliente = nomeClienteOriginal.split("\\(")[0].trim();

                FormaPagamento formaPagamento;
                try {
                    formaPagamento = FormaPagamento.valueOf(formaStr.toUpperCase().trim());
                } catch (Exception e) {
                    formaPagamento = FormaPagamento.DINHEIRO;
                }

                Cliente cliente = clienteRepository.findByNomeIgnoreCase(nomeCliente)
                        .orElseGet(() -> {
                            Cliente c = new Cliente();
                            c.setNome(nomeCliente);
                            c.setNomeIndicador(nomeIndicador);
                            return clienteRepository.save(c);
                        });

                Emprestimo emp = new Emprestimo();
                emp.setCliente(cliente);
                emp.setDataDoEmprestimo(dataEmprestimo);
                emp.setFormaPagamento(formaPagamento);
                emp.setValorEmprestado(valorEmprestado);
                emp.setQuantidadeParcelas(totalParcelas);

                emp.setValorAReceber(valorAReceber);
                emp.setValorRecebido(valorRecebido);
                emp.setValorTotalEmprestimo(valorTotalEmprestimos);

                emp.setTaxaDeJuros(taxaJurosPercentual);
                emp.setValorParcela(valorParcela);
                emp.setInicioPagamento(inicioPgto);
                emp.setFinalPagamento(fimPgto);
                emp.setEmprestimoStatus(situacao);

                emp = emprestimoRepository.save(emp);

                List<Parcelas> parcelasGeradas = gerarParcelas(emp, parcelasPagas);
                parcelaRepository.saveAll(parcelasGeradas);

                linhasImportadas++;

            } catch (Exception e) {
                linhasIgnoradas++;
                System.out.println("💥 Erro inesperado na linha " + row.getRowNum() + ": " + e.getMessage());
            }
        }

        workbook.close();

        System.out.println("\n========== RESUMO IMPORTAÇÃO ==========");
        System.out.println("Total linhas lidas: " + totalLinhas);
        System.out.println("Linhas importadas: " + linhasImportadas);
        System.out.println("Linhas ignoradas: " + linhasIgnoradas);
        System.out.println("=======================================\n");
    }

    private BigDecimal getMoneyValue(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;

        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }

        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return BigDecimal.ZERO;

            try {
                String clean = s.replace("R$", "")
                        .replaceAll("\\s", "")
                        .replace(".", "")
                        .replace(",", ".");
                return new BigDecimal(clean);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal getPercentValue(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;

        try {
            double value;

            if (cell.getCellType() == CellType.NUMERIC) {
                value = cell.getNumericCellValue();
            } else {
                String raw = cell.getStringCellValue()
                        .replace("%", "")
                        .replace(",", ".")
                        .trim();

                value = Double.parseDouble(raw);
            }

            if (value > 0 && value < 1) {
                value = value * 100;
            }

            return BigDecimal.valueOf(Math.round(value));

        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String getString(Cell cell) {
        if (cell == null) return "";

        if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == (long) val) return String.format("%d", (long) val);
            return String.valueOf(val);
        }

        return cell.getStringCellValue().trim();
    }

    private LocalDate getLocalDate(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }

            if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();

                if (value.contains("/")) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    return LocalDate.parse(value, formatter);
                }

                return LocalDate.parse(value);
            }

        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private List<Parcelas> gerarParcelas(Emprestimo emp, Integer pagas) {
        List<Parcelas> list = new ArrayList<>();

        for (int i = 1; i <= emp.getQuantidadeParcelas(); i++) {

            Parcelas p = new Parcelas();
            p.setNumeroParcela(i);
            p.setEmprestimo(emp);
            p.setValorParcela(emp.getValorParcela());

            LocalDate vencimento = emp.getInicioPagamento().plusMonths(i - 1);
            p.setDataVencimento(vencimento);

            if (i <= pagas) {
                p.setStatus(ParcelaStatus.PAGO);
                p.setValorPago(emp.getValorParcela());
                p.setDataPagamento(vencimento);
            } else {
                p.setStatus(ParcelaStatus.PENDENTE);
            }

            list.add(p);
        }

        return list;
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