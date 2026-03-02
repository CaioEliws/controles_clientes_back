package com.caio.controle_clientes.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

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

    private LocalDate dataDoEmprestimo;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    private BigDecimal valorEmprestado;

    private Integer quantidadeParcelas;

    private BigDecimal taxaDeJuros;

    private BigDecimal valorAReceber;

    private BigDecimal valorRecebido;

    private BigDecimal valorTotalEmprestimo;

    private BigDecimal valorParcela;

    private LocalDate inicioPagamento;

    private LocalDate finalPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrequenciaPagamento frequenciaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmprestimoStatus emprestimoStatus;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @JsonIgnore
    @OneToMany(mappedBy = "emprestimo",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<Parcelas> parcelas = new ArrayList<>();

    public Emprestimo() {}

    public FrequenciaPagamento getFrequenciaPagamento() {
        return frequenciaPagamento;
    }

    public void setFrequenciaPagamento(FrequenciaPagamento frequenciaPagamento) {
        this.frequenciaPagamento = frequenciaPagamento;
    }


    public Long getId() { return id; }

    public LocalDate getDataDoEmprestimo() { return dataDoEmprestimo; }
    public void setDataDoEmprestimo(LocalDate dataDoEmprestimo) { this.dataDoEmprestimo = dataDoEmprestimo; }

    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(FormaPagamento formaPagamento) { this.formaPagamento = formaPagamento; }

    public BigDecimal getValorEmprestado() { return valorEmprestado; }
    public void setValorEmprestado(BigDecimal valorEmprestado) { this.valorEmprestado = valorEmprestado; }

    public Integer getQuantidadeParcelas() { return quantidadeParcelas; }
    public void setQuantidadeParcelas(Integer quantidadeParcelas) { this.quantidadeParcelas = quantidadeParcelas; }

    public BigDecimal getTaxaDeJuros() { return taxaDeJuros; }
    public void setTaxaDeJuros(BigDecimal taxaDeJuros) { this.taxaDeJuros = taxaDeJuros; }

    public BigDecimal getValorAReceber() { return valorAReceber; }
    public void setValorAReceber(BigDecimal valorAReceber) { this.valorAReceber = valorAReceber; }

    public BigDecimal getValorRecebido() { return valorRecebido; }
    public void setValorRecebido(BigDecimal valorRecebido) { this.valorRecebido = valorRecebido; }

    public BigDecimal getValorTotalEmprestimo() { return valorTotalEmprestimo; }
    public void setValorTotalEmprestimo(BigDecimal valorTotalEmprestimo) { this.valorTotalEmprestimo = valorTotalEmprestimo; }

    public BigDecimal getValorParcela() { return valorParcela; }
    public void setValorParcela(BigDecimal valorParcela) { this.valorParcela = valorParcela; }

    public LocalDate getInicioPagamento() { return inicioPagamento; }
    public void setInicioPagamento(LocalDate inicioPagamento) { this.inicioPagamento = inicioPagamento; }

    public LocalDate getFinalPagamento() { return finalPagamento; }
    public void setFinalPagamento(LocalDate finalPagamento) { this.finalPagamento = finalPagamento; }

    public EmprestimoStatus getEmprestimoStatus() { return emprestimoStatus; }
    public void setEmprestimoStatus(EmprestimoStatus emprestimoStatus) { this.emprestimoStatus = emprestimoStatus; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public List<Parcelas> getParcelas() { return parcelas; }
    public void setParcelas(List<Parcelas> parcelas) { this.parcelas = parcelas; }
}