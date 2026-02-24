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

        for (Row row : sheet) {
            if (skipHeader) {
                skipHeader = false;
                continue;
            }

            if (row == null) continue;

            // ----------- CAMPOS OBRIGATÓRIOS -----------

            LocalDate dataEmprestimo = getLocalDate(row.getCell(0));
            if (dataEmprestimo == null) continue;

            String nomeClienteOriginal = getString(row.getCell(1));
            if (nomeClienteOriginal.isBlank()) continue;

            String nomeCliente = nomeClienteOriginal.split("\\(")[0].trim();

            String formaStr = getString(row.getCell(3));
            if (formaStr.isBlank()) continue;

            BigDecimal valorEmprestado = getMoneyValue(row.getCell(4));
            if (valorEmprestado.compareTo(BigDecimal.ZERO) <= 0) continue;

            Integer totalParcelas = 0;
            if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.NUMERIC) {
                totalParcelas = (int) row.getCell(5).getNumericCellValue();
            }
            if (totalParcelas <= 0) continue;

            BigDecimal valorParcela = getMoneyValue(row.getCell(12));
            if (valorParcela.compareTo(BigDecimal.ZERO) <= 0) continue;

            LocalDate inicioPgto = getLocalDate(row.getCell(14));
            if (inicioPgto == null) continue;

            // ----------- TRATAMENTO DO ENUM FORMA PAGAMENTO -----------

            FormaPagamento formaPagamento;
            try {
                // Tenta converter o que vem da planilha (ex: "PIX", "Dinheiro")
                // Se na planilha estiver "DINHEIRO", ele mapeia. Se estiver "Dinheiro", o toUpperCase() resolve.
                formaPagamento = FormaPagamento.valueOf(formaStr.toUpperCase().trim());
            } catch (Exception e) {
                // Se o que estiver na planilha não for PIX ou DINHEIRO (ex: "Mensal"),
                // definimos um valor padrão do seu Enum. Vou usar DINHEIRO como fallback.
                formaPagamento = FormaPagamento.DINHEIRO;
            }

            // ----------- CAMPOS OPCIONAIS -----------

            String nomeIndicador = getString(row.getCell(2));

            Integer parcelasPagas = 0;
            if (row.getCell(6) != null && row.getCell(6).getCellType() == CellType.NUMERIC) {
                parcelasPagas = (int) row.getCell(6).getNumericCellValue();
            }

            BigDecimal juros = getPercentValue(row.getCell(13));
            LocalDate fimPgto = getLocalDate(row.getCell(15));
            EmprestimoStatus situacao = converterStatus(getString(row.getCell(16)));

            // ----------- PERSISTÊNCIA -----------

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
            emp.setTaxaDeJuros(juros);
            emp.setValorParcela(valorParcela);
            emp.setInicioPagamento(inicioPgto);
            emp.setFinalPagamento(fimPgto);
            emp.setEmprestimoStatus(situacao);

            emp = emprestimoRepository.save(emp);

            List<Parcelas> parcelasGeradas = gerarParcelas(emp, parcelasPagas);
            parcelaRepository.saveAll(parcelasGeradas);
        }

        workbook.close();
    }

    // ----------------------- AUXILIARES (CONVERSORES) -----------------------

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
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String s = getString(cell).replace("%", "").replace(",", ".");
        try {
            double d = Double.parseDouble(s);
            return BigDecimal.valueOf(d > 1 ? d / 100 : d);
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
                return cell.getDateCellValue().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
            if (cell.getCellType() == CellType.STRING) {
                return LocalDate.parse(cell.getStringCellValue().trim());
            }
        } catch (Exception e) { return null; }
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