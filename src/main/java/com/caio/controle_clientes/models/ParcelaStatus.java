package com.caio.controle_clientes.models;

public enum ParcelaStatus {
    PENDENTE,
    ATRASADO,
    PAGO,
    PARCIAL;

    public boolean isPago() {
        return this == PAGO;
    }

    public boolean isPendente() {
        return this == PENDENTE || this == PARCIAL;
    }

    public boolean isAtrasada() {
        return this == ATRASADO;
    }
}
