package com.caio.controle_clientes.exceptions;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends RuntimeException {
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        ex.printStackTrace();

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Header obrigatório ausente");
        body.put("message", "O header '" + ex.getHeaderName() + "' não foi enviado.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        ex.printStackTrace();

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro de validação");
        body.put("message", "Um ou mais campos estão inválidos.");
        body.put("fields", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        ex.printStackTrace();

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Requisição inválida");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Argumento inválido.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        ex.printStackTrace();

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro de integridade no banco");
        body.put("message", "Não foi possível salvar os dados.");
        body.put("details", ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        ex.printStackTrace();

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro de execução");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Erro inesperado");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        ex.printStackTrace();

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro interno do servidor");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Erro inesperado");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}