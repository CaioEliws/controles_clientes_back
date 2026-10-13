package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContratoService {
    private final EmprestimoRepositorio emprestimoRepositorio;

    @Transactional(readOnly = true)
    public DadosContratoDTO buscarDadosContrato(Long perfilId, Long idCliente, Long idEmprestimo) {
        return montarDados(perfilId, idCliente, idEmprestimo, null);
    }

    @Transactional(readOnly = true)
    public DadosContratoDTO buscarDadosContratoModeloFixo(Long perfilId, Long idCliente,
                                                         Long idEmprestimo, ParteContratoDTO credorFixo) {
        if (credorFixo == null) {
            throw new RegraNegocioException("Credor fixo do modelo é obrigatório.");
        }
        return montarDados(perfilId, idCliente, idEmprestimo, credorFixo);
    }

    private DadosContratoDTO montarDados(Long perfilId, Long idCliente, Long idEmprestimo,
                                        ParteContratoDTO credorFixo) {
        Emprestimo e = emprestimoRepositorio.buscarDadosContrato(idEmprestimo, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Empréstimo não encontrado para o perfil informado."));
        if (e.getPerfil() == null || !perfilId.equals(e.getPerfil().getId())) {
            throw new RegraNegocioException("Empréstimo não encontrado para o perfil informado.");
        }
        if (e.getCliente() == null || !idCliente.equals(e.getCliente().getId())) {
            throw new RegraNegocioException("Este empréstimo não pertence ao cliente informado.");
        }
        validarPerfil(e.getCliente(), perfilId, "Devedor");
        if (e.getFiador() == null) {
            throw new RegraNegocioException("O empréstimo não possui fiador cadastrado.");
        }
        validarPerfil(e.getFiador(), perfilId, "Fiador");

        ParteContratoDTO credor = credorFixo == null ? mapearCredor(e.getPerfil()) : credorFixo;
        ParteContratoDTO devedor = mapearCliente(e.getCliente());
        ParteContratoDTO fiador = mapearCliente(e.getFiador());
        List<String> erros = new ArrayList<>();
        validarParte(credor, "credor", "nomeCompleto", erros);
        validarParte(devedor, "devedor", "nome", erros);
        validarParte(fiador, "fiador", "nome", erros);
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(String.join(" ", erros));
        }

        EmprestimoContratoDTO emprestimo = new EmprestimoContratoDTO(
                e.getValorEmprestado(), e.getTaxaDeJuros(), e.getValorParcela(),
                e.getValorTotalEmprestimo(), e.getQuantidadeParcelas(), e.getDataDoEmprestimo(),
                e.getInicioPagamento(), e.getFinalPagamento(), e.getFormaPagamento(),
                e.getFrequenciaPagamento(), e.getTipoContrato(), e.getEmprestimoStatus());
        List<ParcelaContratoDTO> parcelas = e.getParcelas().stream()
                .sorted(Comparator.comparing(Parcelas::getNumeroParcela))
                .map(p -> new ParcelaContratoDTO(p.getNumeroParcela(), p.getDataVencimento(), p.getValorParcela()))
                .toList();
        return new DadosContratoDTO(e.getId(), credor, devedor, fiador, emprestimo, parcelas);
    }

    private void validarPerfil(Cliente cliente, Long perfilId, String parte) {
        if (cliente.getPerfil() == null || !perfilId.equals(cliente.getPerfil().getId())) {
            throw new RegraNegocioException(parte + " não encontrado para o perfil informado.");
        }
    }

    private ParteContratoDTO mapearCredor(Perfil p) {
        return new ParteContratoDTO(p.getNomeCompleto(), p.getNacionalidade(), p.getEstadoCivil(),
                p.getProfissao(), p.getRg(), p.getRgOrgaoExpedidor(), p.getCpf(), p.getEnderecoRua(),
                p.getEnderecoNumero(), p.getEnderecoComplemento(), p.getEnderecoBairro(),
                p.getEnderecoCep(), p.getEnderecoCidade(), p.getEnderecoEstado());
    }

    private ParteContratoDTO mapearCliente(Cliente p) {
        return new ParteContratoDTO(p.getNome(), p.getNacionalidade(), p.getEstadoCivil(),
                p.getProfissao(), p.getRg(), p.getRgOrgaoExpedidor(), p.getCpf(), p.getEnderecoRua(),
                p.getEnderecoNumero(), p.getEnderecoComplemento(), p.getEnderecoBairro(),
                p.getEnderecoCep(), p.getEnderecoCidade(), p.getEnderecoEstado(), p.getSexoDocumento());
    }

    private void validarParte(ParteContratoDTO p, String parte, String campoNome, List<String> erros) {
        List<String> faltantes = new ArrayList<>();
        obrigatorio(faltantes, campoNome, p.nomeCompleto());
        obrigatorio(faltantes, "nacionalidade", p.nacionalidade());
        obrigatorio(faltantes, "estadoCivil", p.estadoCivil());
        obrigatorio(faltantes, "profissao", p.profissao());
        obrigatorio(faltantes, "rg", p.rg());
        obrigatorio(faltantes, "rgOrgaoExpedidor", p.rgOrgaoExpedidor());
        obrigatorio(faltantes, "cpf", p.cpf());
        obrigatorio(faltantes, "enderecoRua", p.enderecoRua());
        obrigatorio(faltantes, "enderecoNumero", p.enderecoNumero());
        obrigatorio(faltantes, "enderecoBairro", p.enderecoBairro());
        obrigatorio(faltantes, "enderecoCep", p.enderecoCep());
        obrigatorio(faltantes, "enderecoCidade", p.enderecoCidade());
        obrigatorio(faltantes, "enderecoEstado", p.enderecoEstado());
        if (!faltantes.isEmpty()) {
            erros.add("Dados do " + parte + " incompletos: " + String.join(", ", faltantes) + ".");
        }
    }

    private void obrigatorio(List<String> faltantes, String campo, Object valor) {
        if (valor == null || (valor instanceof String texto && texto.isBlank())) {
            faltantes.add(campo);
        }
    }
}
