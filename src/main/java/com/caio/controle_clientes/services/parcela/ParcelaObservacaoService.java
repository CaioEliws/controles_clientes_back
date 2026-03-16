package com.caio.controle_clientes.services.parcela;

import com.caio.controle_clientes.dto.parcela.ParcelaObservacaoResponseDTO;
import com.caio.controle_clientes.models.ParcelaObservacao;
import com.caio.controle_clientes.models.Parcelas;
import com.caio.controle_clientes.repository.ParcelaObservacaoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ParcelaObservacaoService {

    private final ParcelaObservacaoRepositorio observacaoRepositorio;
    private final ParcelaRepositorio parcelaRepositorio;

    public ParcelaObservacaoService(ParcelaObservacaoRepositorio observacaoRepositorio,
                                    ParcelaRepositorio parcelaRepositorio) {
        this.observacaoRepositorio = observacaoRepositorio;
        this.parcelaRepositorio = parcelaRepositorio;
    }

    @Transactional
    public void criarObservacao(Long perfilId,
                                Long idEmprestimo,
                                Integer numeroParcela,
                                String textoObservacao) {

        if (textoObservacao == null || textoObservacao.isBlank()) {
            throw new RuntimeException("Observação é obrigatória");
        }

        Parcelas parcela = parcelaRepositorio
                .findByEmprestimoIdAndPerfilIdAndNumeroParcela(idEmprestimo, perfilId, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        ParcelaObservacao observacao = new ParcelaObservacao();
        observacao.setObservacao(textoObservacao.trim());
        observacao.setParcela(parcela);
        observacao.setPerfil(parcela.getPerfil());

        observacaoRepositorio.save(observacao);
    }

    @Transactional(readOnly = true)
    public List<ParcelaObservacaoResponseDTO> listarObservacoes(Long perfilId,
                                                                Long idEmprestimo,
                                                                Integer numeroParcela) {

        Parcelas parcela = parcelaRepositorio
                .findByEmprestimoIdAndPerfilIdAndNumeroParcela(idEmprestimo, perfilId, numeroParcela)
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada"));

        return observacaoRepositorio
                .findByParcelaIdAndPerfilIdOrderByDataCriacaoDesc(parcela.getId(), perfilId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public void editarObservacao(Long perfilId,
                                 Long idObservacao,
                                 String novoTexto) {

        if (novoTexto == null || novoTexto.isBlank()) {
            throw new RuntimeException("Observação é obrigatória");
        }

        ParcelaObservacao observacao = observacaoRepositorio
                .findByIdAndPerfilId(idObservacao, perfilId)
                .orElseThrow(() -> new RuntimeException("Observação não encontrada"));

        observacao.setObservacao(novoTexto.trim());

        observacaoRepositorio.save(observacao);
    }

    @Transactional
    public void removerObservacao(Long perfilId, Long idObservacao) {
        ParcelaObservacao observacao = observacaoRepositorio
                .findByIdAndPerfilId(idObservacao, perfilId)
                .orElseThrow(() -> new RuntimeException("Observação não encontrada"));

        observacaoRepositorio.delete(observacao);
    }

    private ParcelaObservacaoResponseDTO toDTO(ParcelaObservacao observacao) {
        return new ParcelaObservacaoResponseDTO(
                observacao.getId(),
                observacao.getObservacao(),
                observacao.getDataCriacao(),
                observacao.getDataAtualizacao()
        );
    }
}
