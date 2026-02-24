package com.caio.controle_clientes.services;

import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.repository.ClienteRepositorio;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteService {
    private final ClienteRepositorio clienteRepositorio;
    private final EmprestimoRepositorio emprestimoRepositorio;

    public ClienteService(ClienteRepositorio clienteRepositorio,
                          EmprestimoRepositorio emprestimoRepositorio) {
        this.clienteRepositorio = clienteRepositorio;
        this.emprestimoRepositorio = emprestimoRepositorio;
    }

    public Cliente buscarPorNome(String nome) {
        return clienteRepositorio
                .findByNomeIgnoreCase(nome)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));
    }

    public void criarCliente(String nome,
                             String indicador,
                             String enderecoRua,
                             String enderecoBairro,
                             Integer enderecoNumero) {
        boolean  existe = clienteRepositorio
                .findByNomeIgnoreCase(nome)
                .isPresent();

        if (existe) {
            throw new RuntimeException("Cliente já existe!");
        }

        Cliente cliente = new Cliente();
        cliente.setNome(nome);
        cliente.setNomeIndicador(indicador);
        cliente.setEnderecoRua(enderecoRua);
        cliente.setEnderecoBairro(enderecoBairro);
        cliente.setEnderecoNumero(enderecoNumero);
        cliente.setEnderecoNumero(enderecoNumero);

        clienteRepositorio.save(cliente);
    }

    public void updateNameCliente(Long id, String nome) {
        Cliente cliente = getClienteById(id);
        cliente.setNome(nome);
        clienteRepositorio.save(cliente);
    }

    public void updateEnderecoCliente(Long id, String rua, String bairro, Integer numero) {
        Cliente cliente = getClienteById(id);

        cliente.setEnderecoRua(rua);
        cliente.setEnderecoBairro(bairro);
        cliente.setEnderecoNumero(numero);

        clienteRepositorio.save(cliente);
    }

    @Transactional
    public void deletarCliente(Long clienteId) {
        Cliente cliente = clienteRepositorio.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        boolean possuiEmprestimos =
                emprestimoRepositorio.existsByClienteId(clienteId);

        if (possuiEmprestimos) {
            throw new RuntimeException(
                    "Não é possível deletar o cliente pois ele possui empréstimos cadastrados."
            );
        }

        clienteRepositorio.delete(cliente);
    }

    public Cliente getClienteById(Long id) {
        return clienteRepositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
    }

    public List<Cliente> listarTodos() {
        return clienteRepositorio.findAll();
    }

}
