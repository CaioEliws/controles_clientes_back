package com.caio.controle_clientes.validation;

/** Comparação textual, sem perda de zeros. Não implementa checksum. */
public final class CpfUtil {
    private CpfUtil() {}
    public static String normalizar(String cpf) {
        return cpf == null || cpf.isBlank() ? null : cpf.replaceAll("[.\\-\\s]", "");
    }
    public static boolean validarEstrutura(String cpf) {
        String valor = normalizar(cpf);
        return valor != null && valor.matches("[0-9]{11}");
    }
    public static String mascarar(String cpf) {
        String valor = normalizar(cpf);
        return valor != null && valor.matches("[0-9]{11}") ? "***.***." + valor.substring(6, 9) + "-**" : null;
    }
}
