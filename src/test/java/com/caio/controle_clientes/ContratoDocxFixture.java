package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import org.springframework.beans.BeanWrapperImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

record ContratoDocxFixture(Perfil perfil, Cliente devedor, Cliente fiador, Emprestimo emprestimo) {
    static ContratoDocxFixture criar(PerfilRepositorio perfis, ClienteRepositorio clientes, EmprestimoService service) {
        Perfil p = new Perfil();
        p.setNome("Perfil " + UUID.randomUUID());
        preencher(p);
        p.setNomeCompleto("Pessoa do Perfil diferente do Edson");
        p.setCpf("00000000001");
        perfis.save(p);
        Cliente d = pessoa(p, "Devedor de teste", "00000000002", "Rua A");
        Cliente f = pessoa(p, "Fiador de teste", "00000000003", "Rua Fiador A");
        clientes.save(d);
        clientes.save(f);
        Emprestimo e = service.criarEmprestimo(p.getId(), d, new BigDecimal("3751.50"), 3, BigDecimal.ZERO,
                FormaPagamento.PIX, FrequenciaPagamento.MENSAL, TipoContrato.FISICO, f.getId());
        e.setDataDoEmprestimo(LocalDate.of(2026, 10, 2));
        LocalDate inicio = LocalDate.of(2026, 11, 15);
        for (int i = 0; i < e.getParcelas().size(); i++) e.getParcelas().get(i).setDataVencimento(inicio.plusMonths(i));
        e.setInicioPagamento(inicio);
        e.setFinalPagamento(inicio.plusMonths(2));
        return new ContratoDocxFixture(p, d, f, e);
    }
    private static Cliente pessoa(Perfil p, String nome, String cpf, String rua) {
        Cliente c = new Cliente();
        c.setPerfil(p);
        c.setSexoDocumento(SexoDocumento.FEMININO);
        preencher(c);
        c.setNome(nome);
        c.setCpf(cpf);
        c.setEnderecoRua(rua);
        return c;
    }
    private static void preencher(Object pessoa) {
        var b = new BeanWrapperImpl(pessoa);
        Map.ofEntries(Map.entry("nacionalidade", "brasileira"), Map.entry("estadoCivil", EstadoCivil.UNIAO_ESTAVEL),
                Map.entry("profissao", "Professor"), Map.entry("rg", "00123-X"), Map.entry("rgOrgaoExpedidor", "SSP/SP"),
                Map.entry("enderecoRua", "Rua perfil"), Map.entry("enderecoNumero", 15), Map.entry("enderecoBairro", "Centro"),
                Map.entry("enderecoCep", "00100-000"), Map.entry("enderecoCidade", "Cidade"), Map.entry("enderecoEstado", "SP"))
                .forEach(b::setPropertyValue);
    }
}
