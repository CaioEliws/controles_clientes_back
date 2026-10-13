package com.caio.controle_clientes.exceptions;

public class ParcelaPagaException extends RegraNegocioException {
    public ParcelaPagaException() {
        super("Parcela já está paga.");
    }
}
