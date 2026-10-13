package com.caio.controle_clientes.services.importClientes;

import com.caio.controle_clientes.validation.CpfUtil;
import java.text.Normalizer;
import java.util.*;

/** Índices de comparação, sem alterar grafia cadastrada nem escolher homônimos arbitrariamente. */
public final class IdentificadorClienteCsv {
    private IdentificadorClienteCsv() {}
    public static String normalizarNome(String nome) {
        if (nome == null) return "";
        return Normalizer.normalize(nome.replaceAll("[\\s\\p{Z}]+", " ").strip(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    public record Registro(Long id, Map<String, Object> campos) {}
    public record Match(String tipo, Registro registro, String bloqueio, String motivo) {}
    public static class Indice {
        private final Map<Long, Registro> registros = new LinkedHashMap<>();
        private final Map<String, Set<Long>> nomes = new HashMap<>(), cpfs = new HashMap<>();
        public Indice(Collection<Registro> registros) { registros.forEach(this::atualizar); }
        public void atualizar(Registro registro) {
            Registro antigo = registros.get(registro.id());
            if (antigo != null) { remover(nomes, normalizarNome((String) antigo.campos().get("nome")), antigo.id()); remover(cpfs, CpfUtil.normalizar((String) antigo.campos().get("cpf")), antigo.id()); }
            Registro copia = new Registro(registro.id(), Collections.unmodifiableMap(new LinkedHashMap<>(registro.campos())));
            registros.put(copia.id(), copia);
            adicionar(nomes, normalizarNome((String) copia.campos().get("nome")), copia.id());
            adicionar(cpfs, CpfUtil.normalizar((String) copia.campos().get("cpf")), copia.id());
        }
        private void adicionar(Map<String, Set<Long>> indice, String chave, Long id) { if (chave != null && !chave.isBlank()) indice.computeIfAbsent(chave, k -> new LinkedHashSet<>()).add(id); }
        private void remover(Map<String, Set<Long>> indice, String chave, Long id) { if (chave != null && indice.containsKey(chave)) indice.get(chave).remove(id); }
        public Match encontrar(Map<String, Object> dados) {
            Set<Long> porNome = nomes.getOrDefault(normalizarNome((String) dados.get("nome")), Set.of());
            String cpf = CpfUtil.normalizar((String) dados.get("cpf"));
            Set<Long> porCpf = cpf == null ? Set.of() : cpfs.getOrDefault(cpf, Set.of());
            if (porCpf.size() > 1) return bloqueio("AMBIGUO_CPF", "Mais de um cliente tem esse CPF no perfil; revisão necessária.");
            if (porNome.size() > 1) {
                if (porCpf.size() == 1 && porNome.contains(porCpf.iterator().next())) return encontrado("CPF", porCpf.iterator().next());
                if (!porCpf.isEmpty()) return bloqueio("CONFLITO_IDENTIDADE", "Nome e CPF apontam para clientes diferentes; nenhum cadastro será atualizado.");
                return bloqueio("AMBIGUO_NOME", "Mais de um cliente tem o mesmo nome normalizado; revisão necessária.");
            }
            if (porNome.size() == 1) {
                Long id = porNome.iterator().next();
                String atual = CpfUtil.normalizar((String) registros.get(id).campos().get("cpf"));
                if ((!porCpf.isEmpty() && !porCpf.contains(id)) || (cpf != null && atual != null && !cpf.equals(atual)))
                    return bloqueio("CONFLITO_IDENTIDADE", "Nome e CPF não identificam a mesma pessoa; nenhum cadastro será atualizado.");
                return encontrado("NOME", id);
            }
            if (porCpf.size() == 1) return encontrado("CPF", porCpf.iterator().next());
            return new Match("NOVO", null, null, "Nenhum cliente encontrado por nome ou CPF no perfil.");
        }
        private Match encontrado(String tipo, Long id) { return new Match(tipo, registros.get(id), null, "Cliente encontrado por " + tipo.toLowerCase(Locale.ROOT) + "."); }
        private Match bloqueio(String status, String motivo) { return new Match("AMBIGUO", null, status, motivo); }
    }
}
