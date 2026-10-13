package com.caio.controle_clientes.services;

import com.caio.controle_clientes.dto.cliente.AtualizarClienteDadosDTO;
import com.caio.controle_clientes.dto.cliente.ClienteCreateDTO;
import com.caio.controle_clientes.dto.cliente.ClienteDetalhadoDTO;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Perfil;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.services.perfil.PerfilService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import com.caio.controle_clientes.services.contrato.ContratoFormatador;

@Service
public class ClienteService {

    private final ClienteRepositorio clienteRepositorio;
    private final EmprestimoRepositorio emprestimoRepositorio;
    private final PerfilService perfilService;

    public ClienteService(ClienteRepositorio clienteRepositorio,
                          EmprestimoRepositorio emprestimoRepositorio,
                          PerfilService perfilService) {
        this.clienteRepositorio = clienteRepositorio;
        this.emprestimoRepositorio = emprestimoRepositorio;
        this.perfilService = perfilService;
    }

    public Cliente buscarPorNome(Long perfilId, String nome) {
        return clienteRepositorio
                .findByNomeIgnoreCaseAndPerfilId(nome, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado!"));
    }

    @Transactional
    public void criarCliente(Long perfilId,
                             String nome,
                             String indicador,
                             String enderecoRua,
                             String enderecoBairro,
                             Integer enderecoNumero) {

        criarCliente(perfilId, new ClienteCreateDTO(nome, indicador, enderecoRua,
                enderecoBairro, enderecoNumero));
    }

    @Transactional
    public void criarCliente(Long perfilId, ClienteCreateDTO dto) {

        boolean existe = clienteRepositorio
                .findByNomeIgnoreCaseAndPerfilId(ContratoFormatador.limpar(dto.nome()), perfilId)
                .isPresent();

        if (existe) {
            throw new RegraNegocioException("Cliente já existe!");
        }

        Perfil perfil = perfilService.buscarPorId(perfilId);
        validarCpfUnico(perfilId, dto.cpf(), null);

        Cliente cliente = new Cliente();
        cliente.setNome(dto.nome());
        if (dto.sexoDocumento()!=null) cliente.setSexoDocumento(dto.sexoDocumento());
        cliente.setNomeIndicador(dto.nomeIndicador());
        cliente.setNacionalidade(dto.nacionalidade());
        cliente.setEstadoCivil(dto.estadoCivil());
        cliente.setProfissao(dto.profissao());
        cliente.setRg(dto.rg());
        cliente.setRgOrgaoExpedidor(dto.rgOrgaoExpedidor());
        cliente.setCpf(dto.cpf());
        cliente.setEnderecoRua(dto.enderecoRua());
        cliente.setEnderecoNumero(dto.enderecoNumero());
        cliente.setEnderecoComplemento(dto.enderecoComplemento());
        cliente.setEnderecoBairro(dto.enderecoBairro());
        cliente.setEnderecoCep(dto.enderecoCep());
        cliente.setEnderecoCidade(dto.enderecoCidade());
        cliente.setEnderecoEstado(dto.enderecoEstado());
        cliente.setPerfil(perfil);

        normalizarCadastro(cliente);
        clienteRepositorio.save(cliente);
    }

    public ClienteDetalhadoDTO buscarDetalhado(Long perfilId, Long id) {
        return ClienteDetalhadoDTO.fromEntity(getClienteById(perfilId, id));
    }

    @Transactional
    public ClienteDetalhadoDTO atualizarDados(Long perfilId, Long id, AtualizarClienteDadosDTO dto) {
        Cliente cliente = getClienteById(perfilId, id);
        validarCpfUnico(perfilId, dto.cpf(), id);
        cliente.setNome(dto.nome());
        if (dto.sexoDocumento()!=null) cliente.setSexoDocumento(dto.sexoDocumento());
        cliente.setNomeIndicador(dto.nomeIndicador());
        cliente.setNacionalidade(dto.nacionalidade());
        cliente.setEstadoCivil(dto.estadoCivil());
        cliente.setProfissao(dto.profissao());
        cliente.setRg(dto.rg());
        cliente.setRgOrgaoExpedidor(dto.rgOrgaoExpedidor());
        cliente.setCpf(dto.cpf());
        cliente.setEnderecoRua(dto.enderecoRua());
        cliente.setEnderecoNumero(dto.enderecoNumero());
        cliente.setEnderecoComplemento(dto.enderecoComplemento());
        cliente.setEnderecoBairro(dto.enderecoBairro());
        cliente.setEnderecoCep(dto.enderecoCep());
        cliente.setEnderecoCidade(dto.enderecoCidade());
        cliente.setEnderecoEstado(dto.enderecoEstado());
        normalizarCadastro(cliente);
        return ClienteDetalhadoDTO.fromEntity(clienteRepositorio.save(cliente));
    }

    private void normalizarCadastro(Cliente c) {
        c.setNome(ContratoFormatador.limpar(c.getNome()));
        c.setNomeIndicador(ContratoFormatador.limpar(c.getNomeIndicador()));
        c.setNacionalidade(ContratoFormatador.limpar(c.getNacionalidade()));
        if (c.getSexoDocumento()!=null && (c.getNacionalidade()==null || c.getNacionalidade().isBlank()
                || ContratoFormatador.brasileira(c.getNacionalidade()))) c.setNacionalidade("BRASIL");
        c.setProfissao(ContratoFormatador.limpar(c.getProfissao()));
        c.setRg(com.caio.controle_clientes.validation.RgUtil.formatar(c.getRg(),
                com.caio.controle_clientes.validation.RgUtil.extrairUf(c.getRgOrgaoExpedidor())));
        c.setRgOrgaoExpedidor(ContratoFormatador.orgaoExpedidor(c.getRgOrgaoExpedidor()));
        c.setCpf(ContratoFormatador.limpar(c.getCpf()));
        c.setEnderecoCep(ContratoFormatador.limpar(c.getEnderecoCep()));
        c.setEnderecoRua(ContratoFormatador.limpar(c.getEnderecoRua()));
        c.setEnderecoComplemento(ContratoFormatador.limpar(c.getEnderecoComplemento()));
        c.setEnderecoBairro(ContratoFormatador.limpar(c.getEnderecoBairro()));
        c.setEnderecoCidade(ContratoFormatador.limpar(c.getEnderecoCidade()));
        c.setEnderecoEstado(ContratoFormatador.uf(c.getEnderecoEstado()));
        if (c.getEnderecoEstado()!=null && !c.getEnderecoEstado().isBlank()
                && !ContratoFormatador.ufValida(c.getEnderecoEstado()))
            throw new RegraNegocioException("UF do endereço inválida. Selecione uma UF brasileira.");
    }

    private void validarCpfUnico(Long perfilId, String cpf, Long clienteId) {
        String normalizado = normalizarCpf(cpf);
        if (normalizado == null) {
            return;
        }

        boolean duplicado = clienteRepositorio.findAllByPerfilId(perfilId).stream()
                .filter(c -> !Objects.equals(c.getId(), clienteId))
                .anyMatch(c -> normalizado.equals(normalizarCpf(c.getCpf())));

        if (duplicado) {
            throw new RegraNegocioException("Já existe um cliente com esse CPF no perfil informado.");
        }
    }

    // Compara sem máscara; o valor armazenado é sempre o enviado pelo consumidor.
    private String normalizarCpf(String cpf) {
        return com.caio.controle_clientes.validation.CpfUtil.normalizar(cpf);
    }

    public void updateNameCliente(Long perfilId, Long id, String nome) {
        Cliente cliente = getClienteById(perfilId, id);
        cliente.setNome(ContratoFormatador.limpar(nome));
        clienteRepositorio.save(cliente);
    }

    public void updateEnderecoCliente(Long perfilId, Long id, String rua, String bairro, Integer numero) {
        Cliente cliente = getClienteById(perfilId, id);

        cliente.setEnderecoRua(ContratoFormatador.limpar(rua));
        cliente.setEnderecoBairro(ContratoFormatador.limpar(bairro));
        cliente.setEnderecoNumero(numero);

        clienteRepositorio.save(cliente);
    }

    @Transactional
    public void deletarCliente(Long perfilId, Long clienteId) {
        Cliente cliente = clienteRepositorio.findByIdAndPerfilId(clienteId, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado"));

        boolean possuiEmprestimos =
                emprestimoRepositorio.existsByClienteIdAndPerfilId(clienteId, perfilId);

        if (possuiEmprestimos) {
            throw new RegraNegocioException(
                    "Não é possível deletar o cliente pois ele possui empréstimos cadastrados."
            );
        }

        if (emprestimoRepositorio.existsByFiadorIdAndPerfilId(clienteId, perfilId)) {
            throw new RegraNegocioException(
                    "Não é possível deletar o cliente pois ele está sendo utilizado como fiador.");
        }

        clienteRepositorio.delete(cliente);
    }

    public Cliente getClienteById(Long perfilId, Long id) {
        return clienteRepositorio.findByIdAndPerfilId(id, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado"));
    }

    public List<Cliente> listarTodos(Long perfilId) {
        return clienteRepositorio.findAllByPerfilId(perfilId);
    }

    public List<ClienteDetalhadoDTO> listarDetalhadosComContagem(Long perfilId) {
        var lista = listarTodos(perfilId);
        var contagens = emprestimoRepositorio.contarPorCliente(perfilId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        EmprestimoRepositorio.ContagemPorCliente::getClienteId,
                        EmprestimoRepositorio.ContagemPorCliente::getQuantidade));
        return lista.stream().map(cliente -> ClienteDetalhadoDTO.fromEntity(
                cliente, contagens.getOrDefault(cliente.getId(), 0L))).toList();
    }
}
