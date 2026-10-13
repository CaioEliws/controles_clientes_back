package com.caio.controle_clientes;

import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:controle-clientes-http-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class CredorDevedorFiadorHttpTests {

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepositorio clienteRepository;
    @Autowired EmprestimoRepositorio emprestimoRepository;
    @Autowired com.caio.controle_clientes.repository.ContratoGeradoRepositorio contratoRepository;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void rejeitaRequestSemFiadorOuComFiadorNullSemPersistirEmprestimo() throws Exception {
        Long perfil = perfil();
        Long devedor = cliente(perfil, "Devedor");
        String rota = "/clientes/" + devedor + "/emprestimos";
        for (String body : new String[]{contrato(null), contrato(null).replace("}", ",\"fiadorId\":null}")}) {
            HttpResponse<String> response = request("POST", rota, perfil, body);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("fields").get("fiadorId").asText())
                    .isEqualTo("Fiador é obrigatório");
        }
        assertThat(emprestimoRepository.findAllByPerfilId(perfil)).isEmpty();
    }

    @Test
    void criaComFiadorEEntregaIdentificacaoNoDtoDetalhado() throws Exception {
        Long perfil = perfil();
        Long devedor = cliente(perfil, "Devedor");
        Long fiador = cliente(perfil, "Fiador");
        String rota = "/clientes/" + devedor + "/emprestimos";
        assertThat(request("POST", rota, perfil, contrato(fiador)).statusCode()).isEqualTo(201);
        JsonNode dto = json.readTree(request("GET", rota + "/detalhes", perfil, null).body()).get(0);
        assertThat(dto.get("fiadorId").asLong()).isEqualTo(fiador);
        assertThat(dto.get("fiadorNome").asText()).isEqualTo("Fiador");
        assertThat(request("DELETE", "/clientes/" + fiador, perfil, null).statusCode()).isEqualTo(400);
        assertThat(clienteRepository.findById(fiador)).isPresent();
        JsonNode contratosFiador = json.readTree(request("GET", "/clientes/" + fiador + "/emprestimos/detalhes", perfil, null).body());
        assertThat(contratosFiador.size()).isZero();
    }

    @Test
    void rejeitaFiadorAusenteEOuDeOutroPerfilComErroAtualDaApi() throws Exception {
        Long perfil = perfil();
        Long devedor = cliente(perfil, "Devedor");
        Long externo = cliente(perfil(), "Outro perfil");
        String rota = "/clientes/" + devedor + "/emprestimos";
        for (Long fiador : new Long[]{Long.MAX_VALUE, externo}) {
            HttpResponse<String> response = request("POST", rota, perfil, contrato(fiador));
            assertThat(response.statusCode()).isEqualTo(400);
            JsonNode erro = json.readTree(response.body());
            assertThat(erro.get("error").asText()).isEqualTo("Erro de execução");
            assertThat(erro.get("message").asText()).isEqualTo("Fiador não encontrado para o perfil informado.");
        }
        assertThat(emprestimoRepository.findAllByPerfilId(perfil)).isEmpty();
    }

    @Test
    void cadastroCompletoEAtualizacaoDetalhadaPreservamCamposEEndpointsAntigos() throws Exception {
        Long perfil = perfil();
        String body = dadosPessoais("Pessoa");
        assertThat(request("POST", "/clientes", perfil, body).statusCode()).isEqualTo(201);
        Long id = clienteRepository.findByNomeIgnoreCaseAndPerfilId("Pessoa", perfil).orElseThrow().getId();
        HttpResponse<String> consulta = request("GET", "/clientes/" + id, perfil, null);
        assertThat(consulta.statusCode()).isEqualTo(200);
        JsonNode dados = json.readTree(consulta.body());
        assertThat(dados.get("cpf").asText()).isEqualTo("001.234.567-89");
        assertThat(dados.get("estadoCivil").asText()).isEqualTo("CASADO");
        assertThat(dados.get("enderecoNumero").asInt()).isEqualTo(100);
        assertThat(dados.has("perfil")).isFalse();
        assertThat(dados.has("emprestimos")).isFalse();

        assertThat(request("PUT", "/clientes/" + id, perfil, dadosPessoais("Editado")).statusCode()).isEqualTo(200);
        assertThat(request("PATCH", "/clientes/" + id + "/name", perfil, "{\"nome\":\"Nome via PATCH\"}").statusCode()).isEqualTo(204);
        assertThat(request("PATCH", "/clientes/" + id + "/endereco", perfil,
                "{\"rua\":\"Nova rua\",\"bairro\":\"Novo bairro\",\"numero\":200}").statusCode()).isEqualTo(204);
        JsonNode aposPatch = json.readTree(request("GET", "/clientes/" + id, perfil, null).body());
        assertThat(aposPatch.get("nome").asText()).isEqualTo("Nome via PATCH");
        assertThat(aposPatch.get("enderecoRua").asText()).isEqualTo("Nova rua");
        assertThat(aposPatch.get("cpf").asText()).isEqualTo("001.234.567-89");
        assertThat(aposPatch.get("enderecoComplemento").asText()).isEqualTo("Apto 2");

        assertThat(request("GET", "/clientes/" + id, perfil(), null).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/clientes/" + id, null, null).statusCode()).isEqualTo(400);
        assertThat(request("PUT", "/clientes/" + id, perfil, "{\"nome\":\"\"}").statusCode()).isEqualTo(400);
    }

    @Test
    void cpfDuplicadoRetornaErroSemModificarPessoaEPermiteLimparDadosOpcionais() throws Exception {
        Long perfil = perfil();
        assertThat(request("POST", "/clientes", perfil, dadosPessoais("Um")).statusCode()).isEqualTo(201);
        HttpResponse<String> duplicado = request("POST", "/clientes", perfil,
                dadosPessoais("Dois").replace("001.234.567-89", "00123456789"));
        assertThat(duplicado.statusCode()).isEqualTo(400);
        assertThat(json.readTree(duplicado.body()).get("message").asText()).contains("CPF");
        Long dois = cliente(perfil, "Dois");
        assertThat(request("PUT", "/clientes/" + dois, perfil, dadosPessoais("Dois")).statusCode()).isEqualTo(400);
        assertThat(clienteRepository.findById(dois).orElseThrow().getCpf()).isNull();
        Long um = clienteRepository.findByNomeIgnoreCaseAndPerfilId("Um", perfil).orElseThrow().getId();
        assertThat(request("PUT", "/clientes/" + um, perfil, "{\"nome\":\"Um\"}").statusCode()).isEqualTo(200);
        assertThat(json.readTree(request("GET", "/clientes/" + um, perfil, null).body()).get("cpf").isNull()).isTrue();
    }

    @Test
    void perfilAntigoContinuaSimplesEPermiteConsultarEAtualizarDadosCompletos() throws Exception {
        Long perfil = perfil();
        String nomeCurto = "Curto " + UUID.randomUUID();
        String body = dadosPessoais(nomeCurto).replace("\"nomeIndicador\":null,", "\"nomeCompleto\":\"Nome jurídico\",");
        assertThat(request("PUT", "/perfis/" + perfil, perfil, body).statusCode()).isEqualTo(200);
        JsonNode dto = json.readTree(request("GET", "/perfis/" + perfil, perfil, null).body());
        assertThat(dto.get("nome").asText()).isEqualTo(nomeCurto);
        assertThat(dto.get("nomeCompleto").asText()).isEqualTo("Nome jurídico");
        assertThat(dto.get("cpf").asText()).isEqualTo("001.234.567-89");
        assertThat(dto.has("emprestimos")).isFalse();
        JsonNode lista = json.readTree(request("GET", "/perfis", null, null).body());
        JsonNode curto = null;
        for (JsonNode item : lista) {
            if (item.get("id").asLong() == perfil) curto = item;
        }
        assertThat(curto).isNotNull();
        assertThat(curto.size()).isEqualTo(2);
        assertThat(request("GET", "/perfis/" + perfil, perfil(), null).statusCode()).isEqualTo(400);
        assertThat(request("PUT", "/perfis/" + perfil, perfil, "{\"nome\":\"\"}").statusCode()).isEqualTo(400);
    }

    @Test
    void novaRelacaoNaoAmpliaSerializacaoDasEntidadesLegadasMesmoQuandoFiadorEDevedorSaoIguais() {
        Cliente pessoa = new Cliente();
        pessoa.setNome("Pessoa");
        Emprestimo emp = new Emprestimo();
        emp.setCliente(pessoa);
        emp.setFiador(pessoa);
        JsonNode body = json.readTree(json.writeValueAsString(emp));
        assertThat(body.has("fiador")).isFalse();
        assertThat(body.has("cliente")).isTrue();
        assertThat(body.has("perfil")).isTrue();
        assertThat(body.has("parcelas")).isFalse();
    }

    @Test
    void consultaDadosContratoCompletoViaHttpComTiposEJsonControlados() throws Exception {
        ContratoFixture f = contratoCompleto();
        HttpResponse<String> response = request("GET", f.rota() + "/dados-contrato", f.perfil(), null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode dto = json.readTree(response.body());
        assertThat(dto.size()).isEqualTo(7);
        assertThat(dto.at("/camposDocumento/DEVEDOR_NACIONALIDADE").asText()).isEqualTo("brasileira");
        assertThat(dto.at("/camposDocumento/DEVEDOR_ESTADO_CIVIL").asText()).isEqualTo("casada");
        assertThat(dto.get("emprestimoId").asLong()).isEqualTo(f.emprestimo());
        assertThat(dto.get("credor").get("nomeCompleto").asText()).isEqualTo("Credor completo");
        assertThat(dto.get("devedor").get("nomeCompleto").asText()).isEqualTo("Devedor");
        assertThat(dto.get("fiador").get("nomeCompleto").asText()).isEqualTo("Fiador");
        for (String papel : new String[]{"credor", "devedor", "fiador"}) {
            assertThat(dto.get(papel).size()).isEqualTo(15);
            if (!papel.equals("credor")) assertThat(dto.get(papel).get("sexoDocumento").asText()).isEqualTo("FEMININO");
            assertThat(dto.get(papel).get("enderecoNumero").asInt()).isEqualTo(100);
            assertThat(dto.get(papel).get("estadoCivil").asText()).isEqualTo("CASADO");
            assertThat(dto.get(papel).has("perfil")).isFalse();
            assertThat(dto.get(papel).has("emprestimos")).isFalse();
        }
        JsonNode emp = dto.get("emprestimo");
        assertThat(emp.size()).isEqualTo(13);
        assertThat(emp.get("valorTotalDivida").decimalValue()).isEqualByComparingTo("600");
        assertThat(emp.get("valorTotalDivida")).isEqualTo(emp.get("valorTotalEmprestimo"));
        assertThat(emp.get("valorEmprestado").decimalValue()).isEqualByComparingTo("600");
        assertThat(emp.get("taxaDeJuros").decimalValue()).isEqualByComparingTo("0");
        assertThat(emp.get("dataDoEmprestimo").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(emp.get("emprestimoStatus").asText()).isEqualTo("EM_ABERTO");
        assertThat(dto.get("parcelas").size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            JsonNode parcela = dto.get("parcelas").get(i);
            assertThat(parcela.size()).isEqualTo(3);
            assertThat(parcela.get("numeroParcela").asInt()).isEqualTo(i + 1);
            assertThat(parcela.get("valorParcela").decimalValue()).isEqualByComparingTo("200");
            assertThat(parcela.has("observacoes")).isFalse();
        }
    }

    @Test
    void dadosContratoEAlteracaoFiadorRejeitamPerfilEClienteIncompativeisEHeaderAusente() throws Exception {
        ContratoFixture f = contratoCompleto();
        Long outroPerfil = perfil();
        String outroCliente = f.rota().replace("/clientes/" + f.devedor(), "/clientes/" + f.fiador());
        for (String metodo : new String[]{"GET", "PATCH"}) {
            String sufixo = metodo.equals("GET") ? "/dados-contrato" : "/fiador";
            String body = metodo.equals("GET") ? null : "{\"fiadorId\":" + f.fiador() + "}";
            assertThat(request(metodo, f.rota() + sufixo, null, body).statusCode()).isEqualTo(400);
            HttpResponse<String> externo = request(metodo, f.rota() + sufixo, outroPerfil, body);
            assertThat(externo.statusCode()).isEqualTo(400);
            assertThat(json.readTree(externo.body()).get("message").asText()).contains("Empréstimo não encontrado");
            HttpResponse<String> pathErrado = request(metodo, outroCliente + sufixo, f.perfil(), body);
            assertThat(pathErrado.statusCode()).isEqualTo(400);
            assertThat(json.readTree(pathErrado.body()).get("message").asText()).contains("não pertence ao cliente");
        }
    }

    @Test
    void legadoSemFiadorPodeSerRegularizadoPorPatchEConsultadoComoContrato() throws Exception {
        ContratoFixture f = contratoCompleto();
        Emprestimo legado = emprestimoRepository.findById(f.emprestimo()).orElseThrow();
        legado.setFiador(null); // Simula registro histórico persistido antes da obrigatoriedade.
        emprestimoRepository.saveAndFlush(legado);
        HttpResponse<String> semFiador = request("GET", f.rota() + "/dados-contrato", f.perfil(), null);
        assertThat(semFiador.statusCode()).isEqualTo(400);
        assertThat(json.readTree(semFiador.body()).get("message").asText())
                .isEqualTo("O empréstimo não possui fiador cadastrado.");
        assertThat(request("GET", "/clientes/" + f.devedor() + "/emprestimos", f.perfil(), null).statusCode()).isEqualTo(200);
        assertThat(request("POST", "/parcelas/pagar-parcial", f.perfil(),
                "{\"idEmprestimo\":" + f.emprestimo() + ",\"numeroParcela\":1,\"valorPago\":50}").statusCode()).isEqualTo(204);
        JsonNode antes = json.readTree(request("GET", "/clientes/" + f.devedor() + "/emprestimos/detalhes", f.perfil(), null).body()).get(0);
        assertThat(request("PATCH", f.rota() + "/fiador", f.perfil(), "{\"fiadorId\":" + f.fiador() + "}").statusCode()).isEqualTo(204);
        HttpResponse<String> completo = request("GET", f.rota() + "/dados-contrato", f.perfil(), null);
        assertThat(completo.statusCode()).isEqualTo(200);
        assertThat(json.readTree(completo.body()).get("fiador").get("nomeCompleto").asText()).isEqualTo("Fiador");
        JsonNode depois = json.readTree(request("GET", "/clientes/" + f.devedor() + "/emprestimos/detalhes", f.perfil(), null).body()).get(0);
        for (String campo : new String[]{"valorEmprestado", "valorParcela", "valorTotalEmprestimo", "valorRecebido",
                "valorAReceber", "inicioPagamento", "finalPagamento", "quantidadeParcelas", "status"}) {
            assertThat(depois.get(campo)).as(campo).isEqualTo(antes.get(campo));
        }
    }

    @Test
    void patchFiadorRejeitaAusenteNullInexistenteEOuOutroPerfilSemRemoverVinculo() throws Exception {
        ContratoFixture f = contratoCompleto();
        Long externo = cliente(perfil(), "Externo");
        for (String body : new String[]{"{}", "{\"fiadorId\":null}", "{\"fiadorId\":" + Long.MAX_VALUE + "}",
                "{\"fiadorId\":" + externo + "}"}) {
            HttpResponse<String> response = request("PATCH", f.rota() + "/fiador", f.perfil(), body);
            assertThat(response.statusCode()).isEqualTo(400);
            JsonNode erro = json.readTree(response.body());
            if (body.equals("{}") || body.contains("null")) {
                assertThat(erro.get("fields").get("fiadorId").asText()).isEqualTo("Fiador é obrigatório");
            } else {
                assertThat(erro.get("message").asText()).isEqualTo("Fiador não encontrado para o perfil informado.");
            }
            assertThat(emprestimoRepository.findById(f.emprestimo()).orElseThrow().getFiador().getId()).isEqualTo(f.fiador());
        }
    }

    @Test
    void dadosIncompletosIdentificamCadaParteViaHttpSemBloquearQuitacao() throws Exception {
        for (String parte : new String[]{"credor", "devedor", "fiador"}) {
            ContratoFixture f = contratoCompleto();
            String rota;
            String body;
            if (parte.equals("credor")) {
                rota = "/perfis/" + f.perfil();
                body = dadosCredor(f.perfil()).replace("\"cpf\":\"001.234.567-89\"", "\"cpf\":null");
            } else {
                rota = "/clientes/" + (parte.equals("devedor") ? f.devedor() : f.fiador());
                body = dadosPessoais(parte.equals("devedor") ? "Devedor" : "Fiador")
                        .replace("\"cpf\":\"001.234.567-89\"", "\"cpf\":null");
            }
            assertThat(request("PUT", rota, f.perfil(), body).statusCode()).isEqualTo(200);
            HttpResponse<String> response = request("GET", f.rota() + "/dados-contrato", f.perfil(), null);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("message").asText())
                    .isEqualTo("Dados do " + parte + " incompletos: cpf.");
            assertThat(request("POST", f.rota() + "/quitar", f.perfil(), null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void geraListaEBaixaWordComBytesHistoricosEHeadersCorretos() throws Exception {
        ContratoFixture f = contratoCompleto();
        String rota = f.rota() + "/contratos";
        HttpResponse<String> created = request("POST", rota, f.perfil(), null);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode meta = json.readTree(created.body());
        assertThat(meta.size()).isEqualTo(8);
        assertThat(meta.get("versaoTemplate").asText()).isEqualTo("CONFISSAO_DIVIDA_V1");
        assertThat(meta.has("snapshotJson")).isFalse();
        assertThat(meta.has("docx")).isFalse();
        long id = meta.get("id").asLong();
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rota + "/" + id + "/download"))
                .header("X-Perfil-Id", f.perfil().toString()).header("Origin", "http://localhost:5173").GET();
        var download = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Type").orElseThrow())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(download.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("attachment").contains(meta.get("nomeArquivo").asText());
        assertThat(download.headers().firstValue("Access-Control-Expose-Headers").orElseThrow())
                .contains("Content-Disposition");
        assertThat(download.body()).isNotEmpty().isEqualTo(contratoRepository.findById(id).orElseThrow().getDocx());
        assertThat(com.caio.controle_clientes.services.contrato.ContratoGeracaoService.hash(download.body()))
                .isEqualTo(meta.get("hashDocumento").asText());
        JsonNode lista = json.readTree(request("GET", rota, f.perfil(), null).body());
        assertThat(lista.size()).isEqualTo(1);
        assertThat(lista.get(0).size()).isEqualTo(8);
        assertThat(meta.get("pdfAssinadoDisponivel").asBoolean()).isTrue();
        assertThat(meta.get("pdfDisponivel").asBoolean()).isTrue();
        var pdfDownload = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + rota + "/" + id + "/download-pdf"))
                .header("X-Perfil-Id", f.perfil().toString()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(pdfDownload.statusCode()).isEqualTo(200);
        assertThat(pdfDownload.headers().firstValue("Content-Type").orElseThrow()).isEqualTo("application/pdf");
        assertThat(pdfDownload.headers().firstValue("Content-Disposition").orElseThrow()).contains("attachment", ".pdf");
        var armazenado = contratoRepository.findById(id).orElseThrow();
        assertThat(pdfDownload.body()).isNotEmpty().isEqualTo(armazenado.getPdfFinal());
        assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pdfDownload.body())))
                .isEqualTo(armazenado.getHashPdfFinal());
        assertThat(request("POST", rota, f.perfil(), null).statusCode()).isEqualTo(201);
        assertThat(json.readTree(request("GET", rota, f.perfil(), null).body()).size()).isEqualTo(2);
    }

    @Test
    void endpointsDeContratosExigemHeaderEEscopoCompleto() throws Exception {
        ContratoFixture f = contratoCompleto();
        String rota = f.rota() + "/contratos";
        long id = json.readTree(request("POST", rota, f.perfil(), null).body()).get("id").asLong();
        Long outroPerfil = perfil();
        String errado = rota.replace("/clientes/" + f.devedor(), "/clientes/" + f.fiador());
        for (String method : new String[]{"POST", "GET"}) {
            assertThat(request(method, rota, null, null).statusCode()).isEqualTo(400);
            assertThat(request(method, rota, outroPerfil, null).statusCode()).isEqualTo(400);
            assertThat(request(method, errado, f.perfil(), null).statusCode()).isEqualTo(400);
        }
        for (Long perfil : new Long[]{null, outroPerfil}) {
            assertThat(request("GET", rota + "/" + id + "/download", perfil, null).statusCode()).isEqualTo(perfil == null ? 400 : 404);
        }
        assertThat(request("GET", errado + "/" + id + "/download", f.perfil(), null).statusCode()).isEqualTo(404);
        assertThat(request("GET", rota + "/" + Long.MAX_VALUE + "/download", f.perfil(), null).statusCode()).isEqualTo(404);
        for (Long perfil : new Long[]{null, outroPerfil}) {
            assertThat(request("GET", rota + "/" + id + "/download-pdf", perfil, null).statusCode()).isEqualTo(perfil == null ? 400 : 404);
        }
        assertThat(request("GET", errado + "/" + id + "/download-pdf", f.perfil(), null).statusCode()).isEqualTo(404);
        assertThat(request("GET", rota + "/" + Long.MAX_VALUE + "/download-pdf", f.perfil(), null).statusCode()).isEqualTo(404);
        assertThat(request("GET", rota.replace("/emprestimos/" + f.emprestimo(), "/emprestimos/" + Long.MAX_VALUE)
                + "/" + id + "/download-pdf", f.perfil(), null).statusCode()).isEqualTo(404);
    }

    private ContratoFixture contratoCompleto() throws Exception {
        Long perfil = perfil();
        Long devedor = cliente(perfil, "Devedor");
        Long fiador = cliente(perfil, "Fiador");
        assertThat(request("PUT", "/perfis/" + perfil, perfil, dadosCredor(perfil)).statusCode()).isEqualTo(200);
        assertThat(request("PUT", "/clientes/" + devedor, perfil, dadosPessoais("Devedor")).statusCode()).isEqualTo(200);
        assertThat(request("PUT", "/clientes/" + fiador, perfil,
                dadosPessoais("Fiador").replace("001.234.567-89", "002.234.567-89")).statusCode()).isEqualTo(200);
        String base = "/clientes/" + devedor + "/emprestimos";
        assertThat(request("POST", base, perfil, contrato(fiador)).statusCode()).isEqualTo(201);
        Long emp = json.readTree(request("GET", base + "/detalhes", perfil, null).body()).get(0).get("id").asLong();
        return new ContratoFixture(perfil, devedor, fiador, emp, base + "/" + emp);
    }

    private String dadosCredor(Long perfil) {
        return dadosPessoais("Carteira completa " + perfil)
                .replace("\"nomeIndicador\":null,", "\"nomeCompleto\":\"Credor completo\",");
    }

    private record ContratoFixture(Long perfil, Long devedor, Long fiador, Long emprestimo, String rota) {}

    private Long perfil() throws Exception {
        HttpResponse<String> response = request("POST", "/perfis", null,
                "{\"nome\":\"Carteira " + UUID.randomUUID() + "\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asLong();
    }

    private Long cliente(Long perfil, String nome) throws Exception {
        HttpResponse<String> response = request("POST", "/clientes", perfil,
                "{\"nome\":\"" + nome + "\",\"enderecoRua\":\"Rua\",\"enderecoBairro\":\"Bairro\",\"enderecoNumero\":100}");
        assertThat(response.statusCode()).isEqualTo(201);
        return clienteRepository.findByNomeIgnoreCaseAndPerfilId(nome, perfil).orElseThrow().getId();
    }

    private String contrato(Long fiador) {
        return "{\"valorEmprestado\":600,\"quantidadeParcelas\":3,\"jurosCobrado\":0,"
                + "\"formaPagamento\":\"DINHEIRO\",\"frequenciaPagamento\":\"MENSAL\",\"tipoContrato\":\"FISICO\""
                + (fiador == null ? "" : ",\"fiadorId\":" + fiador) + "}";
    }

    private String dadosPessoais(String nome) {
        return "{\"nome\":\"" + nome + "\",\"nomeIndicador\":null,\"nacionalidade\":\"Brasileira\","
                + "\"sexoDocumento\":\"FEMININO\",\"estadoCivil\":\"CASADO\",\"profissao\":\"Profissão\",\"rg\":\"52.779.723\",\"rgOrgaoExpedidor\":\"SSP/SP\","
                + "\"cpf\":\"001.234.567-89\",\"enderecoRua\":\"Rua\",\"enderecoNumero\":100,"
                + "\"enderecoComplemento\":\"Apto 2\",\"enderecoBairro\":\"Bairro\",\"enderecoCep\":\"00100-000\","
                + "\"enderecoCidade\":\"Cidade\",\"enderecoEstado\":\"SP\"}";
    }

    private HttpResponse<String> request(String method, String path, Long perfil, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (perfil != null) builder.header("X-Perfil-Id", perfil.toString());
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
