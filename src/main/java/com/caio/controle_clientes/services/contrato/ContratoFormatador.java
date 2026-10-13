package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.dto.contrato.ParteContratoDTO;
import com.caio.controle_clientes.models.EstadoCivil;
import com.caio.controle_clientes.models.SexoDocumento;
import com.caio.controle_clientes.models.UfBrasil;
import java.util.Set;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ContratoFormatador {
    private ContratoFormatador() {}
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    public static String moeda(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(PT_BR);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return formato.format(valor);
    }
    public static String data(LocalDate data) { return data.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")); }
    public static String dataExtenso(LocalDate data) {
        return data.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", PT_BR));
    }
    public static String cpf(String valor) {
        if (valor == null || !valor.matches("[0-9]{11}")) return valor;
        return valor.substring(0, 3) + "." + valor.substring(3, 6) + "."
                + valor.substring(6, 9) + "-" + valor.substring(9);
    }
    public static String cep(String valor) {
        if (valor == null || !valor.matches("[0-9]{8}")) return valor;
        return valor.substring(0, 5) + "-" + valor.substring(5);
    }
    public static String estadoCivil(EstadoCivil estado) {
        return switch (estado) {
            case SOLTEIRO -> "solteiro";
            case CASADO -> "casado";
            case DIVORCIADO -> "divorciado";
            case VIUVO -> "viúvo";
            case UNIAO_ESTAVEL -> "união estável";
            case SEPARADO -> "separado";
        };
    }
    public static String estadoCivil(EstadoCivil estado, SexoDocumento sexo) {
        if (estado == null) return null;
        String texto=estadoCivil(estado);
        return sexo==SexoDocumento.FEMININO && estado!=EstadoCivil.UNIAO_ESTAVEL
                ? texto.substring(0,texto.length()-1)+"a" : texto;
    }
    public static String limpar(String valor) { return valor==null ? null : valor.strip().replaceAll("\\s+", " "); }
    public static boolean brasileira(String valor) {
        return valor!=null && Set.of("BRASIL","BRASILEIRO","BRASILEIRA").contains(limpar(valor).toUpperCase(PT_BR));
    }
    public static String nacionalidade(String valor, SexoDocumento sexo) {
        if (sexo != null && brasileira(valor)) return sexo==SexoDocumento.FEMININO ? "brasileira" : "brasileiro";
        return valor==null ? null : limpar(valor).toLowerCase(PT_BR);
    }
    public static String uf(String valor) { return valor==null ? null : limpar(valor).toUpperCase(PT_BR); }
    public static boolean ufValida(String valor) {
        try { UfBrasil.valueOf(uf(valor)); return true; }
        catch (IllegalArgumentException | NullPointerException e) { return false; }
    }
    public static String orgaoExpedidor(String valor) {
        if (valor==null) return null;
        String texto=valor.replaceAll("\\s+", "").toUpperCase(PT_BR);
        var orgaoUf = Pattern.compile("^([\\p{L}\\p{N}]{2,30})[/\\-]([A-Z]{2})$").matcher(texto);
        if (orgaoUf.matches() && ufValida(orgaoUf.group(2))) return orgaoUf.group(1) + "/" + orgaoUf.group(2);
        var match=Pattern.compile("^(?:SSP[/\\-]?)?([A-Z]{2})$").matcher(texto);
        return match.matches() && ufValida(match.group(1)) ? "SSP/"+match.group(1) : texto;
    }
    public static boolean orgaoExpedidorValido(String valor) {
        String orgao = orgaoExpedidor(valor);
        return orgao != null && orgao.matches("[\\p{L}\\p{N}]{2,30}/[A-Z]{2}")
            && com.caio.controle_clientes.validation.RgUtil.extrairUf(orgao) != null;
    }
    public static String rg(String valor) {
        return com.caio.controle_clientes.validation.RgUtil.formatar(valor);
    }
    public static String nome(String valor) {
        if (valor==null || valor.isBlank()) return limpar(valor);
        String[] palavras=limpar(valor).split(" ");
        Set<String> ligacoes=Set.of("da","de","do","das","dos","e");
        for (int i=0;i<palavras.length;i++) {
            String palavra=palavras[i], lower=palavra.toLowerCase(PT_BR);
            if (i>0 && ligacoes.contains(lower)) { palavras[i]=lower; continue; }
            // Preserve existing internal capitals such as McDonald and D'Ávila.
            if (!palavra.equals(lower) && !palavra.equals(palavra.toUpperCase(PT_BR))
                    && Character.isUpperCase(palavra.codePointAt(0))) continue;
            var matcher=Pattern.compile("[\\p{L}\\p{M}]+").matcher(lower);
            StringBuilder texto=new StringBuilder();
            while (matcher.find()) {
                String parte=matcher.group(); int primeiro=parte.offsetByCodePoints(0,1);
                matcher.appendReplacement(texto,java.util.regex.Matcher.quoteReplacement(parte.substring(0,primeiro).toUpperCase(PT_BR)+parte.substring(primeiro)));
            }
            matcher.appendTail(texto); palavras[i]=texto.toString();
        }
        return String.join(" ",palavras);
    }
    public static String cidade(String valor) { return nome(valor); }
    public static String bairro(String valor) { return nome(valor); }
    public static String logradouro(String valor) { return nome(valor); }
    public static String profissao(String valor) {
        if(valor==null || valor.isBlank()) return limpar(valor);
        String texto=limpar(valor).toLowerCase(PT_BR);
        int primeiro=texto.offsetByCodePoints(0,1);
        return texto.substring(0,primeiro).toUpperCase(PT_BR)+texto.substring(primeiro);
    }
    public static String formatarProfissaoParaContrato(String valor) {
        return valor == null ? null : valor.trim().toLowerCase(PT_BR);
    }
    public static void pessoa(java.util.Map<String, String> campos, String prefixo, ParteContratoDTO p) {
        if (p == null) return;
        campos.put(prefixo + "_NOME", nome(p.nomeCompleto()));
        campos.put(prefixo + "_NACIONALIDADE", nacionalidade(p.nacionalidade(), p.sexoDocumento()));
        campos.put(prefixo + "_ESTADO_CIVIL", p.sexoDocumento() == null ? null : estadoCivil(p.estadoCivil(), p.sexoDocumento()));
        campos.put(prefixo + "_PROFISSAO", formatarProfissaoParaContrato(p.profissao()));
        campos.put(prefixo + "_RG", com.caio.controle_clientes.validation.RgUtil.formatar(p.rg(),
                com.caio.controle_clientes.validation.RgUtil.extrairUf(p.rgOrgaoExpedidor())));
        campos.put(prefixo + "_RG_ORGAO", orgaoExpedidor(p.rgOrgaoExpedidor()));
        campos.put(prefixo + "_CPF", cpf(p.cpf()));
        campos.put(prefixo + "_ENDERECO", endereco(p));
    }
    public static String endereco(ParteContratoDTO p) {
        String complemento = p.enderecoComplemento();
        return logradouro(p.enderecoRua()) + ", Nº " + p.enderecoNumero()
                + (complemento == null || complemento.isBlank() ? "" : ", " + complemento)
                + ", " + bairro(p.enderecoBairro()) + ", Cep " + cep(p.enderecoCep())
                + ", " + cidade(p.enderecoCidade()) + " — " + uf(p.enderecoEstado());
    }
}
