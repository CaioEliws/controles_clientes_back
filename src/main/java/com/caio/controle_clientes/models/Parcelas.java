package com.caio.controle_clientes.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "parcelas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_parcela_emprestimo_numero",
                columnNames = {"emprestimo_id", "numero_parcela"}
        )
)
public class Parcelas {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "numero_parcela", nullable = false)
    private Integer numeroParcela;

    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorParcela;

    @Setter
    @Column(nullable = false)
    private LocalDate dataVencimento;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParcelaStatus status = ParcelaStatus.PENDENTE;

    @Setter
    private LocalDate dataPagamento;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emprestimo_id", nullable = false)
    private Emprestimo emprestimo;

    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    public Parcelas() {}

    public Parcelas(Integer numeroParcela,
                    BigDecimal valorParcela,
                    LocalDate dataVencimento,
                    ParcelaStatus status,
                    LocalDate dataPagamento,
                    Emprestimo emprestimo,
                    BigDecimal valorPago) {
        this.numeroParcela = numeroParcela;
        this.valorParcela = valorParcela;
        this.dataVencimento = dataVencimento;
        this.status = status;
        this.dataPagamento = dataPagamento;
        this.emprestimo = emprestimo;
        this.valorPago = valorPago;
    }
}