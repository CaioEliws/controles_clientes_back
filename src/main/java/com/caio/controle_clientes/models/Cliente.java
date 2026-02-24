package com.caio.controle_clientes.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

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

    public Long getId() {
        return id;
    }

    public String getEnderecoRua() {
        return enderecoRua;
    }

    public void setEnderecoRua(String enderecoRua) {
        this.enderecoRua = enderecoRua;
    }

    public String getEnderecoBairro() {
        return enderecoBairro;
    }

    public void setEnderecoBairro(String enderecoBairro) {
        this.enderecoBairro = enderecoBairro;
    }

    public Integer getEnderecoNumero() {
        return enderecoNumero;
    }

    public void setEnderecoNumero(Integer enderecoNumero) {
        this.enderecoNumero = enderecoNumero;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getNomeIndicador() {
        return nomeIndicador;
    }

    public void setNomeIndicador(String nomeIndicador) {
        this.nomeIndicador = nomeIndicador;
    }

    public LocalDate getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDate criadoEm) {
        this.criadoEm = criadoEm;
    }

    public List<Emprestimo> getEmprestimos() {
        return emprestimos;
    }

    public void setEmprestimos(List<Emprestimo> emprestimos) {
        this.emprestimos = emprestimos;
    }
}
