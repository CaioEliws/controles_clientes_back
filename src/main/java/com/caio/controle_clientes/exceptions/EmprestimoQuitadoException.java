package com.caio.controle_clientes.exceptions;

public class EmprestimoQuitadoException extends RegraNegocioException {
    public EmprestimoQuitadoException() {
        super("Empréstimo já está quitado.");
    }
}
