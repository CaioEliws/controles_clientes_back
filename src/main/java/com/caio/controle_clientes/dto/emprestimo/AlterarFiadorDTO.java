package com.caio.controle_clientes.dto.emprestimo;

import jakarta.validation.constraints.NotNull;

public record AlterarFiadorDTO(@NotNull(message = "Fiador é obrigatório") Long fiadorId) {
}
