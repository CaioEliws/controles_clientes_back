package com.caio.controle_clientes.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "emprestimos")
public class Emprestimo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    private LocalDate dataDoEmprestimo;

    @Setter
    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    @Setter
    private BigDecimal valorEmprestado;

    @Setter
    private Integer quantidadeParcelas;

    @Setter
    private BigDecimal taxaDeJuros;

    @Setter
    private BigDecimal valorAReceber;

    @Setter
    private BigDecimal valorRecebido;

    @Setter
    private BigDecimal valorTotalEmprestimo;

    @Setter
    private BigDecimal valorParcela;

    @Setter
    private LocalDate inicioPagamento;

    @Setter
    private LocalDate finalPagamento;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrequenciaPagamento frequenciaPagamento;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoContrato tipoContrato;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmprestimoStatus emprestimoStatus;

    @Setter
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Setter
    @JsonIgnore
    @OneToMany(mappedBy = "emprestimo",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<Parcelas> parcelas = new ArrayList<>();

    public Emprestimo() {}

    public Long getId() {
        return id;
    }

    public LocalDate getDataDoEmprestimo() {
        return dataDoEmprestimo;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public BigDecimal getValorEmprestado() {
        return valorEmprestado;
    }

    public Integer getQuantidadeParcelas() {
        return quantidadeParcelas;
    }

    public BigDecimal getTaxaDeJuros() {
        return taxaDeJuros;
    }

    public BigDecimal getValorAReceber() {
        return valorAReceber;
    }

    public BigDecimal getValorRecebido() {
        return valorRecebido;
    }

    public BigDecimal getValorTotalEmprestimo() {
        return valorTotalEmprestimo;
    }

    public BigDecimal getValorParcela() {
        return valorParcela;
    }

    public LocalDate getInicioPagamento() {
        return inicioPagamento;
    }

    public LocalDate getFinalPagamento() {
        return finalPagamento;
    }

    public FrequenciaPagamento getFrequenciaPagamento() {
        return frequenciaPagamento;
    }

    public TipoContrato getTipoContrato() {
        return tipoContrato;
    }

    public EmprestimoStatus getEmprestimoStatus() {
        return emprestimoStatus;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public List<Parcelas> getParcelas() {
        return parcelas;
    }

}