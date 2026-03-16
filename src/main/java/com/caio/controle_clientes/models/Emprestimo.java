package com.caio.controle_clientes.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "emprestimos")
public class Emprestimo {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    private LocalDate dataDoEmprestimo;

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    @Getter
    @Setter
    private BigDecimal valorEmprestado;

    @Getter
    @Setter
    private Integer quantidadeParcelas;

    @Getter
    @Setter
    private BigDecimal taxaDeJuros;

    @Getter
    @Setter
    private BigDecimal valorAReceber;

    @Getter
    @Setter
    private BigDecimal valorRecebido;

    @Getter
    @Setter
    private BigDecimal valorTotalEmprestimo;

    @Getter
    @Setter
    private BigDecimal valorParcela;

    @Getter
    @Setter
    private LocalDate inicioPagamento;

    @Getter
    @Setter
    private LocalDate finalPagamento;

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrequenciaPagamento frequenciaPagamento;

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoContrato tipoContrato;

    @Getter
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmprestimoStatus emprestimoStatus;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @Getter
    @Setter
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Getter
    @Setter
    @JsonIgnore
    @OneToMany(mappedBy = "emprestimo",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<Parcelas> parcelas = new ArrayList<>();

    public Emprestimo() {}

}