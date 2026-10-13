package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.dto.contrato.ContratoSnapshotDTO;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

@Service
public class ContratoTemplateService {
    private static final Pattern NODES = Pattern.compile(
            "<w:p(?:\\s[^>]*)?>|</w:p>|<w:t(?:\\s[^>]*)?>(.*?)</w:t>", Pattern.DOTALL);
    private static final Pattern SLOT = Pattern.compile("\\{\\{([A-Z_]+)}}");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9A-Fa-f]+|#[0-9]+|amp|lt|gt|quot|apos);");
    private record Texto(int inicio, int fim, String valor) {}
    private record Alteracao(int inicio, int fim, String valor) {}

    public byte[] gerar(ContratoSnapshotDTO snapshot) {
        if (!ContratoSnapshotService.VERSAO.equals(snapshot.versaoTemplate())) {
            throw new RegraNegocioException("Versão de template não suportada.");
        }
        try (InputStream input = new ClassPathResource("templates/contrato-confissao-divida-v1.docx").getInputStream()) {
            return preencher(input.readAllBytes(), snapshot.camposDocumento());
        } catch (IOException e) {
            throw new RegraNegocioException("Não foi possível carregar o modelo Word do contrato.");
        }
    }

    public byte[] preencher(byte[] template, Map<String, String> campos) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(template));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            boolean documento = false;
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] conteudo = input.readAllBytes();
                if (entry.getName().equals("word/document.xml")) documento = true;
                if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                    conteudo = substituirXml(new String(conteudo, StandardCharsets.UTF_8), campos)
                            .getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry copia = new ZipEntry(entry.getName());
                if (entry.getTime() >= 0) copia.setTime(entry.getTime());
                output.putNextEntry(copia);
                output.write(conteudo);
                output.closeEntry();
            }
            if (!documento) throw new RegraNegocioException("Modelo Word inválido: documento principal ausente.");
            output.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RegraNegocioException("Não foi possível preencher o modelo Word do contrato.");
        }
    }

    private String substituirXml(String xml, Map<String, String> campos) {
        if ("1".equals(campos.get("QUANTIDADE_PARCELAS")) && campos.containsKey("VENCIMENTO_UNICO_EXTENSO")) {
            xml = adaptarParcelaUnica(xml);
        }
        Deque<List<Texto>> pilha = new ArrayDeque<>();
        List<Alteracao> alteracoes = new ArrayList<>();
        Matcher matcher = NODES.matcher(xml);
        while (matcher.find()) {
            if (matcher.group().startsWith("<w:p")) pilha.push(new ArrayList<>());
            else if (matcher.group().equals("</w:p>")) {
                if (!pilha.isEmpty()) substituirParagrafo(pilha.pop(), campos, alteracoes);
            } else if (!pilha.isEmpty()) {
                pilha.peek().add(new Texto(matcher.start(1), matcher.end(1), decodificar(matcher.group(1))));
            }
        }
        StringBuilder resultado = new StringBuilder(xml);
        alteracoes.sort(Comparator.comparingInt(Alteracao::inicio).reversed());
        for (Alteracao a : alteracoes) resultado.replace(a.inicio(), a.fim(), escapar(a.valor()));
        Matcher textos = Pattern.compile("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", Pattern.DOTALL).matcher(resultado);
        StringBuilder visivel = new StringBuilder();
        while (textos.find()) visivel.append(decodificar(textos.group(1)));
        if (visivel.indexOf("{{") >= 0 || visivel.indexOf("}}") >= 0) {
            throw new RegraNegocioException("Modelo Word contém placeholder não preenchido.");
        }
        return resultado.toString();
    }

    /** Ajustes locais do pagamento conforme o Word oficial de uma parcela, mantendo os runs/estilos. */
    private String adaptarParcelaUnica(String xml) {
        Pattern paragrafos = Pattern.compile("<w:p(?:\\s[^>]*)?>.*?</w:p>", Pattern.DOTALL);
        return paragrafos.matcher(xml).replaceAll(match -> {
            String paragrafo = match.group();
            List<Texto> nodes = new ArrayList<>();
            Matcher textos = Pattern.compile("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", Pattern.DOTALL).matcher(paragrafo);
            while (textos.find()) nodes.add(new Texto(textos.start(1), textos.end(1), decodificar(textos.group(1))));
            String texto = nodes.stream().map(Texto::valor).reduce("", String::concat);
            if (texto.contains("{{PRIMEIRA_PARCELA}}") && texto.contains("{{ULTIMA_PARCELA}}")) return "";
            Map<String, String> trocas;
            if (texto.startsWith("CLAUSULA 3ª")) {
                trocas = Map.of("com vencimento fixo todo dia ", "com vencimento fixo no dia ",
                        "{{DIA_VENCIMENTO}}", "{{VENCIMENTO_UNICO_EXTENSO}}", " de cada mês.", ".");
            } else if (texto.contains("{{VALOR_PROMISSORIA}}")) {
                trocas = Map.of("§2º.", "§1º.", "1 (uma) promissória", "uma nota promissória");
            } else return Matcher.quoteReplacement(paragrafo);
            List<Alteracao> substituicoes = new ArrayList<>();
            trocas.forEach((antes, depois) -> {
                int inicio = texto.indexOf(antes);
                if (inicio >= 0) substituicoes.add(new Alteracao(inicio, inicio + antes.length(), depois));
            });
            List<Alteracao> alteracoes = new ArrayList<>();
            aplicarSubstituicoes(nodes, substituicoes, alteracoes);
            StringBuilder resultado = new StringBuilder(paragrafo);
            alteracoes.sort(Comparator.comparingInt(Alteracao::inicio).reversed());
            for (Alteracao a : alteracoes) resultado.replace(a.inicio(), a.fim(), escapar(a.valor()));
            return Matcher.quoteReplacement(resultado.toString());
        });
    }

    private void substituirParagrafo(List<Texto> nodes, Map<String, String> campos, List<Alteracao> alteracoes) {
        String texto = nodes.stream().map(Texto::valor).reduce("", String::concat);
        Matcher slots = SLOT.matcher(texto);
        List<Alteracao> substituicoes = new ArrayList<>();
        while (slots.find()) {
            String valor = campos.get(slots.group(1));
            if (valor == null) throw new RegraNegocioException("Modelo Word contém placeholder não preenchido: " + slots.group(1));
            substituicoes.add(new Alteracao(slots.start(), slots.end(), valor));
        }
        aplicarSubstituicoes(nodes, substituicoes, alteracoes);
    }

    private void aplicarSubstituicoes(List<Texto> nodes, List<Alteracao> substituicoes, List<Alteracao> alteracoes) {
        substituicoes.sort(Comparator.comparingInt(Alteracao::inicio));
        int offset = 0;
        for (Texto node : nodes) {
            StringBuilder valor = new StringBuilder(node.valor());
            boolean mudou = false;
            for (int i = substituicoes.size()-1; i >= 0; i--) {
                Alteracao s = substituicoes.get(i);
                if (offset < s.fim() && offset + node.valor().length() > s.inicio()) {
                    int inicio = Math.max(0, s.inicio()-offset);
                    int fim = Math.min(node.valor().length(), s.fim()-offset);
                    String insercao = offset <= s.inicio() ? s.valor() : "";
                    valor.replace(inicio, fim, insercao);
                    mudou = true;
                }
            }
            if (mudou) alteracoes.add(new Alteracao(node.inicio(), node.fim(), valor.toString()));
            offset += node.valor().length();
        }
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private static String decodificar(String texto) {
        return ENTITY.matcher(texto).replaceAll(match -> Matcher.quoteReplacement(switch (match.group(1)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            default -> new String(Character.toChars(match.group(1).startsWith("#x")
                    ? Integer.parseInt(match.group(1).substring(2),16) : Integer.parseInt(match.group(1).substring(1))));
        }));
    }
}
