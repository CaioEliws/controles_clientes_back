package com.caio.controle_clientes;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@Transactional
class PadronizacaoCadastroHttpTests {
    @Autowired WebApplicationContext context;
    @Autowired PerfilRepositorio perfis;
    @Autowired ClienteRepositorio clientes;
    @Autowired ObjectMapper json;
    Perfil perfil() { var p = new Perfil(); p.setNome("Padronização"); return perfis.save(p); }
    String cadastro(String nome, String sexo, String nacionalidade) {
        return """
            {"nome":"%s","sexoDocumento":%s,"nacionalidade":"%s","estadoCivil":"SOLTEIRO",
             "profissao":" vendedora ","rg":" 52779723x ","rgOrgaoExpedidor":"Ssp mg","cpf":"00000000002",
             "enderecoRua":" RUA  ALMIR ANTÔNIO ARTONI ","enderecoNumero":528,"enderecoBairro":"JARDIM DOS COLIBRIS",
             "enderecoCep":"00100000","enderecoCidade":"INDAIATUBA","enderecoEstado":"sp"}
            """.formatted(nome, sexo == null ? "null" : "\"" + sexo + "\"", nacionalidade);
    }
    @Test void criacaoConsultaEdicaoNormalizamSemDestruirCaixaCadastral() throws Exception {
        var p = perfil(); var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var criado = mvc.perform(post("/clientes").header("X-Perfil-Id", p.getId()).contentType("application/json")
                .content(cadastro("IRENE APARECIDA ARAÚJO DA SILVA", "FEMININO", "Brasileiro"))).andReturn().getResponse();
        assertThat(criado.getStatus()).isEqualTo(201);
        var c = clientes.findAllByPerfilId(p.getId()).get(0);
        assertThat(c.getNome()).isEqualTo("IRENE APARECIDA ARAÚJO DA SILVA");
        assertThat(c.getSexoDocumento()).isEqualTo(SexoDocumento.FEMININO);
        assertThat(c.getNacionalidade()).isEqualTo("BRASIL");
        assertThat(c.getRg()).isEqualTo("52.779.723-X"); assertThat(c.getRgOrgaoExpedidor()).isEqualTo("SSP/MG");
        assertThat(c.getEnderecoEstado()).isEqualTo("SP"); assertThat(c.getEnderecoRua()).isEqualTo("RUA ALMIR ANTÔNIO ARTONI");
        assertThat(c.getCpf()).isEqualTo("00000000002"); assertThat(c.getEnderecoCep()).isEqualTo("00100000");
        var consulta = mvc.perform(get("/clientes/" + c.getId()).header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(consulta.getStatus()).isEqualTo(200);
        var dados = json.readTree(consulta.getContentAsString());
        assertThat(dados.get("cpf").asText()).isEqualTo("000.000.000-02");
        assertThat(dados.get("enderecoCep").asText()).isEqualTo("00100-000");
        var atualizado = mvc.perform(put("/clientes/" + c.getId()).header("X-Perfil-Id", p.getId()).contentType("application/json")
                .content(cadastro("Irene", "MASCULINO", "Portuguesa"))).andReturn().getResponse();
        assertThat(atualizado.getStatus()).isEqualTo(200);
        assertThat(c.getSexoDocumento()).isEqualTo(SexoDocumento.MASCULINO);
        assertThat(c.getNacionalidade()).isEqualTo("Portuguesa");
        // Consumidores antigos que omitem o novo campo não apagam a seleção explícita.
        var legado = mvc.perform(put("/clientes/" + c.getId()).header("X-Perfil-Id", p.getId()).contentType("application/json")
                .content(cadastro("Irene", null, "Portuguesa"))).andReturn().getResponse();
        assertThat(legado.getStatus()).isEqualTo(200); assertThat(c.getSexoDocumento()).isEqualTo(SexoDocumento.MASCULINO);
    }
    @Test void clienteLegadoContinuaListandoSemSexoEOrgaoSemUfNaoEhInventado() throws Exception {
        var p = perfil(); var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var resposta = mvc.perform(post("/clientes").header("X-Perfil-Id", p.getId()).contentType("application/json")
                .content(cadastro("Legado", null, "Brasileira").replace("Ssp mg", "ssp"))).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(201);
        var c = clientes.findAllByPerfilId(p.getId()).get(0);
        assertThat(c.getSexoDocumento()).isNull(); assertThat(c.getRgOrgaoExpedidor()).isEqualTo("SSP");
        var lista = mvc.perform(get("/clientes").header("X-Perfil-Id", p.getId())).andReturn().getResponse();
        assertThat(lista.getStatus()).isEqualTo(200);
        var registro = json.readTree(lista.getContentAsString()).get(0);
        assertThat(registro.has("sexoDocumento")).isTrue();
        assertThat(registro.has("perfil")).isFalse(); assertThat(registro.has("emprestimos")).isFalse();
    }
    @Test void metadataEhFonteUnicaDas27UfsEDaNacionalidadeBrasileira() throws Exception {
        var resposta = MockMvcBuilders.webAppContextSetup(context).build().perform(get("/documentos/opcoes-cadastro")).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200); var opcoes = json.readTree(resposta.getContentAsString());
        assertThat(opcoes.get("ufs").size()).isEqualTo(27); assertThat(opcoes.get("sexos").size()).isEqualTo(2);
        assertThat(opcoes.at("/sexos/0/nacionalidade").asText()).isEqualTo("brasileiro");
        assertThat(opcoes.at("/sexos/1/nacionalidade").asText()).isEqualTo("brasileira");
    }
    @Test void rejeitaUfInvalidaESexoDesconhecidoComErroDeEntrada() throws Exception {
        var p = perfil(); var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String body : new String[]{cadastro("Inválido", "FEMININO", "BRASIL").replace("\"sp\"", "\"XX\""), cadastro("Inválido", "OUTRO", "BRASIL")}) {
            assertThat(mvc.perform(post("/clientes").header("X-Perfil-Id", p.getId()).contentType("application/json").content(body)).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(clientes.findAllByPerfilId(p.getId())).isEmpty();
    }
}
