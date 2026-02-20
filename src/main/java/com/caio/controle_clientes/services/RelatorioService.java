package com.caio.controle_clientes.services;

import com.caio.controle_clientes.dto.HistoricoClienteDTO;
import com.caio.controle_clientes.dto.InadimplenteDTO;
import com.caio.controle_clientes.models.Cliente;
import com.caio.controle_clientes.models.Emprestimo;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.models.Parcelas;
import com.caio.controle_clientes.repository.EmprestimoRepositorio;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RelatorioService {
    private EmprestimoRepositorio emprestimoRepositorio;
    private ParcelaRepositorio parcelaRepositorio;

    public RelatorioService(EmprestimoRepositorio emprestimoRepositorio,
                            ParcelaRepositorio parcelaRepositorio) {
        this.emprestimoRepositorio = emprestimoRepositorio;
        this.parcelaRepositorio = parcelaRepositorio;
    }

    public HistoricoClienteDTO gerarHistoricoCliente(Cliente cliente) {
        List<Emprestimo> emprestimos = emprestimoRepositorio.findByCliente(cliente);

        BigDecimal totalEmprestado = BigDecimal.ZERO;
        BigDecimal totalPago = BigDecimal.ZERO;
        BigDecimal totalAberto = BigDecimal.ZERO;

        int parcelasAtrasadas = 0;
        int parcelasPagas = 0;
        int parcelasAVencer = 0;
        int totalParcelas = 0;

        for (Emprestimo e : emprestimos) {
            totalEmprestado = totalEmprestado.add(e.getValorEmprestado());

            List<Parcelas> parcelas = parcelaRepositorio.findByEmprestimo(e);

            totalParcelas += parcelas.size();

            for (Parcelas p : parcelas) {
                if (p.getStatus() == ParcelaStatus.PAGO) {
                    totalPago = totalPago.add(p.getValorParcela());
                    parcelasPagas++;
                }

                if (p.getStatus() == ParcelaStatus.PENDENTE) {
                    totalAberto = totalAberto.add(p.getValorParcela());
                    parcelasAVencer++;
                }

                if (p.getStatus() == ParcelaStatus.ATRASADO) {
                    totalAberto = totalAberto.add(p.getValorParcela());
                    parcelasAtrasadas++;
                }
            }
        }

        return new HistoricoClienteDTO(
                totalEmprestado,
                totalPago,
                totalAberto,
                parcelasAtrasadas,
                emprestimos.size(),
                totalParcelas,
                parcelasPagas,
                parcelasAVencer
        );
    }

    public List<InadimplenteDTO> gerarRelatorioInadimplentes() {
        List <Parcelas> parcelasAtrasadas =
                parcelaRepositorio.findParcelasAtrasadasComCliente(ParcelaStatus.ATRASADO);

        if (parcelasAtrasadas.isEmpty()) {
            return List.of();
        }

        return parcelasAtrasadas.stream()
                .collect(Collectors.groupingBy(p -> p.getEmprestimo().getCliente()))
                .entrySet()
                .stream()
                .map(entry -> {
                    Cliente cliente = entry.getKey();
                    List<Parcelas> parcelasCliente = entry.getValue();

                    BigDecimal totalEmAtraso =
                            parcelasCliente.stream()
                                    .map(Parcelas::getValorParcela)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                    LocalDate ultimoVencimento =
                            parcelasCliente.stream()
                                    .map(Parcelas::getDataVencimento)
                                    .max(LocalDate::compareTo)
                                    .orElse(null);

                    return new InadimplenteDTO(
                            cliente.getNome(),
                            parcelasCliente.size(),
                            totalEmAtraso,
                            ultimoVencimento
                    );
                })
                .toList();
    }
}
