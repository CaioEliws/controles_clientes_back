package com.caio.controle_clientes;

import com.caio.controle_clientes.dto.Perfil.AtualizarPerfilDadosDTO;
import com.caio.controle_clientes.dto.Perfil.CriarPerfilRequest;
import com.caio.controle_clientes.dto.Perfil.PerfilDetalhadoDTO;
import com.caio.controle_clientes.dto.cliente.AtualizarClienteDadosDTO;
import com.caio.controle_clientes.dto.cliente.ClienteCreateDTO;
import com.caio.controle_clientes.dto.cliente.ClienteDetalhadoDTO;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.services.parcela.ParcelaObservacaoService;
import com.caio.controle_clientes.services.parcela.ParcelaService;
import com.caio.controle_clientes.services.perfil.PerfilService;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class CredorDevedorFiadorIntegrationTests {

    @Autowired ClienteService clientes;
    @Autowired PerfilService perfis;
    @Autowired EmprestimoService emprestimos;
    @Autowired ParcelaService parcelas;
    @Autowired ParcelaObservacaoService observacoes;
    @Autowired RelatorioService relatorios;
    @Autowired ImportService importador;
    @Autowired ExportService exportador;
    @Autowired ClienteRepositorio clienteRepository;
    @Autowired PerfilRepositorio perfilRepository;
    @Autowired EmprestimoRepositorio emprestimoRepository;
    @Autowired ParcelaRepositorio parcelaRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persisteERecuperaTodosOsDadosDoClienteSemPerderZerosOuMascara() {
        Perfil perfil = perfil("Carteira");
        ClienteCreateDTO dto = clienteCompleto("Pessoa", "001.234.567-89");
        clientes.criarCliente(perfil.getId(), dto);
        Long id = clientes.buscarPorNome(perfil.getId(), dto.nome()).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(clientes.buscarDetalhado(perfil.getId(), id)).isEqualTo(
                new ClienteDetalhadoDTO(id, "Pessoa", "Indicador", "Brasileira",
                        EstadoCivil.CASADO, "Profissão", "00.123.456-X", "SSP/SP",
                        "001.234.567-89", "Rua", 100, "Apto 2", "Bairro",
                        "00100-000", "Cidade", "SP"));
    }

    @Test
    void persistePerfilCompletoMantendoNomeCurtoECriacaoAntiga() {
        Perfil perfil = perfil(" Nome curto ");
        assertThat(perfil.getNome()).isEqualTo("Nome curto");
        assertThat(perfil.getCpf()).isNull();
        AtualizarPerfilDadosDTO dto = new AtualizarPerfilDadosDTO("Nome curto", "Nome jurídico",
                "Brasileira", EstadoCivil.UNIAO_ESTAVEL, "Profissão", "00123-X", "SSP/SP",
                "001.234.567-89", "Rua", 100, "Sala 1", "Bairro", "00100-000", "Cidade", "SP");
        perfis.atualizarDados(perfil.getId(), perfil.getId(), dto);
        entityManager.flush();
        entityManager.clear();

        assertThat(perfis.buscarDetalhado(perfil.getId(), perfil.getId())).isEqualTo(
                new PerfilDetalhadoDTO(perfil.getId(), "Nome curto", "Nome jurídico", "Brasileira",
                        EstadoCivil.UNIAO_ESTAVEL, "Profissão", "00123-X", "SSP/SP", "001.234.567-89",
                        "Rua", 100, "Sala 1", "Bairro", "00100-000", "Cidade", "SP"));
        assertThat(perfis.listarTodos()).extracting("nome").contains("Nome curto");
    }

    @Test
    void atualizaClienteCompletoSemModificarPerfilOuDataOriginal() {
        Perfil perfil = perfil("Carteira");
        Cliente cliente = cliente(perfil, "Antigo");
        LocalDate criadoEm = cliente.getCriadoEm();
        clientes.atualizarDados(perfil.getId(), cliente.getId(), dadosCliente("Atualizado", "00012345678"));
        entityManager.flush();
        entityManager.clear();
        Cliente recuperado = clientes.getClienteById(perfil.getId(), cliente.getId());
        assertThat(recuperado.getCpf()).isEqualTo("00012345678");
        assertThat(recuperado.getEstadoCivil()).isEqualTo(EstadoCivil.CASADO);
        assertThat(recuperado.getPerfil().getId()).isEqualTo(perfil.getId());
        assertThat(recuperado.getCriadoEm()).isEqualTo(criadoEm);
    }

    @Test
    void rejeitaCpfDuplicadoComMascaraDiferenteNoMesmoPerfil() {
        Perfil perfil = perfil("Carteira");
        clientes.criarCliente(perfil.getId(), clienteCompleto("Um", "001.234.567-89"));
        assertThatThrownBy(() -> clientes.criarCliente(perfil.getId(), clienteCompleto("Dois", "00123456789")))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("CPF");
        assertThat(clienteRepository.findAllByPerfilId(perfil.getId())).hasSize(1);
    }

    @Test
    void permiteCpfEmOutroPerfilEVariosClientesSemCpf() {
        Perfil primeiro = perfil("Primeira");
        Perfil segundo = perfil("Segunda");
        clientes.criarCliente(primeiro.getId(), clienteCompleto("Um", "00123456789"));
        clientes.criarCliente(segundo.getId(), clienteCompleto("Dois", "001.234.567-89"));
        cliente(primeiro, "Legado um");
        cliente(primeiro, "Legado dois");
        assertThat(clienteRepository.findAllByPerfilId(primeiro.getId())).hasSize(3);
        assertThat(clientes.buscarPorNome(primeiro.getId(), "Legado um").getCpf()).isNull();
    }

    @Test
    void atualizacaoPermiteProprioCpfMasRejeitaCpfDeOutroCliente() {
        Perfil perfil = perfil("Carteira");
        clientes.criarCliente(perfil.getId(), clienteCompleto("Um", "001.234.567-89"));
        Cliente um = clientes.buscarPorNome(perfil.getId(), "Um");
        clientes.atualizarDados(perfil.getId(), um.getId(), dadosCliente("Um", "00123456789"));
        Cliente dois = cliente(perfil, "Dois");
        assertThatThrownBy(() -> clientes.atualizarDados(perfil.getId(), dois.getId(), dadosCliente("Dois", "001.234.567-89")))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(dois.getCpf()).isNull();
    }

    @Test
    void assinaturaAntigaTambemRejeitaCriacaoSemFiador() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        assertThatThrownBy(() -> emprestimos.criarEmprestimo(perfil.getId(), devedor,
                new BigDecimal("600"), 3, BigDecimal.ZERO, FormaPagamento.DINHEIRO,
                FrequenciaPagamento.MENSAL, TipoContrato.FISICO))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Fiador é obrigatório.");
        assertThat(emprestimoRepository.findAllByPerfilId(perfil.getId())).isEmpty();
        assertThat(parcelaRepository.count()).isZero();
    }

    @Test
    void associaFiadorDoMesmoPerfilSemTransformaLoEmDevedorNosRelatorios() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        Cliente fiador = cliente(perfil, "Fiador");
        Emprestimo emp = emprestimo(perfil, devedor, fiador.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(emprestimoRepository.findById(emp.getId()).orElseThrow().getFiador().getId()).isEqualTo(fiador.getId());
        devedor = clientes.getClienteById(perfil.getId(), devedor.getId());
        fiador = clientes.getClienteById(perfil.getId(), fiador.getId());
        assertThat(emprestimos.listarDetalhado(perfil.getId(), devedor)).singleElement().satisfies(d -> {
            assertThat(d.fiadorId()).isEqualTo(emp.getFiador().getId());
            assertThat(d.fiadorNome()).isEqualTo("Fiador");
        });
        assertThat(emprestimos.listarEmprestimosCliente(perfil.getId(), fiador)).isEmpty();
        assertThat(relatorios.gerarHistoricoCliente(perfil.getId(), fiador).totalEmprestimos()).isZero();
        assertThat(relatorios.gerarHistoricoCliente(perfil.getId(), devedor).totalEmprestado()).isEqualByComparingTo("600");
    }

    @Test
    void permiteMesmoClienteComoDevedorEFiadorESuaAtuacaoEmMaisDeUmContrato() {
        Perfil perfil = perfil("Carteira");
        Cliente a = cliente(perfil, "A");
        Cliente b = cliente(perfil, "B");
        emprestimo(perfil, a, b.getId());
        emprestimo(perfil, b, a.getId());
        emprestimo(perfil, a, a.getId());
        assertThat(clienteRepository.findAllByPerfilId(perfil.getId())).hasSize(2);
        assertThat(emprestimos.listarEmprestimosCliente(perfil.getId(), a)).hasSize(2);
        assertThat(emprestimos.listarEmprestimosCliente(perfil.getId(), b)).hasSize(1);
    }

    @Test
    void rejeitaFiadorInexistenteAntesDePersistirContratoOuParcelas() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        assertThatThrownBy(() -> emprestimo(perfil, devedor, Long.MAX_VALUE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Fiador não encontrado para o perfil informado.");
        assertThat(emprestimoRepository.findAllByPerfilId(perfil.getId())).isEmpty();
        assertThat(parcelaRepository.count()).isZero();
    }

    @Test
    void rejeitaFiadorDeOutroPerfil() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        Cliente externo = cliente(perfil("Outra"), "Externo");
        assertThatThrownBy(() -> emprestimo(perfil, devedor, externo.getId()))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("perfil informado");
        assertThat(emprestimoRepository.findAllByPerfilId(perfil.getId())).isEmpty();
    }

    @Test
    void impedeExcluirFiadorMesmoEmContratoEncerrado() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        Cliente fiador = cliente(perfil, "Fiador");
        Emprestimo emp = emprestimo(perfil, devedor, fiador.getId());
        emprestimos.quitarEmprestimo(perfil.getId(), devedor.getId(), emp.getId());
        assertThatThrownBy(() -> clientes.deletarCliente(perfil.getId(), fiador.getId()))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("fiador");
        assertThat(clienteRepository.findById(fiador.getId())).isPresent();
    }

    @Test
    void mantemBloqueioDeExclusaoDoDevedorELiberaClienteSemVinculo() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        emprestimo(perfil, devedor, cliente(perfil, "Fiador").getId());
        assertThatThrownBy(() -> clientes.deletarCliente(perfil.getId(), devedor.getId()))
                .hasMessageContaining("empréstimos cadastrados");
    }

    @Test
    void excluiClienteSemContratosEContratoAbertoComFiadorSemExcluirPessoa() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        Cliente fiador = cliente(perfil, "Fiador");
        Emprestimo emp = emprestimo(perfil, devedor, fiador.getId());
        emprestimos.deletarEmprestimo(perfil.getId(), devedor.getId(), emp.getId());
        entityManager.flush();
        assertThat(clienteRepository.findById(fiador.getId())).isPresent();
        clientes.deletarCliente(perfil.getId(), fiador.getId());
        entityManager.flush();
        assertThat(clienteRepository.findById(fiador.getId())).isEmpty();
    }

    @Test
    void preservaPagamentosEstornoAlteracaoReprogramacaoEQuitacaoComFiador() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        Cliente fiador = cliente(perfil, "Fiador");
        Emprestimo emp = emprestimo(perfil, devedor, fiador.getId());
        Long id = emp.getId();
        emprestimos.pagarParcelaParcial(perfil.getId(), id, 1, new BigDecimal("50"));
        assertThat(parcelaRepository.findByEmprestimoIdAndPerfilIdAndNumeroParcela(id, perfil.getId(), 1).orElseThrow().getStatus())
                .isEqualTo(ParcelaStatus.PARCIAL);
        assertThat(emp.getValorRecebido()).isEqualByComparingTo("50");
        emprestimos.pagarParcela(perfil.getId(), id, 1);
        assertThat(emp.getValorRecebido()).isEqualByComparingTo("200");
        parcelas.desfazerPagamentoParcela(perfil.getId(), id, 1);
        assertThat(emp.getValorRecebido()).isEqualByComparingTo("0");
        parcelas.alterarParcela(perfil.getId(), id, 1, LocalDate.now().plusDays(2), new BigDecimal("250"));
        assertThat(emp.getValorTotalEmprestimo()).isEqualByComparingTo("650");
        LocalDate inicio = LocalDate.now().plusMonths(1).withDayOfMonth(10);
        emprestimos.reprogramarParcelas(perfil.getId(), devedor.getId(), id, inicio, new BigDecimal("210"));
        assertThat(emp.getValorTotalEmprestimo()).isEqualByComparingTo("630");
        assertThat(emp.getInicioPagamento()).isEqualTo(inicio);
        assertThat(emp.getFinalPagamento()).isEqualTo(inicio.plusMonths(2));
        emprestimos.quitarEmprestimo(perfil.getId(), devedor.getId(), id);
        assertThat(emp.getEmprestimoStatus()).isEqualTo(EmprestimoStatus.QUITADO);
        assertThat(emp.getValorRecebido()).isEqualByComparingTo("630");
        assertThat(emp.getValorAReceber()).isEqualByComparingTo("0");
        assertThat(emp.getFiador().getId()).isEqualTo(fiador.getId());
    }

    @Test
    void preservaCalculoComJurosERefinanciamentoSemCriarNovoContrato() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        Emprestimo emp = emprestimos.criarEmprestimo(perfil.getId(), devedor, new BigDecimal("1000"),
                2, new BigDecimal("10"), FormaPagamento.PIX, FrequenciaPagamento.MENSAL, TipoContrato.DIGITAL,
                cliente(perfil, "Fiador").getId());
        assertThat(emp.getValorParcela()).isEqualByComparingTo("576.19");
        assertThat(emp.getValorTotalEmprestimo()).isEqualByComparingTo("1152.38");
        emprestimos.refinanciarEmprestimo(perfil.getId(), devedor.getId(), emp.getId());
        assertThat(emp.getEmprestimoStatus()).isEqualTo(EmprestimoStatus.REFINANCIADO);
        assertThat(emp.getParcelas()).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ParcelaStatus.PAGO));
        assertThat(emprestimoRepository.findAllByPerfilId(perfil.getId())).hasSize(1);
    }

    @Test
    void preservaObservacoesEAtrasosRelatandoSomenteDevedor() {
        Perfil perfil = perfil("Carteira");
        Cliente devedor = cliente(perfil, "Devedor");
        Cliente fiador = cliente(perfil, "Fiador");
        Emprestimo emp = emprestimo(perfil, devedor, fiador.getId());
        observacoes.criarObservacao(perfil.getId(), emp.getId(), 1, "  Nota  ");
        var nota = observacoes.listarObservacoes(perfil.getId(), emp.getId(), 1).get(0);
        assertThat(nota.observacao()).isEqualTo("Nota");
        observacoes.editarObservacao(perfil.getId(), nota.id(), "Editada");
        assertThat(observacoes.listarObservacoes(perfil.getId(), emp.getId(), 1)).singleElement()
                .satisfies(n -> assertThat(n.observacao()).isEqualTo("Editada"));
        observacoes.removerObservacao(perfil.getId(), nota.id());
        assertThat(observacoes.listarObservacoes(perfil.getId(), emp.getId(), 1)).isEmpty();
        emp.getParcelas().get(0).setDataVencimento(LocalDate.now().minusDays(1));
        entityManager.flush();
        parcelas.atualizarParcelasAtrasadas(perfil.getId());
        assertThat(relatorios.gerarRelatorioInadimplentes(perfil.getId())).singleElement().satisfies(d -> {
            assertThat(d.nomeCliente()).isEqualTo("Devedor");
            assertThat(d.valorTotalEmAtraso()).isEqualByComparingTo("200");
        });
    }

    @Test
    void preservaLayoutDeImportacaoSemExigirDadosPessoaisOuFiador() throws Exception {
        Perfil perfil = perfil("Carteira");
        byte[] arquivo;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Entrada");
            sheet.createRow(0).createCell(0).setCellValue("Cabeçalho antigo");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("2026-01-01");
            row.createCell(1).setCellValue("Importado");
            row.createCell(3).setCellValue("DINHEIRO");
            row.createCell(4).setCellValue("FISICO");
            row.createCell(5).setCellValue("MENSAL");
            row.createCell(6).setCellValue(600);
            row.createCell(7).setCellValue(3);
            row.createCell(8).setCellValue(1);
            row.createCell(10).setCellValue(400);
            row.createCell(11).setCellValue(200);
            row.createCell(13).setCellValue(600);
            row.createCell(14).setCellValue(200);
            row.createCell(15).setCellValue(0);
            row.createCell(16).setCellValue("2026-02-01");
            row.createCell(17).setCellValue("2026-04-01");
            row.createCell(18).setCellValue("EM_ABERTO");
            wb.write(out);
            arquivo = out.toByteArray();
        }
        var resultado = importador.importarPlanilha(perfil.getId(), new ByteArrayInputStream(arquivo));
        assertThat(resultado.linhasImportadas()).isEqualTo(1);
        assertThat(resultado.erros()).isEmpty();
        entityManager.flush();
        entityManager.clear();
        Cliente cliente = clientes.buscarPorNome(perfil.getId(), "Importado");
        assertThat(cliente.getCpf()).isNull();
        assertThat(cliente.getEnderecoRua()).isNull();
        Emprestimo emp = emprestimos.listarEmprestimosCliente(perfil.getId(), cliente).get(0);
        assertThat(emp.getFiador()).isNull();
        assertThat(emp.getParcelas()).hasSize(3);
        assertThat(emp.getParcelas()).filteredOn(p -> p.getStatus() == ParcelaStatus.PAGO).hasSize(1);
        assertThat(importador.importarPlanilha(perfil.getId(), new ByteArrayInputStream(arquivo)).linhasDuplicadas()).isEqualTo(1);
    }

    @Test
    void preservaTodasAsColunasEValoresDaExportacaoComFiador() throws Exception {
        Perfil perfil = perfil("Carteira");
        Emprestimo emp = emprestimo(perfil, cliente(perfil, "Devedor"), cliente(perfil, "Fiador").getId());
        emprestimos.pagarParcelaParcial(perfil.getId(), emp.getId(), 1, new BigDecimal("50"));
        byte[] arquivo = exportador.exportarExcel(perfil.getId());
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(arquivo))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Backup");
            String[] esperados = {"Data empréstimo", "Cliente", "Indicação", "Forma Pgto", "Contrato", "Pagamento",
                    "Valor empréstimo", "Qtde parcelas", "Qtde parcelas pagas", "Qtde parcelas a vencer",
                    "Valor a receber", "Valor recebido", "Juros cobrado", "Valor total empréstimos",
                    "Valor parcela", "Tx juros", "Inicio pgto", "Final pgto", "Situação do empréstimo", "Detalhes das parcelas"};
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 20);
            for (int i = 0; i < esperados.length; i++) {
                assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(esperados[i]);
            }
            Row row = sheet.getRow(1);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("Devedor");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo(emp.getTipoContrato().name());
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(550);
            assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(50);
            assertThat(row.getCell(13).getCellStyle().getDataFormatString()).contains("R$");
            assertThat(row.getCell(19).getStringCellValue()).contains("\"pago\":50");
        }
    }

    @Test
    void novosDadosDetalhadosContinuamRestritosAoPerfilAtivo() {
        Perfil um = perfil("Um");
        Perfil dois = perfil("Dois");
        Cliente cliente = cliente(um, "Cliente");
        assertThatThrownBy(() -> clientes.buscarDetalhado(dois.getId(), cliente.getId())).hasMessageContaining("não encontrado");
        assertThatThrownBy(() -> perfis.buscarDetalhado(dois.getId(), um.getId())).isInstanceOf(RegraNegocioException.class);
    }

    private Perfil perfil(String nome) {
        CriarPerfilRequest request = new CriarPerfilRequest();
        request.setNome(nome);
        return perfis.buscarPorId(perfis.criar(request).getId());
    }

    private Cliente cliente(Perfil perfil, String nome) {
        clientes.criarCliente(perfil.getId(), nome, null, "Rua", "Bairro", 100);
        return clientes.buscarPorNome(perfil.getId(), nome);
    }

    private Emprestimo emprestimo(Perfil perfil, Cliente devedor, Long fiadorId) {
        return emprestimos.criarEmprestimo(perfil.getId(), devedor, new BigDecimal("600"), 3,
                BigDecimal.ZERO, FormaPagamento.DINHEIRO, FrequenciaPagamento.MENSAL, TipoContrato.FISICO, fiadorId);
    }

    private ClienteCreateDTO clienteCompleto(String nome, String cpf) {
        return new ClienteCreateDTO(nome, "Indicador", "Rua", "Bairro", 100,
                "Brasileira", EstadoCivil.CASADO, "Profissão", "00.123.456-X", "SSP/SP", cpf,
                "Apto 2", "00100-000", "Cidade", "SP");
    }

    private AtualizarClienteDadosDTO dadosCliente(String nome, String cpf) {
        return new AtualizarClienteDadosDTO(nome, "Indicador", "Brasileira", EstadoCivil.CASADO,
                "Profissão", "00.123.456-X", "SSP/SP", cpf, "Rua", 100, "Apto 2", "Bairro",
                "00100-000", "Cidade", "SP");
    }
}
