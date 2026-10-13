package com.caio.controle_clientes.exceptions;

import org.springframework.dao.DataIntegrityViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadLimit(org.springframework.web.multipart.MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/import/clientes")) return ResponseEntity.badRequest()
            .body(Map.of("error", "CSV inválido", "message", "CSV excede o limite de 1 MB."));
        return handleGeneric(ex);
    }
    @ExceptionHandler(DocumentoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentoNaoEncontrado(DocumentoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Documento não encontrado", "message", ex.getMessage()));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidParameter(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Requisição inválida", "message", "Parâmetro obrigatório ausente ou inválido."));
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Recurso não encontrado", "message", "O recurso solicitado não foi encontrado."));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Requisição inválida", "message", "O corpo da requisição contém valores inválidos."));
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        LOG.warn("Falha HTTP: {}", ex.getClass().getSimpleName());

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Header obrigatório ausente");
        body.put("message", "O header '" + ex.getHeaderName() + "' não foi enviado.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // A exceção de validação inclui valores rejeitados de documentos pessoais.

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
        LOG.warn("Falha HTTP: {}", ex.getClass().getSimpleName());

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Requisição inválida");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Argumento inválido.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        LOG.warn("Falha HTTP: {}", ex.getClass().getSimpleName());

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro de integridade no banco");
        boolean exclusaoEmprestimo = "DELETE".equals(request.getMethod())
                && request.getRequestURI().matches(".*/clientes/[0-9]+/emprestimos/[0-9]+/?");
        body.put("message", exclusaoEmprestimo
                ? "Não foi possível excluir o empréstimo." : "Não foi possível salvar os dados.");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RegraNegocioException ex) {
        LOG.warn("Falha HTTP: {}", ex.getClass().getSimpleName());

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro de execução");
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Erro inesperado");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        LOG.warn("Falha HTTP: {}", ex.getClass().getSimpleName());

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro interno do servidor");
        body.put("message", "Não foi possível concluir a operação. Tente novamente.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
