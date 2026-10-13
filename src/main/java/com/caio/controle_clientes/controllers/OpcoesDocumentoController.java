package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.services.contrato.ContratoFormatador;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;

/** Public, static metadata, shared by create/edit forms; contains no personal data. */
@RestController
@RequestMapping("/documentos/opcoes-cadastro")
public class OpcoesDocumentoController {
    @GetMapping public Opcoes opcoes() {
        return new Opcoes(Arrays.stream(UfBrasil.values()).map(u -> new Uf(u.name(),u.descricao())).toList(),
                Arrays.stream(SexoDocumento.values()).map(s -> new Sexo(s.name(),s==SexoDocumento.FEMININO ? "Feminino" : "Masculino",
                        ContratoFormatador.nacionalidade("BRASIL",s))).toList());
    }
    public record Opcoes(List<Uf> ufs,List<Sexo> sexos) {}
    public record Uf(String codigo,String descricao) {}
    public record Sexo(String codigo,String descricao,String nacionalidade) {}
}
