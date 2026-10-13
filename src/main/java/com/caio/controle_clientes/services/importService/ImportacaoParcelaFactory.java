package com.caio.controle_clientes.services.importService;

import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.FrequenciaPagamento;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImportacaoParcelaFactory {
    public record DetalheBackup(int numero, BigDecimal valor, BigDecimal pago,
            LocalDate vencimento, LocalDate pagamento) {}

    public List<Parcelas> restaurar(Emprestimo emp, List<DetalheBackup> detalhes) {
        return detalhes.stream().sorted(java.util.Comparator.comparingInt(DetalheBackup::numero)).map(d -> {
            var p = new Parcelas();
            p.setNumeroParcela(d.numero()); p.setValorParcela(d.valor()); p.setValorPago(d.pago());
            p.setDataVencimento(d.vencimento()); p.setDataPagamento(d.pagamento()); p.setEmprestimo(emp);
            p.setStatus(d.pago().compareTo(d.valor()) == 0 ? ParcelaStatus.PAGO
                    : d.vencimento().isBefore(LocalDate.now()) ? ParcelaStatus.ATRASADO
                    : d.pago().signum() > 0 ? ParcelaStatus.PARCIAL : ParcelaStatus.PENDENTE);
            return p;
        }).toList();
    }

    public List<Parcelas> gerar(Emprestimo emp, Integer pagas) {
        List<Parcelas> list = new ArrayList<>();

        LocalDate vencimento = emp.getInicioPagamento();
        FrequenciaPagamento freq = emp.getFrequenciaPagamento() == null
                ? FrequenciaPagamento.MENSAL
                : emp.getFrequenciaPagamento();

        if (freq == FrequenciaPagamento.DIARIO) {
            vencimento = ajustarSeDomingo(vencimento);
        }

        for (int i = 1; i <= emp.getQuantidadeParcelas(); i++) {
            Parcelas p = new Parcelas();
            p.setNumeroParcela(i);
            p.setEmprestimo(emp);
            p.setValorParcela(emp.getValorParcela());
            p.setDataVencimento(vencimento);

            if (i <= pagas) {
                p.setStatus(ParcelaStatus.PAGO);
                p.setValorPago(emp.getValorParcela());
                p.setDataPagamento(vencimento);
            } else {
                p.setStatus(ParcelaStatus.PENDENTE);
                p.setValorPago(BigDecimal.ZERO);
            }

            list.add(p);
            vencimento = freq == FrequenciaPagamento.MENSAL
                    ? emp.getInicioPagamento().plusMonths(i)
                    : proximoVencimento(vencimento, freq);
        }

        return list;
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
