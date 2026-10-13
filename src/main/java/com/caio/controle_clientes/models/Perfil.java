package com.caio.controle_clientes.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "perfil")
@Getter
@Setter
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    private String nomeCompleto;

    private String nacionalidade;

    @Enumerated(EnumType.STRING)
    private EstadoCivil estadoCivil;

    private String profissao;

    private String rg;

    private String rgOrgaoExpedidor;

    private String cpf;

    private String enderecoRua;

    private Integer enderecoNumero;

    private String enderecoComplemento;

    private String enderecoBairro;

    private String enderecoCep;

    private String enderecoCidade;

    private String enderecoEstado;

    private LocalDateTime dataCriacao = LocalDateTime.now();
}
