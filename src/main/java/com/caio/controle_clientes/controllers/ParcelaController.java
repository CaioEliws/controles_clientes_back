package com.caio.controle_clientes.controllers;

import com.caio.controle_clientes.dto.AlterarParcelaDTO;
import com.caio.controle_clientes.dto.PagarParcelaDTO;
import com.caio.controle_clientes.dto.PagarParcelaParcialDTO;
import com.caio.controle_clientes.dto.ParcelaResponseDTO;
import com.caio.controle_clientes.models.ParcelaStatus;
import com.caio.controle_clientes.repository.ParcelaRepositorio;
import com.caio.controle_clientes.services.EmprestimoService;
import com.caio.controle_clientes.services.ParcelaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/parcelas")
public class ParcelaController {
    private final EmprestimoService emprestimoService;
    private final ParcelaRepositorio parcelaRepositorio;
    private final ParcelaService parcelaService;

    public ParcelaController(EmprestimoService emprestimoService,
                             ParcelaRepositorio parcelaRepositorio,
                             ParcelaService parcelaService) {
        this.emprestimoService = emprestimoService;
        this.parcelaRepositorio = parcelaRepositorio;
        this.parcelaService = parcelaService;
    }

    @PostMapping("/pagar")
    public ResponseEntity<Void> pagarParcela(
            @RequestBody PagarParcelaDTO dto
    ) {
        emprestimoService.pagarParcela(
                dto.idEmprestimo(),
                dto.numeroParcela()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pagar-parcial")
    public ResponseEntity<Void> pagarParcelaParcial(
            @RequestBody PagarParcelaParcialDTO dto
    ) {
        emprestimoService.pagarParcelaParcial(
                dto.idEmprestimo(),
                dto.numeroParcela(),
                dto.valorPago()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<ParcelaResponseDTO> listarPorStatus(
            @RequestParam ParcelaStatus status
    ) {
        parcelaService.atualizarParcelasAtrasadas();

        return parcelaRepositorio
                .findParcelasComEmprestimoEClientePorStatus(status)
                .stream()
                .map(parcelaService::toDTO)
                .toList();
    }

    @GetMapping("/vencendo-hoje")
    public List<ParcelaResponseDTO> vencendoHoje() {
        parcelaService.atualizarParcelasAtrasadas();

        return parcelaRepositorio.findParcelasVencendoHoje(
                        List.of(ParcelaStatus.PENDENTE, ParcelaStatus.PARCIAL),
                        LocalDate.now()
                ).stream()
                .map(parcelaService::toDTO)
                .toList();
    }

    @GetMapping("/mes-atual")
    public List<ParcelaResponseDTO> parcelasDoMes() {
        parcelaService.atualizarParcelasAtrasadas();

        LocalDate inicio = LocalDate.now().withDayOfMonth(1);
        LocalDate fim = LocalDate.now()
                .withDayOfMonth(LocalDate.now().lengthOfMonth());

        return parcelaRepositorio.findParcelasDoMes(inicio, fim)
                .stream()
                .map(parcelaService::toDTO)
                .toList();
    }

    @PatchMapping("/alterar")
    public ResponseEntity<Void> alterarParcela(
            @RequestBody AlterarParcelaDTO dto
    ) {
        parcelaService.alterarParcela(
                dto.idEmprestimo(),
                dto.numeroParcela(),
                dto.novaData(),
                dto.novoValor()
        );

        return ResponseEntity.noContent().build();
    }
}