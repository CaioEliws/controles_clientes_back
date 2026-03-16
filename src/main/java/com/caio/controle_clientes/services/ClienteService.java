package com.caio.controle_clientes.services;

import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Perfil;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.services.perfil.PerfilService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

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
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));
    }

    public void criarCliente(Long perfilId,
                             String nome,
                             String indicador,
                             String enderecoRua,
                             String enderecoBairro,
                             Integer enderecoNumero) {

        boolean existe = clienteRepositorio
                .findByNomeIgnoreCaseAndPerfilId(nome, perfilId)
                .isPresent();

        if (existe) {
            throw new RuntimeException("Cliente já existe!");
        }

        Perfil perfil = perfilService.buscarPorId(perfilId);

        Cliente cliente = new Cliente();
        cliente.setNome(nome);
        cliente.setNomeIndicador(indicador);
        cliente.setEnderecoRua(enderecoRua);
        cliente.setEnderecoBairro(enderecoBairro);
        cliente.setEnderecoNumero(enderecoNumero);
        cliente.setPerfil(perfil);

        clienteRepositorio.save(cliente);
    }

    public void updateNameCliente(Long perfilId, Long id, String nome) {
        Cliente cliente = getClienteById(perfilId, id);
        cliente.setNome(nome);
        clienteRepositorio.save(cliente);
    }

    public void updateEnderecoCliente(Long perfilId, Long id, String rua, String bairro, Integer numero) {
        Cliente cliente = getClienteById(perfilId, id);

        cliente.setEnderecoRua(rua);
        cliente.setEnderecoBairro(bairro);
        cliente.setEnderecoNumero(numero);

        clienteRepositorio.save(cliente);
    }

    @Transactional
    public void deletarCliente(Long perfilId, Long clienteId) {
        Cliente cliente = clienteRepositorio.findByIdAndPerfilId(clienteId, perfilId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        boolean possuiEmprestimos =
                emprestimoRepositorio.existsByClienteIdAndPerfilId(clienteId, perfilId);

        if (possuiEmprestimos) {
            throw new RuntimeException(
                    "Não é possível deletar o cliente pois ele possui empréstimos cadastrados."
            );
        }

        clienteRepositorio.delete(cliente);
    }

    public Cliente getClienteById(Long perfilId, Long id) {
        return clienteRepositorio.findByIdAndPerfilId(id, perfilId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
    }

    public List<Cliente> listarTodos(Long perfilId) {
        return clienteRepositorio.findAllByPerfilId(perfilId);
    }
}