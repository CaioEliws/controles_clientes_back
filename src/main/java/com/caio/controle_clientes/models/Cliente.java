package com.caio.controle_clientes.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "clientes")
@Data
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String nomeIndicador;

    private String enderecoRua;

    private String enderecoBairro;

    private  Integer enderecoNumero;

    private LocalDate criadoEm = LocalDate.now();

    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @JsonIgnore
    @OneToMany(mappedBy = "cliente",
            cascade = CascadeType.ALL)
    private List<Emprestimo> emprestimos;

    public Cliente() {}

    public Cliente(String nome,
                   String nomeIndicador,
                   LocalDate criadoEm,
                   List<Emprestimo> emprestimos) {
        this.nome = nome;
        this.nomeIndicador = nomeIndicador;
        this.criadoEm = criadoEm;
        this.emprestimos = emprestimos;
    }
}
