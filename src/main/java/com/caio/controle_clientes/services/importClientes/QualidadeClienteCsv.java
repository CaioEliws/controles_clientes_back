package com.caio.controle_clientes.services.importClientes;

import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;

/** Qualidade da extração, separada da ação de criar/completar cadastro no banco. */
@Component
public class QualidadeClienteCsv {
    private static final List<String> NECESSARIOS_REVISAO = List.of("nomeCompleto", "cpf", "rg", "nacionalidade",
        "estadoCivil", "profissao", "enderecoRua", "enderecoNumero", "enderecoCidade", "enderecoEstado", "sexoDocumento");
    public record Avaliacao(String classificacao, String motivo, List<String> camposAusentes, boolean elegivel, boolean aviso) {}
    public Avaliacao avaliar(Map<String, String> csv, long ocorrenciasCpf) {
        String status = status(csv);
        List<String> faltantes = NECESSARIOS_REVISAO.stream().filter(c -> csv.getOrDefault(c, "").isBlank()).toList();
        String observacao = resumir(csv.get("observacoes"));
        if (faltantes.isEmpty()) observacao = observacao.replace("Campos ausentes:", "Observação histórica de campos ausentes:");
        if (status.equals("SEM_WORD")) return bloqueado("SEM_WORD", "Pasta sem arquivos Word.", faltantes);
        if (status.equals("INCOMPLETO")) return bloqueado("BLOQUEADO", motivoAusentes(faltantes, observacao), faltantes);
        if (!Set.of("OK", "REVISAR", "DUPLICIDADE").contains(status))
            return bloqueado("BLOQUEADO", juntar("Status de extração " + (status.isBlank() ? "ausente" : status) + "; revisão necessária.", observacao), faltantes);
        if (status.equals("OK")) return new Avaliacao("IMPORTAVEL", observacao, List.of(), true, false);
        if (!faltantes.isEmpty()) return bloqueado(status.equals("DUPLICIDADE") ? "BLOQUEADO_DUPLICIDADE" : "BLOQUEADO", motivoAusentes(faltantes, observacao), faltantes);
        if (status.equals("DUPLICIDADE")) {
            String original = chave(csv.getOrDefault("observacoes", ""));
            boolean irresolvido = original.matches("(?s).*(irresolvid|nao consolidad|sem decisao|sem consolidacao|incompativeis|ambigu).*");
            boolean fontes = original.contains("documentos considerados:") || original.contains("dados consolidados") || original.contains("registro consolidado");
            boolean decisoes = !original.contains("divergencias:") || original.contains("utilizados dados mais recentes") || original.contains("divergencias resolvidas");
            if (ocorrenciasCpf != 1 || irresolvido || !fontes || !decisoes)
                return bloqueado("BLOQUEADO_DUPLICIDADE", juntar("Duplicidade sem registro final único ou consolidação segura documentada; revisão necessária.", observacao), List.of());
        }
        return new Avaliacao("IMPORTAVEL_COM_AVISO", juntar("Campos finais completos; dados do CSV podem ser importados após confirmação.", observacao), List.of(), true, true);
    }
    private Avaliacao bloqueado(String tipo, String motivo, List<String> faltantes) { return new Avaliacao(tipo, motivo, faltantes, false, false); }
    public String status(Map<String, String> csv) { return csv.getOrDefault("statusExtracao", "").strip().toUpperCase(Locale.ROOT); }
    private String motivoAusentes(List<String> faltantes, String obs) {
        return juntar(faltantes.isEmpty() ? "Extrator marcou INCOMPLETO; revisão necessária, sem inventar dados." : "Campos ausentes: " + String.join(", ", faltantes) + ".", obs);
    }
    public String juntar(String motivo, String obs) { return obs == null || obs.isBlank() ? motivo : motivo + " " + obs; }
    /** Não devolve qualificações, CPFs, RGs completos ou caminhos de arquivos da extração. */
    public String resumir(String observacoes) {
        if (observacoes == null || observacoes.isBlank()) return "";
        var notas = new LinkedHashSet<String>();
        for (String parte : observacoes.split("\\s*\\|\\s*")) {
            String chave = chave(parte);
            if (chave.startsWith("qualificacao original:") || chave.startsWith("fonte de ") || chave.startsWith("documentos considerados:")) continue;
            String resumo;
            if (chave.startsWith("divergencias:")) resumo = chave.contains("utilizados dados mais recentes")
                ? "Divergência histórica entre documentos; o extrator selecionou dados mais recentes por campo/bloco."
                : "Divergências entre documentos; revisar a consolidação dos dados.";
            else if (chave.startsWith("identidade encontrada em multiplas pastas:")) resumo = "Mesmo CPF encontrado em múltiplas pastas.";
            else if (chave.startsWith("nome da pasta divergente")) resumo = "Nome da pasta divergente do documento; conferir identidade.";
            else {
                resumo = parte.strip().replaceAll("[\\r\\n]+", " ")
                    .replaceAll("(?i)(?:[A-Z]:\\\\|/)[^|]*", "[fonte omitida]")
                    .replaceAll("[0-9][0-9. /-]{5,}[0-9Xx]", "[documento/dados omitidos]");
                if (resumo.length() > 180) resumo = resumo.substring(0, 180) + "…";
            }
            if (!resumo.isBlank()) notas.add(resumo);
            if (notas.size() == 3) break;
        }
        return String.join(" ", notas);
    }
    private String chave(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
