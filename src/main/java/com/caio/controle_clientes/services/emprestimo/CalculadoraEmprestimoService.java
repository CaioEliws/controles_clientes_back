package com.caio.controle_clientes.services.emprestimo;

import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.FrequenciaPagamento;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        LocalDate primeiroVencimento = calcularPrimeiroVencimento(frequencia);

        for (int numeroParcela = 1; numeroParcela <= emprestimo.getQuantidadeParcelas(); numeroParcela++) {
            BigDecimal jurosParcela = saldoDevedor.multiply(taxa);
            BigDecimal amortizacao = valorParcela.subtract(jurosParcela);
            saldoDevedor = saldoDevedor.subtract(amortizacao);

            LocalDate vencimento = calcularVencimentoPorIndice(
                    primeiroVencimento,
                    numeroParcela - 1,
                    frequencia
            );

            parcelas.add(criarParcela(emprestimo, numeroParcela, valorParcela, vencimento));
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
        parcela.setPerfil(emprestimo.getPerfil());
        return parcela;
    }

    private LocalDate calcularPrimeiroVencimento(FrequenciaPagamento freq) {
        LocalDate base = LocalDate.now();

        return switch (freq) {
            case DIARIO -> base.plusDays(1);
            case SEMANAL -> base.plusWeeks(1);
            case QUINZENAL -> base.plusDays(15);
            case MENSAL -> base.plusMonths(1);
        };
    }

    private LocalDate calcularVencimentoPorIndice(LocalDate primeiroVencimento,
                                                  int indice,
                                                  FrequenciaPagamento freq) {
        return switch (freq) {
            case DIARIO -> primeiroVencimento.plusDays(indice);
            case SEMANAL -> primeiroVencimento.plusWeeks(indice);
            case QUINZENAL -> primeiroVencimento.plusDays(indice * 15L);
            case MENSAL -> calcularVencimentoMensal(primeiroVencimento, indice);
        };
    }

    private LocalDate calcularVencimentoMensal(LocalDate primeiroVencimento, int indice) {
        int diaBase = primeiroVencimento.getDayOfMonth();

        LocalDate mesBase = primeiroVencimento.plusMonths(indice);
        int ultimoDiaDoMes = mesBase.lengthOfMonth();
        int diaNoMes = Math.min(diaBase, ultimoDiaDoMes);

        return mesBase.withDayOfMonth(diaNoMes);
    }
}