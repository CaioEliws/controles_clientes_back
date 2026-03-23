package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.parcela.*;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import com.caio.controle_clientes.services.emprestimo.EmprestimoService;
import com.caio.controle_clientes.services.parcela.ParcelaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/parcelas")
@RequiredArgsConstructor
public class ParcelaController {

    private final EmprestimoService emprestimoService;
    private final ParcelaRepositorio parcelaRepositorio;
    private final ParcelaService parcelaService;

    @PostMapping("/pagar")
    public ResponseEntity<Void> pagarParcela(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestBody PagarParcelaDTO dto
    ) {
        emprestimoService.pagarParcela(
                perfilId,
                dto.idEmprestimo(),
                dto.numeroParcela()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pagar-parcial")
    public ResponseEntity<Void> pagarParcelaParcial(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestBody PagarParcelaParcialDTO dto
    ) {
        emprestimoService.pagarParcelaParcial(
                perfilId,
                dto.idEmprestimo(),
                dto.numeroParcela(),
                dto.valorPago()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/desfazer-pagamento")
    public ResponseEntity<Void> desfazerPagamentoParcela(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestBody DesfazerPagamentoParcelaDTO dto
    ) {
        parcelaService.desfazerPagamentoParcela(
                perfilId,
                dto.idEmprestimo(),
                dto.numeroParcela()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ParcelaResponseDTO>> listarPorStatus(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestParam ParcelaStatus status
    ) {
        parcelaService.atualizarParcelasAtrasadas(perfilId);

        List<ParcelaResponseDTO> response = parcelaRepositorio
                .findParcelasComEmprestimoEClientePorStatus(status, perfilId)
                .stream()
                .map(parcelaService::toDTO)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/vencendo-hoje")
    public ResponseEntity<List<ParcelaResponseDTO>> vencendoHoje(
            @RequestHeader("X-Perfil-Id") Long perfilId
    ) {
        parcelaService.atualizarParcelasAtrasadas(perfilId);

        List<ParcelaResponseDTO> response = parcelaRepositorio.findParcelasVencendoHoje(
                        LocalDate.now(),
                        List.of(ParcelaStatus.PENDENTE, ParcelaStatus.PARCIAL),
                        perfilId
                ).stream()
                .map(parcelaService::toDTO)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/mes-atual")
    public ResponseEntity<List<ParcelaResponseDTO>> parcelasDoMes(
            @RequestHeader("X-Perfil-Id") Long perfilId
    ) {
        parcelaService.atualizarParcelasAtrasadas(perfilId);

        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.withDayOfMonth(1);
        LocalDate fim = hoje.withDayOfMonth(hoje.lengthOfMonth());

        List<ParcelaResponseDTO> response = parcelaRepositorio.findParcelasDoMes(inicio, fim, perfilId)
                .stream()
                .map(parcelaService::toDTO)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/alterar")
    public ResponseEntity<Void> alterarParcela(
            @RequestHeader("X-Perfil-Id") Long perfilId,
            @RequestBody AlterarParcelaDTO dto
    ) {
        parcelaService.alterarParcela(
                perfilId,
                dto.idEmprestimo(),
                dto.numeroParcela(),
                dto.novaData(),
                dto.novoValor()
        );

        return ResponseEntity.noContent().build();
    }
}