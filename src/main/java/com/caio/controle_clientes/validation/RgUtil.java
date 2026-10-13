package com.caio.controle_clientes.validation;

/** RG é texto. Validação estrutural e apresentação sem cálculo de dígito verificador. */
public final class RgUtil {
    private RgUtil() {}
    private static String apresentacao(String valor) {
        return valor == null ? null : valor.strip().replaceAll("[\\s\\p{Z}]+", "").toUpperCase(java.util.Locale.ROOT);
    }
    public static String normalizar(String valor) {
        String texto = apresentacao(valor);
        return texto == null ? null : texto.replaceAll("[.\\-/]", "");
    }
    public static boolean validarEstrutura(String valor) {
        return validar(valor);
    }
    public static boolean validar(String valor) {
        if (valor == null || valor.isBlank()) return true; // Campo cadastral opcional.
        String normalizado = normalizar(valor);
        return normalizado.matches("(?:[0-9]{7,13}|[0-9]{6,12}X)");
    }
    public static String formatar(String valor) { return formatar(valor, null); }

    /** Aceita UF isolada ou no final de órgão/UF; uma UF ausente nunca impede o fallback. */
    public static String extrairUf(String orgaoOuUf) {
        String texto = apresentacao(orgaoOuUf);
        if (texto == null) return null;
        var match = java.util.regex.Pattern.compile("(?:^|[/\\-])([A-Z]{2})$").matcher(texto);
        if (!match.find()) return null;
        try { return com.caio.controle_clientes.models.UfBrasil.valueOf(match.group(1)).name(); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static String formatar(String valor, String ufEmissora) {
        String texto = apresentacao(valor);
        // Leitura de legados inválidos é tolerante; criação/edição usa @RgValido.
        if (texto == null || texto.isEmpty() || !validar(texto)) return texto;
        String normalizado = normalizar(texto);
        // Pontuação válida informada tem prioridade sobre qualquer máscara automática.
        if (!texto.equals(normalizado)) return texto;
        // Convenção de 9 caracteres já utilizada no projeto, sem calcular/inventar DV.
        if (normalizado.length() == 9) {
            return agrupar(normalizado.substring(0, 8)) + "-" + normalizado.substring(8);
        }
        // Não há regras estaduais verificadas: qualquer UF usa o mesmo fallback visual.
        boolean xFinal = normalizado.endsWith("X");
        String numeros = xFinal ? normalizado.substring(0, normalizado.length() - 1) : normalizado;
        return agrupar(numeros) + (xFinal ? "X" : "");
    }
    private static String agrupar(String numeros) {
        return numeros.replaceAll("(?<=\\d)(?=(?:\\d{3})+$)", ".");
    }
}
