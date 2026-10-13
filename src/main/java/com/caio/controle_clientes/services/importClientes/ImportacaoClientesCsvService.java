package com.caio.controle_clientes.services.importClientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.validation.CpfUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class ImportacaoClientesCsvService {
    private final ClientesCsvReader reader;
    private final ClienteCsvMapper mapper;
    private final QualidadeClienteCsv qualidade;
    private final ClienteRepositorio clientes;
    private final PerfilRepositorio perfis;
    private final EntityManager em;
    private final TransactionTemplate leitura, escrita;
    public ImportacaoClientesCsvService(ClientesCsvReader reader, ClienteCsvMapper mapper, ClienteRepositorio clientes,
            PerfilRepositorio perfis, EntityManager em, PlatformTransactionManager tm, QualidadeClienteCsv qualidade) {
        this.reader = reader; this.mapper = mapper; this.clientes = clientes; this.perfis = perfis; this.em = em;
        this.qualidade = qualidade;
        leitura = new TransactionTemplate(tm); leitura.setReadOnly(true);
        escrita = new TransactionTemplate(tm); escrita.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private record Acao(String status, String motivo, List<String> conflitos, Map<String, Object> preenchidos,
            boolean existente, String match, Long clienteId, Map<String, Object> finais) {
        Acao(String status, String motivo, List<String> conflitos, Map<String, Object> preenchidos, boolean existente) {
            this(status, motivo, conflitos, preenchidos, existente, "NAO_IDENTIFICADO", null, Map.of());
        }
    }
    public ImportacaoClientesResultadoDTO importar(Long perfilId, MultipartFile arquivo, boolean previa) {
        if (perfilId == null || !perfis.existsById(perfilId)) throw new IllegalArgumentException("Perfil informado não existe.");
        var linhas = reader.ler(arquivo);
        Map<String, Long> ocorrencias = linhas.stream().map(l -> CpfUtil.normalizar(l.campos().get("cpf")))
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.groupingBy(c -> c, java.util.stream.Collectors.counting()));
        IdentificadorClienteCsv.Indice indice = previa ? leitura.execute(t -> {
            var registros = new ArrayList<IdentificadorClienteCsv.Registro>();
            for (Cliente cliente : clientes.findAllByPerfilId(perfilId)) {
                registros.add(new IdentificadorClienteCsv.Registro(cliente.getId(), mapper.lerCliente(cliente)));
                // A prévia/índice não mantém entidades antigas gerenciadas durante a gravação por linha.
                em.detach(cliente);
            }
            return new IdentificadorClienteCsv.Indice(registros);
        }) : new IdentificadorClienteCsv.Indice(List.of());
        var vistos = new HashSet<String>(); var detalhes = new ArrayList<ImportacaoClientesResultadoDTO.Detalhe>();
        int criados = 0, atualizados = 0, ignorados = 0, conflitos = 0, erros = 0, existentes = 0;
        int semRessalvas = 0, comAviso = 0, importadosComAviso = 0, bloqueados = 0, semWord = 0, duplicidades = 0;
        int porNome = 0, porCpf = 0, camposPreenchidos = 0, ambiguos = 0, identidade = 0, clientesAtualizados = 0;
        for (var linha : linhas) {
            String cpf = CpfUtil.normalizar(linha.campos().get("cpf"));
            var avaliacao = qualidade.avaliar(linha.campos(), ocorrencias.getOrDefault(cpf, 0L));
            Acao acao;
            try {
                if (linha.erro() != null) acao = simples("ERRO", linha.erro());
                else if (!avaliacao.elegivel()) acao = simples("IGNORADO", avaliacao.motivo());
                else {
                    Map<String, Object> dados = mapper.mapear(linha.campos());
                    if (cpf != null && vistos.contains(cpf)) acao = simples("DUPLICADO_NO_ARQUIVO", "CPF já processado neste arquivo; primeiro registro válido mantido.");
                    else {
                        if (previa) {
                            var match = indice.encontrar(dados);
                            acao = planejarMatch(match, dados, -(long) linha.numero());
                            if (acao.clienteId() != null && !acao.preenchidos().isEmpty())
                                indice.atualizar(new IdentificadorClienteCsv.Registro(acao.clienteId(), acao.finais()));
                        } else acao = escrita.execute(t -> persistir(perfilId, dados));
                        if (cpf != null && (acao.existente() || acao.status().equals("CRIADO"))) vistos.add(cpf);
                    }
                }
            } catch (IllegalArgumentException e) { acao = simples("ERRO", e.getMessage()); }
            catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException e) {
                // Nunca expõe exceção SQL ou dados pessoais no relatório/log.
                acao = simples("ERRO", "Não foi possível salvar esta linha; nenhuma alteração desta linha foi confirmada.");
            }
            if (acao.existente()) existentes++;
            if (acao.match().equals("NOME")) porNome++;
            if (acao.match().equals("CPF")) porCpf++;
            camposPreenchidos += acao.preenchidos().size();
            if (acao.existente() && !acao.preenchidos().isEmpty()) clientesAtualizados++;
            if (acao.status().startsWith("AMBIGUO")) ambiguos++;
            if (acao.status().equals("CONFLITO_IDENTIDADE")) identidade++;
            String classificacao = avaliacao.classificacao();
            if (acao.status().equals("ERRO")) classificacao = "ERRO";
            else if (acao.status().startsWith("AMBIGUO") || acao.status().equals("CONFLITO_IDENTIDADE")) classificacao = acao.status();
            else if (acao.status().equals("DUPLICADO_NO_ARQUIVO") || acao.conflitos().contains("cpf")) classificacao = "BLOQUEADO_DUPLICIDADE";
            else if (acao.status().equals("CONFLITO_PRESERVADO")) classificacao = "CONFLITO_PRESERVADO";
            else if (acao.status().equals("IGNORADO") && !acao.existente() && avaliacao.elegivel()) classificacao = "BLOQUEADO";
            boolean bloqueado = classificacao.startsWith("BLOQUEADO") || classificacao.startsWith("AMBIGUO")
                || classificacao.equals("CONFLITO_IDENTIDADE") || classificacao.equals("ERRO") || classificacao.equals("SEM_WORD");
            if (bloqueado) bloqueados++;
            if (classificacao.equals("SEM_WORD")) semWord++;
            if (classificacao.equals("BLOQUEADO_DUPLICIDADE")) duplicidades++;
            if (!bloqueado) {
                if (avaliacao.aviso()) { comAviso++; if (!previa && !acao.preenchidos().isEmpty()) importadosComAviso++; }
                else semRessalvas++;
            }
            switch (acao.status()) {
                case "CRIADO" -> criados++;
                case "ATUALIZADO" -> atualizados++;
                case "CONFLITO_PRESERVADO" -> conflitos++;
                case "ERRO" -> erros++;
                default -> ignorados++;
            }
            String motivo = acao.motivo();
            if (avaliacao.elegivel()) motivo = qualidade.juntar(motivo, bloqueado
                ? qualidade.resumir(linha.campos().get("observacoes")) : avaliacao.motivo());
            if (detalhes.size() < 500) detalhes.add(new ImportacaoClientesResultadoDTO.Detalhe(linha.numero(),
                    linha.campos().get("nomeCompleto"), CpfUtil.mascarar(cpf), acao.status(), motivo,
                    acao.conflitos(), List.copyOf(acao.preenchidos().keySet()), qualidade.status(linha.campos()),
                    classificacao, avaliacao.aviso() && !bloqueado, avaliacao.camposAusentes(), acao.match()));
        }
        return new ImportacaoClientesResultadoDTO(previa, linhas.size(), criados, existentes, atualizados, ignorados,
                conflitos, erros, linhas.size() > 500, List.copyOf(detalhes), semRessalvas, comAviso, importadosComAviso,
                bloqueados, semWord, duplicidades, porNome, porCpf, camposPreenchidos, conflitos, ambiguos, identidade, clientesAtualizados);
    }
    private Acao persistir(Long perfilId, Map<String, Object> dados) {
        // Serializa as importações deste fluxo no perfil, por transação curta.
        Perfil perfil = em.find(Perfil.class, perfilId, LockModeType.PESSIMISTIC_WRITE);
        if (perfil == null) throw new IllegalArgumentException("Perfil informado não existe.");
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            var registros = clientes.listarIdentidades(perfilId).stream().map(c -> {
                Map<String, Object> campos = new LinkedHashMap<>(); campos.put("nome", c.getNome()); campos.put("cpf", c.getCpf());
                return new IdentificadorClienteCsv.Registro(c.getId(), campos);
            }).toList();
            var atual = new IdentificadorClienteCsv.Indice(registros);
            var match = atual.encontrar(dados);
            if (match.bloqueio() != null) return planejarMatch(match, dados, null);
            Cliente cliente;
            if (match.registro() == null) cliente = new Cliente();
            else {
                cliente = clientes.findByIdAndPerfilId(match.registro().id(), perfilId).orElse(null);
                if (cliente == null) continue;
                em.refresh(cliente, LockModeType.PESSIMISTIC_WRITE);
                atual.atualizar(new IdentificadorClienteCsv.Registro(cliente.getId(), mapper.lerCliente(cliente)));
                var confirmado = atual.encontrar(dados);
                if (confirmado.bloqueio() != null) return planejarMatch(confirmado, dados, null);
                if (confirmado.registro() == null || !confirmado.registro().id().equals(cliente.getId())) continue;
                match = confirmado;
            }
            Acao acao = planejarMatch(match, dados, null);
            if (!acao.preenchidos().isEmpty()) {
                if (cliente.getId() == null) cliente.setPerfil(perfil);
                mapper.aplicar(cliente, acao.preenchidos()); clientes.saveAndFlush(cliente);
                return new Acao(acao.status(), acao.motivo(), acao.conflitos(), acao.preenchidos(), acao.existente(), acao.match(), cliente.getId(), mapper.lerCliente(cliente));
            }
            return acao;
        }
        return new Acao("CONFLITO_IDENTIDADE", "Cadastro alterado durante a importação; revisão necessária.", List.of(), Map.of(), false, "AMBIGUO", null, Map.of());
    }
    private Acao planejarMatch(IdentificadorClienteCsv.Match match, Map<String, Object> dados, Long idNovo) {
        if (match.bloqueio() != null) return new Acao(match.bloqueio(), match.motivo(), List.of(), Map.of(), false, "AMBIGUO", null, Map.of());
        boolean existente = match.registro() != null;
        if (!existente && !dados.containsKey("cpf")) return new Acao("IGNORADO", "CPF ausente; revisão manual necessária para criar cliente novo.", List.of(), Map.of(), false, "NOVO", null, Map.of());
        Map<String, Object> atuais = existente ? match.registro().campos() : Map.of();
        Acao acao = planejar(atuais, dados, existente);
        var finais = new LinkedHashMap<>(atuais); finais.putAll(acao.preenchidos());
        return new Acao(acao.status(), acao.motivo(), acao.conflitos(), acao.preenchidos(), existente,
            match.tipo(), existente ? match.registro().id() : idNovo, finais);
    }
    /** Política ATUALIZAR_VAZIOS, isolada da leitura CSV e da persistência para evolução futura. */
    private Acao planejar(Map<String, Object> atuais, Map<String, Object> dados, boolean existente) {
        if (!existente && !dados.containsKey("nome")) return simples("IGNORADO", "Nome completo ausente; revisão manual necessária.");
        var preencher = new LinkedHashMap<String, Object>(); var conflitos = new ArrayList<String>();
        dados.forEach((campo, valor) -> {
            Object atual = atuais.get(campo);
            if (atual == null || atual instanceof String s && s.isBlank()) preencher.put(campo, valor);
            else if (campo.equals("nome") && IdentificadorClienteCsv.normalizarNome((String) atual).equals(IdentificadorClienteCsv.normalizarNome((String) valor))) { /* Preserva a grafia existente. */ }
            else if (!mapper.equivalentes(campo, atual, valor)) conflitos.add(campo);
        });
        if (!conflitos.isEmpty()) return new Acao("CONFLITO_PRESERVADO", "Campos diferentes: " + String.join(", ", conflitos) + "; valores existentes preservados.", List.copyOf(conflitos), preencher, true);
        if (!existente) return new Acao("CRIADO", "Cliente novo.", List.of(), preencher, false);
        if (preencher.isEmpty()) return new Acao("IGNORADO", "Cliente existente sem campos vazios a preencher.", List.of(), Map.of(), true);
        return new Acao("ATUALIZADO", "Somente campos vazios preenchidos.", List.of(), preencher, true);
    }
    private Acao simples(String status, String motivo) { return new Acao(status, motivo, List.of(), Map.of(), false); }
}
