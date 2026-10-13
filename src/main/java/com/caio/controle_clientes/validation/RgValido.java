package com.caio.controle_clientes.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RgValidator.class)
public @interface RgValido {
    String message() default "RG deve conter de 7 a 13 caracteres significativos: dígitos e, opcionalmente, X no final.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
