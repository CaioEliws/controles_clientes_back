package com.caio.controle_clientes.services;

import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.FrequenciaPagamento;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CalculadoraEmprestimoService {

    public BigDecimal calcularParcela(BigDecimal valorEmprestado,
                                      BigDecimal taxaJuros,
                                      int quantidadeParcelas) {

        BigDecimal i = taxaJuros
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        if (i.compareTo(BigDecimal.ZERO) == 0) {
            return valorEmprestado.divide(
                    BigDecimal.valueOf(quantidadeParcelas),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        BigDecimal umMaisI = BigDecimal.ONE.add(i);
        BigDecimal potencia = umMaisI.pow(quantidadeParcelas);
        BigDecimal potenciaNegativa = BigDecimal.ONE
                .divide(potencia, 10, RoundingMode.HALF_UP);

        BigDecimal denominador = BigDecimal.ONE.subtract(potenciaNegativa);
        BigDecimal numerador = valorEmprestado.multiply(i);

        return numerador.divide(denominador, 2, RoundingMode.HALF_UP);
    }

    public List<Parcelas> gerarParcelas(Emprestimo emprestimo) {
        FrequenciaPagamento frequencia = validarFrequencia(emprestimo);

        List<Parcelas> parcelas = new ArrayList<>();
        BigDecimal saldoDevedor = emprestimo.getValorEmprestado();

        BigDecimal taxa = emprestimo.getTaxaDeJuros()
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal valorParcela = calcularParcela(
                emprestimo.getValorEmprestado(),
                emprestimo.getTaxaDeJuros(),
                emprestimo.getQuantidadeParcelas()
        ).setScale(2, RoundingMode.HALF_UP);

        LocalDate vencimento = calcularPrimeiroVencimento(frequencia);

        for (int numeroParcela = 1; numeroParcela <= emprestimo.getQuantidadeParcelas(); numeroParcela++) {
            BigDecimal jurosParcela = saldoDevedor.multiply(taxa);
            BigDecimal amortizacao = valorParcela.subtract(jurosParcela);
            saldoDevedor = saldoDevedor.subtract(amortizacao);

            parcelas.add(criarParcela(emprestimo, numeroParcela, valorParcela, vencimento));
            vencimento = proximoVencimento(vencimento, frequencia);
        }

        return parcelas;
    }

    private FrequenciaPagamento validarFrequencia(Emprestimo emprestimo) {
        if (emprestimo.getFrequenciaPagamento() == null) {
            throw new RuntimeException("Frequência de pagamento não informada");
        }
        return emprestimo.getFrequenciaPagamento();
    }

    private Parcelas criarParcela(Emprestimo emprestimo,
                                  int numeroParcela,
                                  BigDecimal valorParcela,
                                  LocalDate vencimento) {
        Parcelas parcela = new Parcelas();
        parcela.setNumeroParcela(numeroParcela);
        parcela.setValorParcela(valorParcela);
        parcela.setDataVencimento(vencimento);
        parcela.setStatus(ParcelaStatus.PENDENTE);
        parcela.setValorPago(BigDecimal.ZERO);
        parcela.setEmprestimo(emprestimo);
        return parcela;
    }

    private LocalDate calcularPrimeiroVencimento(FrequenciaPagamento freq) {
        LocalDate base = LocalDate.now();

        return switch (freq) {
            case DIARIO -> ajustarSeDomingo(base.plusDays(1));
            case SEMANAL -> base.plusWeeks(1);
            case QUINZENAL -> base.plusDays(15);
            case MENSAL -> base.plusMonths(1);
        };
    }

    private LocalDate proximoVencimento(LocalDate atual, FrequenciaPagamento freq) {
        return switch (freq) {
            case DIARIO -> ajustarSeDomingo(atual.plusDays(1));
            case SEMANAL -> atual.plusWeeks(1);
            case QUINZENAL -> atual.plusDays(15);
            case MENSAL -> atual.plusMonths(1);
        };
    }

    private LocalDate ajustarSeDomingo(LocalDate data) {
        return data.getDayOfWeek() == DayOfWeek.SUNDAY ? data.plusDays(1) : data;
    }
}