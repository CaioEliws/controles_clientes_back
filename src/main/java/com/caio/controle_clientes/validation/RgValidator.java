package com.caio.controle_clientes.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class RgValidator implements ConstraintValidator<RgValido, String> {
    @Override public boolean isValid(String valor, ConstraintValidatorContext context) {
        return RgUtil.validarEstrutura(valor);
    }
}
