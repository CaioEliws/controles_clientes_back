package com.caio.controle_clientes.services.perfil;

import com.caio.controle_clientes.dto.Perfil.CriarPerfilRequest;
import com.caio.controle_clientes.dto.Perfil.AtualizarPerfilDadosDTO;
import com.caio.controle_clientes.dto.Perfil.PerfilDetalhadoDTO;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.dto.Perfil.PerfilResponse;
import com.caio.controle_clientes.models.Perfil;
import com.caio.controle_clientes.repository.PerfilRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PerfilService {

    private final PerfilRepositorio perfilRepositorio;

    public PerfilService(PerfilRepositorio perfilRepositorio) {
        this.perfilRepositorio = perfilRepositorio;
    }

    public List<PerfilResponse> listarTodos() {
        return perfilRepositorio.findAll()
                .stream()
                .map(PerfilResponse::fromEntity)
                .toList();
    }

    public PerfilResponse criar(CriarPerfilRequest request) {
        String nome = request.getNome().trim();

        perfilRepositorio.findByNomeIgnoreCase(nome)
                .ifPresent(p -> {
                    throw new RegraNegocioException("Já existe um perfil com esse nome");
                });

        Perfil perfil = new Perfil();
        perfil.setNome(nome);

        Perfil salvo = perfilRepositorio.save(perfil);

        return PerfilResponse.fromEntity(salvo);
    }

    public Perfil buscarPorId(Long perfilId) {
        return perfilRepositorio.findById(perfilId)
                .orElseThrow(() -> new RegraNegocioException("Perfil não encontrado"));
    }

    public PerfilDetalhadoDTO buscarDetalhado(Long perfilId, Long id) {
        validarPerfilAtivo(perfilId, id);
        return PerfilDetalhadoDTO.fromEntity(buscarPorId(id));
    }

    @Transactional
    public PerfilDetalhadoDTO atualizarDados(Long perfilId, Long id, AtualizarPerfilDadosDTO dto) {
        validarPerfilAtivo(perfilId, id);
        Perfil perfil = buscarPorId(id);
        String nome = dto.nome().trim();
        perfilRepositorio.findByNomeIgnoreCase(nome)
                .filter(p -> !Objects.equals(p.getId(), id))
                .ifPresent(p -> {
                    throw new RegraNegocioException("Já existe um perfil com esse nome");
                });

        perfil.setNomeCompleto(dto.nomeCompleto());
        perfil.setNacionalidade(dto.nacionalidade());
        perfil.setEstadoCivil(dto.estadoCivil());
        perfil.setProfissao(dto.profissao());
        perfil.setRg(com.caio.controle_clientes.validation.RgUtil.formatar(dto.rg(),
                com.caio.controle_clientes.validation.RgUtil.extrairUf(dto.rgOrgaoExpedidor())));
        perfil.setRgOrgaoExpedidor(dto.rgOrgaoExpedidor());
        perfil.setCpf(dto.cpf());
        perfil.setEnderecoRua(dto.enderecoRua());
        perfil.setEnderecoNumero(dto.enderecoNumero());
        perfil.setEnderecoComplemento(dto.enderecoComplemento());
        perfil.setEnderecoBairro(dto.enderecoBairro());
        perfil.setEnderecoCep(dto.enderecoCep());
        perfil.setEnderecoCidade(dto.enderecoCidade());
        perfil.setEnderecoEstado(dto.enderecoEstado());
        perfil.setNome(nome);
        return PerfilDetalhadoDTO.fromEntity(perfilRepositorio.save(perfil));
    }

    private void validarPerfilAtivo(Long perfilId, Long id) {
        if (perfilId == null || !perfilId.equals(id)) {
            throw new RegraNegocioException("Perfil solicitado não corresponde ao perfil ativo.");
        }
    }
}
