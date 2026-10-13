package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.exceptions.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

@Service
public class DocxPdfConversor {
    private final String executavel;
    private final long timeout;
    public DocxPdfConversor(@Value("${contrato.pdf.executavel:soffice}") String executavel,
            @Value("${contrato.pdf.timeout-segundos:90}") long timeout) {
        if ("soffice".equals(executavel) && System.getProperty("os.name").startsWith("Windows")) {
            Path instalado = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:/Program Files"),
                    "LibreOffice", "program", "soffice.exe");
            this.executavel = Files.isRegularFile(instalado) ? instalado.toString() : executavel;
        } else {
            this.executavel = executavel;
        }
        this.timeout=timeout;
    }
    public byte[] converter(byte[] docx) {
        Path dir=null; Process process=null;
        try {
            dir=Files.createTempDirectory("contrato-");
            Path input=dir.resolve("input.docx"), output=dir.resolve("input.pdf");
            Files.write(input,docx);
            process=new ProcessBuilder(executavel, "-env:UserInstallation=" + dir.resolve("profile").toUri(),
                    "--headless", "--nologo", "--nodefault", "--nofirststartwizard", "--convert-to", "pdf:writer_pdf_Export",
                    "--outdir",dir.toString(),input.toString()).redirectErrorStream(true)
                    .redirectOutput(dir.resolve("converter.log").toFile()).start();
            if (!process.waitFor(timeout,TimeUnit.SECONDS)) {
                parar(process);
                throw new RegraNegocioException("Conversão DOCX para PDF excedeu o tempo limite.");
            }
            if (process.exitValue()!=0 || !Files.isRegularFile(output) || Files.size(output)==0) {
                throw new RegraNegocioException("Falha na conversão DOCX para PDF pelo LibreOffice.");
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException e) {
            if (process!=null) parar(process);
            Thread.currentThread().interrupt();
            throw new RegraNegocioException("Conversão DOCX para PDF interrompida.");
        } catch (IOException e) {
            throw new RegraNegocioException("Não foi possível executar o LibreOffice para converter o DOCX. Verifique contrato.pdf.executavel.");
        } finally {
            if (dir!=null) {
                try (var paths=Files.walk(dir)) {
                    for (Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                } catch (IOException e) { throw new RegraNegocioException("Falha ao limpar temporários da conversão de contrato."); }
            }
        }
    }
    private static void parar(Process p) { p.descendants().forEach(ProcessHandle::destroyForcibly); p.destroyForcibly(); }

    /** Verifica o mesmo executável configurado para conversão, sem produzir documentos. */
    public boolean disponivel() {
        Process process = null;
        try {
            String comando = executavel;
            // O launcher GUI Windows abre diálogo para --version; usar o launcher
            // de console da mesma instalação, sem alterar o executável da conversão.
            if (System.getProperty("os.name").startsWith("Windows")
                    && Path.of(executavel).getFileName().toString().equalsIgnoreCase("soffice.exe")) {
                Path console = Path.of(executavel).resolveSibling("soffice.com");
                if (Files.isRegularFile(console)) comando = console.toString();
            }
            process = new ProcessBuilder(comando, "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) parar(process);
        }
    }
}
