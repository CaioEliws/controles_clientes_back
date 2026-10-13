package com.caio.controle_clientes.services;

import com.caio.controle_clientes.services.contrato.DocxPdfConversor;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class HealthService {
    private final DataSource datasource;
    private final DocxPdfConversor conversor;
    // Sem fila: sondas simultâneas ou um driver travado não acumulam tarefas/threads.
    private final ExecutorService banco = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "health-database");
                thread.setDaemon(true);
                return thread;
            });

    public HealthService(DataSource datasource, DocxPdfConversor conversor) {
        this.datasource = datasource;
        this.conversor = conversor;
    }

    public Map<String, Object> verificar() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("api", "UP");
        states.put("database", databaseUp() ? "UP" : "DOWN");
        states.put("libreOffice", conversor.disponivel() ? "UP" : "DOWN");
        states.put("contratoTemplate", recurso("templates/contrato-confissao-divida-v1.docx"));
        states.put("promissoriaTemplate", recurso("templates/promissoria-v1.pdf"));
        states.put("assinaturaEdson", recurso("assinaturas/edson.png"));
        states.put("assinaturaIvani", recurso("assinaturas/ivani.png"));
        String status = "DOWN".equals(states.get("database")) ? "DOWN"
                : states.containsValue("DOWN") ? "DEGRADED" : "UP";
        states.put("status", status);
        return states;
    }

    private boolean databaseUp() {
        Future<Boolean> task = null;
        try {
            task = banco.submit(() -> {
                try (var connection = datasource.getConnection()) { return connection.isValid(1); }
            });
            return task.get(1200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RejectedExecutionException e) {
            return false;
        } finally {
            if (task != null && !task.isDone()) task.cancel(true);
        }
    }

    private String recurso(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return input.read() >= 0 ? "UP" : "DOWN";
        } catch (java.io.IOException e) { return "DOWN"; }
    }

    @PreDestroy public void fechar() { banco.shutdownNow(); }
}
