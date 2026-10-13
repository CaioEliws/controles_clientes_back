package com.caio.controle_clientes.services.importService;

import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.Parcelas;
import com.caio.controle_clientes.models.Perfil;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import com.caio.controle_clientes.services.importService.dto.ErroImportacao;
import com.caio.controle_clientes.services.importService.dto.ImportacaoResultado;
import com.caio.controle_clientes.services.importService.dto.LinhaImportacaoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImportacaoPlanilhaService {

    private final ClienteRepositorio clienteRepository;
    private final EmprestimoRepositorio emprestimoRepository;
    private final ParcelaRepositorio parcelaRepository;
    private final ImportacaoLinhaMapper linhaMapper;
    private final ImportacaoValidator validator;
    private final ImportacaoParcelaFactory parcelaFactory;
    private final tools.jackson.databind.ObjectMapper json;

    @Transactional
    public ImportacaoResultado importar(Long perfilId, InputStream file) throws Exception {

        List<ErroImportacao> erros = new ArrayList<>();
        Map<String, Cliente> cacheClientes = new HashMap<>();

        int totalLinhas = 0;
        int linhasImportadas = 0;
        int linhasIgnoradas = 0;
        int linhasDuplicadas = 0;

        try (Workbook workbook = WorkbookFactory.create(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter();

            boolean skipHeader = true;

            for (Row row : sheet) {

                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }

                if (row == null || linhaMapper.isRowEmpty(row, evaluator, formatter)) {
                    continue;
                }

                totalLinhas++;

                try {
                    LinhaImportacaoDTO dto = linhaMapper.mapear(row, evaluator, formatter);

                    String erroValidacao = validator.validar(dto);

                    if (erroValidacao != null) {
                        linhasIgnoradas++;
                        erros.add(new ErroImportacao(row.getRowNum() + 1, erroValidacao));

                        log.warn(
                                "Linha {} ignorada - {} | cliente='{}' | dataEmprestimo='{}' | valorEmprestado='{}' | inicioPgto='{}' | fimPgto='{}'",
                                row.getRowNum() + 1,
                                erroValidacao,
                                dto.nomeClienteOriginal(),
                                dto.dataEmprestimo(),
                                dto.valorEmprestado(),
                                dto.inicioPgto(),
                                dto.fimPgto()
                        );
                        continue;
                    }

                    List<ImportacaoParcelaFactory.DetalheBackup> detalhes = lerDetalhes(row, dto);
                    Cliente cliente = obterOuCriarCliente(
                            perfilId,
                            dto.nomeCliente(),
                            dto.nomeIndicador(),
                            cacheClientes
                    );

                    if (emprestimoJaExiste(perfilId, cliente, dto)) {
                        linhasDuplicadas++;
                        String motivo = "Empréstimo duplicado para cliente/data/valor/início/parcelas";
                        erros.add(new ErroImportacao(row.getRowNum() + 1, motivo));

                        log.warn(
                                "Linha {} duplicada - {} | cliente='{}' | clienteId='{}' | dataEmprestimo='{}' | valorEmprestado='{}' | inicioPgto='{}' | fimPgto='{}' | totalParcelas='{}'",
                                row.getRowNum() + 1,
                                motivo,
                                dto.nomeClienteOriginal(),
                                cliente.getId(),
                                dto.dataEmprestimo(),
                                dto.valorEmprestado(),
                                dto.inicioPgto(),
                                dto.fimPgto(),
                                dto.totalParcelas()
                        );
                        continue;
                    }

                    Emprestimo emprestimo = criarEmprestimo(perfilId, cliente, dto);
                    Emprestimo emprestimoSalvo = emprestimoRepository.save(emprestimo);

                    List<Parcelas> parcelas = detalhes == null
                            ? parcelaFactory.gerar(emprestimoSalvo, dto.parcelasPagas())
                            : parcelaFactory.restaurar(emprestimoSalvo, detalhes);

                    for (Parcelas parcela : parcelas) {
                        parcela.setPerfil(emprestimoSalvo.getPerfil());
                        parcela.setEmprestimo(emprestimoSalvo);
                    }

                    parcelaRepository.saveAll(parcelas);
                    emprestimoSalvo.setParcelas(parcelas);

                    linhasImportadas++;

                } catch (Exception e) {
                    // Uma falha de persistência invalida a transação inteira; nunca informar sucesso parcial.
                    if (e instanceof org.springframework.dao.DataAccessException) throw e;
                    linhasIgnoradas++;
                    String motivo = e instanceof IllegalArgumentException
                            ? "Linha inválida: " + e.getMessage() : "Não foi possível importar esta linha.";
                    erros.add(new ErroImportacao(row.getRowNum() + 1, motivo));

                    log.error("Falha na linha {}: {}", row.getRowNum() + 1, e.getClass().getSimpleName());
                }
            }
        }

        ImportacaoResultado resultado = new ImportacaoResultado(
                totalLinhas,
                linhasImportadas,
                linhasIgnoradas,
                linhasDuplicadas,
                erros
        );

        log.info("========== RESUMO IMPORTAÇÃO ==========");
        log.info("Total linhas lidas: {}", resultado.totalLinhas());
        log.info("Linhas importadas: {}", resultado.linhasImportadas());
        log.info("Linhas ignoradas: {}", resultado.linhasIgnoradas());
        log.info("Linhas duplicadas: {}", resultado.linhasDuplicadas());
        log.info("Total de erros: {}", resultado.erros().size());
        log.info("=======================================");

        return resultado;
    }

    private Cliente obterOuCriarCliente(Long perfilId,
                                        String nomeCliente,
                                        String nomeIndicador,
                                        Map<String, Cliente> cacheClientes) {

        String chave = normalizarChave(nomeCliente);

        if (cacheClientes.containsKey(chave)) {
            return cacheClientes.get(chave);
        }

        Cliente cliente = clienteRepository
                .findByNomeIgnoreCaseAndPerfilId(nomeCliente, perfilId)
                .orElseGet(() -> {
                    Perfil perfil = new Perfil();
                    perfil.setId(perfilId);

                    Cliente novo = new Cliente();
                    novo.setPerfil(perfil);
                    novo.setNome(nomeCliente);
                    novo.setNomeIndicador(nomeIndicador);

                    return clienteRepository.save(novo);
                });

        cacheClientes.put(chave, cliente);
        return cliente;
    }

    private boolean emprestimoJaExiste(Long perfilId,
                                       Cliente cliente,
                                       LinhaImportacaoDTO dto) {

        if (cliente.getId() == null) {
            return false;
        }

        return emprestimoRepository
                .existsByClienteIdAndPerfilIdAndDataDoEmprestimoAndValorEmprestadoAndInicioPagamentoAndQuantidadeParcelas(
                        cliente.getId(),
                        perfilId,
                        dto.dataEmprestimo(),
                        dto.valorEmprestado(),
                        dto.inicioPgto(),
                        dto.totalParcelas()
                );
    }

    private Emprestimo criarEmprestimo(Long perfilId,
                                       Cliente cliente,
                                       LinhaImportacaoDTO dto) {

        Perfil perfil = new Perfil();
        perfil.setId(perfilId);

        Emprestimo emp = new Emprestimo();
        emp.setPerfil(perfil);
        emp.setCliente(cliente);
        emp.setDataDoEmprestimo(dto.dataEmprestimo());
        emp.setFormaPagamento(dto.formaPagamento());
        emp.setFrequenciaPagamento(dto.frequenciaPagamento());
        emp.setTipoContrato(dto.tipoContrato());
        emp.setValorEmprestado(dto.valorEmprestado());
        emp.setQuantidadeParcelas(dto.totalParcelas());
        emp.setValorAReceber(dto.valorAReceber());
        emp.setValorRecebido(dto.valorRecebido());
        emp.setValorTotalEmprestimo(dto.valorTotalEmprestimos());
        emp.setTaxaDeJuros(dto.taxaJurosPercentual());
        emp.setValorParcela(dto.valorParcela());
        emp.setInicioPagamento(dto.inicioPgto());
        emp.setFinalPagamento(dto.fimPgto());
        emp.setEmprestimoStatus(dto.situacao());

        return emp;
    }

    private String normalizarChave(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase();
    }

    private List<ImportacaoParcelaFactory.DetalheBackup> lerDetalhes(Row row, LinhaImportacaoDTO dto) {
        Cell cell = row.getCell(19);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            var recebidoIdentificado = dto.valorParcela().multiply(java.math.BigDecimal.valueOf(dto.parcelasPagas()));
            if (dto.valorRecebido() != null && recebidoIdentificado.compareTo(dto.valorRecebido()) != 0) {
                throw new IllegalArgumentException("Backup antigo não identifica a distribuição dos pagamentos parciais. Exporte novamente com os detalhes das parcelas.");
            }
            return null;
        }
        var detalhes = json.readValue(cell.getStringCellValue(),
                new tools.jackson.core.type.TypeReference<List<ImportacaoParcelaFactory.DetalheBackup>>() {});
        if (detalhes.size() != dto.totalParcelas()) throw new IllegalArgumentException("Quantidade de detalhes das parcelas inválida.");
        var numeros = new java.util.HashSet<Integer>();
        for (var d : detalhes) {
            if (!numeros.add(d.numero()) || d.numero() < 1 || d.numero() > dto.totalParcelas()
                    || d.valor() == null || d.valor().signum() <= 0 || d.pago() == null
                    || d.pago().signum() < 0 || d.pago().compareTo(d.valor()) > 0 || d.vencimento() == null) {
                throw new IllegalArgumentException("Detalhes das parcelas inválidos.");
            }
        }
        var total = detalhes.stream().map(ImportacaoParcelaFactory.DetalheBackup::valor).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var recebido = detalhes.stream().map(ImportacaoParcelaFactory.DetalheBackup::pago).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (total.compareTo(dto.valorTotalEmprestimos()) != 0 || recebido.compareTo(dto.valorRecebido()) != 0
                || total.subtract(recebido).compareTo(dto.valorAReceber()) != 0) {
            throw new IllegalArgumentException("Totais do backup não correspondem às parcelas.");
        }
        return detalhes;
    }
}
