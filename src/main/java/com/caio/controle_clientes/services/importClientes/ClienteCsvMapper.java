package com.caio.controle_clientes.services.importClientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.services.contrato.ContratoFormatador;
import com.caio.controle_clientes.validation.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;

@Component
public class ClienteCsvMapper {
    static final List<String> CAMPOS = List.of("nome", "nacionalidade", "estadoCivil", "profissao", "rg",
        "rgOrgaoExpedidor", "cpf", "enderecoRua", "enderecoNumero", "enderecoComplemento", "enderecoBairro",
        "enderecoCep", "enderecoCidade", "enderecoEstado", "sexoDocumento", "nomeIndicador");

    public Map<String, Object> mapear(Map<String, String> csv) {
        var dados = new LinkedHashMap<String, Object>();
        for (String campo : CAMPOS) {
            String valor = ContratoFormatador.limpar(csv.get(campo.equals("nome") ? "nomeCompleto" : campo));
            if (valor == null || valor.isBlank()) continue;
            if (valor.length() > 255) throw new IllegalArgumentException("Campo " + campo + " excede 255 caracteres.");
            dados.put(campo, valor);
        }
        String cpf = (String) dados.get("cpf");
        if (cpf != null) {
            if (!CpfUtil.validarEstrutura(cpf)) throw new IllegalArgumentException("CPF inválido: informe 11 dígitos, com ou sem máscara.");
            dados.put("cpf", ContratoFormatador.cpf(CpfUtil.normalizar(cpf)));
        }
        if (dados.containsKey("estadoCivil")) dados.put("estadoCivil", estadoCivil((String) dados.get("estadoCivil")));
        if (dados.containsKey("sexoDocumento")) {
            try { dados.put("sexoDocumento", SexoDocumento.valueOf(chave((String) dados.get("sexoDocumento")))); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("sexoDocumento inválido: use MASCULINO ou FEMININO."); }
        }
        if (dados.containsKey("nacionalidade") && dados.containsKey("sexoDocumento")
                && ContratoFormatador.brasileira((String) dados.get("nacionalidade"))) dados.put("nacionalidade", "BRASIL");
        if (dados.containsKey("rgOrgaoExpedidor")) {
            String orgao = ContratoFormatador.orgaoExpedidor((String) dados.get("rgOrgaoExpedidor"));
            if (!ContratoFormatador.orgaoExpedidorValido(orgao)) throw new IllegalArgumentException("rgOrgaoExpedidor inválido: informe órgão/UF com uma UF brasileira válida.");
            dados.put("rgOrgaoExpedidor", orgao);
        }
        if (dados.containsKey("rg")) {
            String rg = (String) dados.get("rg");
            if (!RgUtil.validar(rg)) throw new IllegalArgumentException("RG inválido: use 7 a 13 caracteres significativos e X somente no final.");
            dados.put("rg", RgUtil.formatar(rg, RgUtil.extrairUf((String) dados.get("rgOrgaoExpedidor"))));
        }
        if (dados.containsKey("enderecoCep")) {
            String cep = ((String) dados.get("enderecoCep")).replaceAll("[\\s-]", "");
            if (!cep.matches("[0-9]{8}")) throw new IllegalArgumentException("CEP inválido: informe 8 dígitos, com ou sem hífen.");
            dados.put("enderecoCep", ContratoFormatador.cep(cep));
        }
        if (dados.containsKey("enderecoEstado")) {
            String uf = ContratoFormatador.uf((String) dados.get("enderecoEstado"));
            if (!ContratoFormatador.ufValida(uf)) throw new IllegalArgumentException("enderecoEstado inválido: use uma UF brasileira.");
            dados.put("enderecoEstado", uf);
        }
        if (dados.containsKey("enderecoNumero")) {
            try { dados.put("enderecoNumero", Integer.valueOf((String) dados.get("enderecoNumero"))); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("enderecoNumero inválido: informe um número inteiro."); }
        }
        return dados;
    }
    private EstadoCivil estadoCivil(String valor) {
        return switch (chave(valor)) {
            case "SOLTEIRO", "SOLTEIRA" -> EstadoCivil.SOLTEIRO;
            case "CASADO", "CASADA" -> EstadoCivil.CASADO;
            case "DIVORCIADO", "DIVORCIADA" -> EstadoCivil.DIVORCIADO;
            case "VIUVO", "VIUVA" -> EstadoCivil.VIUVO;
            case "UNIAO_ESTAVEL" -> EstadoCivil.UNIAO_ESTAVEL;
            case "SEPARADO", "SEPARADA" -> EstadoCivil.SEPARADO;
            default -> throw new IllegalArgumentException("estadoCivil inválido.");
        };
    }
    private String chave(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }
    Map<String, Object> lerCliente(Cliente cliente) {
        var bean = new BeanWrapperImpl(cliente); var campos = new LinkedHashMap<String, Object>();
        CAMPOS.forEach(campo -> campos.put(campo, bean.getPropertyValue(campo)));
        return campos;
    }
    void aplicar(Cliente cliente, Map<String, Object> campos) {
        var bean = new BeanWrapperImpl(cliente);
        campos.forEach(bean::setPropertyValue);
    }
    boolean equivalentes(String campo, Object banco, Object csv) {
        if (campo.equals("cpf")) return Objects.equals(CpfUtil.normalizar((String) banco), CpfUtil.normalizar((String) csv));
        if (campo.equals("rg")) return Objects.equals(RgUtil.normalizar((String) banco), RgUtil.normalizar((String) csv));
        if (campo.equals("enderecoCep")) return ((String) banco).replaceAll("[\\s-]", "").equals(((String) csv).replaceAll("[\\s-]", ""));
        if (banco instanceof String a && csv instanceof String b) {
            if (campo.equals("nacionalidade") && ContratoFormatador.brasileira(a) && ContratoFormatador.brasileira(b)) return true;
            return ContratoFormatador.limpar(a).equalsIgnoreCase(ContratoFormatador.limpar(b));
        }
        return Objects.equals(banco, csv);
    }
}
