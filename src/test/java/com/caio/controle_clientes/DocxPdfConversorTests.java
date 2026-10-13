package com.caio.controle_clientes;

import com.caio.controle_clientes.services.contrato.DocxPdfConversor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class DocxPdfConversorTests {
    @TempDir Path scripts;
    @Test void configuracaoPadraoNaoFixaCaminhoDoWindowsNoLinux() throws Exception {
        var properties=new java.util.Properties();
        try(var input=Files.newInputStream(Path.of("src/main/resources/application.properties"))) { properties.load(input); }
        assertThat(properties.getProperty("contrato.pdf.executavel")).doesNotContain("C:");
    }
    @Test void executavelAusenteTemErroControladoELimpaTemporarios() throws Exception {
        var before=temporarios();
        assertThatThrownBy(() -> new DocxPdfConversor(scripts.resolve("inexistente").toString(),1).converter(new byte[]{1}))
                .hasMessageContaining("Não foi possível executar o LibreOffice");
        assertThat(temporarios()).isEqualTo(before);
    }
    @Test @EnabledOnOs(OS.LINUX)
    void timeoutMataProcessoELimpaTemporarios() throws Exception {
        Path executable=scripts.resolve("soffice-timeout");
        Files.writeString(executable,"#!/bin/sh\nsleep 10\n");
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        var before=temporarios();
        assertThatThrownBy(() -> new DocxPdfConversor(executable.toString(),1).converter(new byte[]{1}))
                .hasMessageContaining("tempo limite");
        assertThat(temporarios()).isEqualTo(before);
    }
    static Set<Path> temporarios() throws Exception {
        try(var paths=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(p -> p.getFileName().toString().startsWith("contrato-")).collect(Collectors.toSet());
        }
    }
}
