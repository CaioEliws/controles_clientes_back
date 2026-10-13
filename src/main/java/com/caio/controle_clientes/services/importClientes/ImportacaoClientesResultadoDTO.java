package com.caio.controle_clientes.services.importClientes;

import java.util.List;

public record ImportacaoClientesResultadoDTO(boolean previa, int totalLinhas, int criados, int existentes,
        int atualizados, int ignorados, int conflitos, int erros, boolean detalhesTruncados,
        List<Detalhe> detalhes, int importaveisSemRessalvas, int importaveisComAviso, int importadosComAviso,
        int bloqueados, int semWord, int duplicidadesRevisar, int clientesEncontradosPorNome,
        int clientesEncontradosPorCpf, int camposPreenchidos, int conflitosPreservados, int ambiguos,
        int conflitosIdentidade, int clientesAtualizados) {
    public record Detalhe(int linha, String nome, String cpf, String status, String motivo,
            List<String> camposConflitantes, List<String> camposPreenchidos, String statusCsv,
            String classificacao, boolean comAviso, List<String> camposAusentes, String match) {}
}
