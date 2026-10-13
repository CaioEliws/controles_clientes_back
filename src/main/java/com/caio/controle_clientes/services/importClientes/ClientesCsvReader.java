package com.caio.controle_clientes.services.importClientes;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/** CSV delimitado por ponto e vírgula, incluindo aspas escapadas e campos multilinha. */
@Component
public class ClientesCsvReader {
    public record Linha(int numero, Map<String, String> campos, String erro) {}
    private record Registro(int numero, List<String> campos) {}
    public List<Linha> ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.getOriginalFilename() == null
                || !arquivo.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".csv"))
            throw new IllegalArgumentException("Envie um arquivo CSV válido.");
        if (arquivo.isEmpty()) throw new IllegalArgumentException("CSV vazio: informe um arquivo com cabeçalho.");
        if (arquivo.getSize() > 1_000_000) throw new IllegalArgumentException("CSV excede o limite de 1 MB.");
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            var registros = registros(new PushbackReader(new InputStreamReader(arquivo.getInputStream(), decoder), 1));
            if (registros.isEmpty()) throw new IllegalArgumentException("CSV vazio: informe um arquivo com cabeçalho.");
            List<String> colunas = registros.get(0).campos().stream().map(String::strip).toList();
            if (new HashSet<>(colunas).size() != colunas.size() || colunas.contains(""))
                throw new IllegalArgumentException("CSV inválido: cabeçalhos vazios ou duplicados.");
            for (String essencial : List.of("cpf", "nomeCompleto", "statusExtracao"))
                if (!colunas.contains(essencial)) throw new IllegalArgumentException("CSV inválido: coluna " + essencial + " não encontrada.");
            var linhas = new ArrayList<Linha>();
            for (Registro registro : registros.subList(1, registros.size())) {
                if (registro.campos().stream().allMatch(String::isBlank)) continue;
                if (linhas.size() >= 10_000) throw new IllegalArgumentException("CSV excede o limite de 10000 registros.");
                Map<String, String> dados = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(colunas.size(), registro.campos().size()); i++) dados.put(colunas.get(i), registro.campos().get(i));
                linhas.add(new Linha(registro.numero(), Collections.unmodifiableMap(dados),
                        registro.campos().size() == colunas.size() ? null : "Quantidade de colunas diferente do cabeçalho."));
            }
            return List.copyOf(linhas);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("CSV inválido: utilize UTF-8, com ou sem BOM.");
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o CSV em UTF-8.");
        }
    }
    private List<Registro> registros(PushbackReader reader) throws IOException {
        try (reader) {
            var registros = new ArrayList<Registro>();
            var campos = new ArrayList<String>(); var campo = new StringBuilder();
            boolean aspas = false, fechado = false, inicio = true, primeiro = true;
            int linha = 1, inicioRegistro = 1, ch;
            while ((ch = reader.read()) != -1) {
                if (primeiro) { primeiro = false; if (ch == '\uFEFF') continue; }
                char c = (char) ch;
                if (aspas) {
                    if (c == '"') {
                        int proximo = reader.read();
                        if (proximo == '"') campo.append('"');
                        else { aspas = false; fechado = true; if (proximo != -1) reader.unread(proximo); }
                    } else { campo.append(c); if (c == '\n') linha++; }
                    continue;
                }
                if (c == '"') {
                    if (!inicio || fechado) throw malformado();
                    aspas = true; inicio = false; continue;
                }
                if (c == ';' || c == '\n' || c == '\r') {
                    campos.add(campo.toString()); campo.setLength(0); inicio = true; fechado = false;
                    if (c != ';') {
                        if (c == '\r') { int proximo = reader.read(); if (proximo != '\n' && proximo != -1) reader.unread(proximo); }
                        registros.add(new Registro(inicioRegistro, List.copyOf(campos))); campos.clear();
                        linha++; inicioRegistro = linha;
                    }
                } else {
                    if (fechado && !Character.isWhitespace(c)) throw malformado();
                    if (!fechado) { campo.append(c); inicio = false; }
                }
            }
            if (aspas) throw malformado();
            if (!campos.isEmpty() || !campo.isEmpty() || fechado) { campos.add(campo.toString()); registros.add(new Registro(inicioRegistro, List.copyOf(campos))); }
            return registros;
        }
    }
    private IllegalArgumentException malformado() { return new IllegalArgumentException("CSV inválido: aspas malformadas."); }
}
