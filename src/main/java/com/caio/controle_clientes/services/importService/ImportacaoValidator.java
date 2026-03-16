package com.caio.controle_clientes.services.importService;

import com.caio.controle_clientes.services.importService.dto.LinhaImportacaoDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ImportacaoValidator {

    public String validar(LinhaImportacaoDTO dto) {

        if (dto.dataEmprestimo() == null) {
            return "Data do empréstimo inválida | valor recebido: " + dto.dataEmprestimo();
        }

        if (dto.nomeClienteOriginal() == null || dto.nomeClienteOriginal().isBlank()) {
            return "Nome cliente original vazio | valor recebido: '" + dto.nomeClienteOriginal() + "'";
        }

        if (dto.nomeCliente() == null || dto.nomeCliente().isBlank()) {
            return "Nome cliente normalizado inválido | valor recebido: '" + dto.nomeCliente() + "'";
        }

        if (dto.valorEmprestado() == null || dto.valorEmprestado().compareTo(BigDecimal.ZERO) <= 0) {
            return "Valor emprestado inválido | valor recebido: " + dto.valorEmprestado();
        }

        if (dto.totalParcelas() == null || dto.totalParcelas() <= 0) {
            return "Total parcelas inválido | valor recebido: " + dto.totalParcelas();
        }

        if (dto.parcelasPagas() == null || dto.parcelasPagas() < 0) {
            return "Parcelas pagas inválidas | valor recebido: " + dto.parcelasPagas();
        }

        if (dto.parcelasPagas() > dto.totalParcelas()) {
            return "Parcelas pagas maior que total de parcelas | pagas="
                    + dto.parcelasPagas()
                    + " total="
                    + dto.totalParcelas();
        }

        if (dto.valorParcela() == null || dto.valorParcela().compareTo(BigDecimal.ZERO) <= 0) {
            return "Valor parcela inválido | valor recebido: " + dto.valorParcela();
        }

        if (dto.inicioPgto() == null) {
            return "Data início pagamento inválida | valor recebido: " + dto.inicioPgto();
        }

        if (dto.fimPgto() != null && dto.fimPgto().isBefore(dto.inicioPgto())) {
            return "Data final menor que data inicial | inicio="
                    + dto.inicioPgto()
                    + " fim="
                    + dto.fimPgto();
        }

        if (dto.taxaJurosPercentual() != null && dto.taxaJurosPercentual().compareTo(BigDecimal.ZERO) < 0) {
            return "Taxa de juros inválida | valor recebido: " + dto.taxaJurosPercentual();
        }

        if (dto.valorRecebido() != null && dto.valorRecebido().compareTo(BigDecimal.ZERO) < 0) {
            return "Valor recebido inválido | valor recebido: " + dto.valorRecebido();
        }

        if (dto.valorAReceber() != null && dto.valorAReceber().compareTo(BigDecimal.ZERO) < 0) {
            return "Valor a receber inválido | valor recebido: " + dto.valorAReceber();
        }

        if (dto.valorTotalEmprestimos() != null && dto.valorTotalEmprestimos().compareTo(BigDecimal.ZERO) < 0) {
            return "Valor total empréstimo inválido | valor recebido: " + dto.valorTotalEmprestimos();
        }

        return null;
    }
}