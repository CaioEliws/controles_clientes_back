package com.caio.controle_clientes;

import com.caio.controle_clientes.controllers.HealthController;
import com.caio.controle_clientes.services.HealthService;
import com.caio.controle_clientes.services.contrato.DocxPdfConversor;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthTests {
    @Test void recursosDisponiveisRetornam200SemSegredos() throws Exception {
        DataSource ds=mock(DataSource.class); Connection connection=mock(Connection.class);
        when(ds.getConnection()).thenReturn(connection); when(connection.isValid(1)).thenReturn(true);
        var lo=mock(DocxPdfConversor.class); when(lo.disponivel()).thenReturn(true);
        var service=new HealthService(ds,lo);
        try {
            var response=new HealthController(service).verificar();
            assertEquals(200,response.getStatusCode().value());
            assertEquals("UP",response.getBody().get("status"));
            assertEquals(8,response.getBody().size());
            assertTrue(response.getBody().values().stream().allMatch("UP"::equals));
            verify(connection).close();
        } finally { service.fechar(); }
    }
    @Test void libreOfficeAusenteDegradaSemDerrubarApi() throws Exception {
        DataSource ds=mock(DataSource.class); Connection c=mock(Connection.class);
        when(ds.getConnection()).thenReturn(c); when(c.isValid(1)).thenReturn(true);
        var service=new HealthService(ds,mock(DocxPdfConversor.class));
        try { var r=new HealthController(service).verificar(); assertEquals(200,r.getStatusCode().value());
            assertEquals("DEGRADED",r.getBody().get("status")); assertEquals("DOWN",r.getBody().get("libreOffice"));
        } finally { service.fechar(); }
    }
    @Test void bancoIndisponivelRetorna503SemDetalhes() throws Exception {
        DataSource ds=mock(DataSource.class); when(ds.getConnection()).thenThrow(new java.sql.SQLException("senha/host secreto"));
        var service=new HealthService(ds,mock(DocxPdfConversor.class));
        try { var r=new HealthController(service).verificar(); assertEquals(503,r.getStatusCode().value());
            assertEquals("DOWN",r.getBody().get("database")); assertFalse(r.getBody().toString().contains("secreto"));
        } finally { service.fechar(); }
    }
    @Test void driverTravadoNaoTravaRequestNemAcumulaSondas() throws Exception {
        DataSource ds=mock(DataSource.class); var release=new CountDownLatch(1);
        when(ds.getConnection()).thenAnswer(call->{while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return mock(Connection.class);});
        var service=new HealthService(ds,mock(DocxPdfConversor.class));
        try { long inicio=System.nanoTime(); assertEquals("DOWN",service.verificar().get("database"));
            assertTrue((System.nanoTime()-inicio)/1_000_000<2500);
            inicio=System.nanoTime(); assertEquals("DOWN",service.verificar().get("database"));
            assertTrue((System.nanoTime()-inicio)/1_000_000<500);
        } finally { release.countDown();service.fechar(); }
    }
    @Test void executableAusenteEhDetectado() {
        assertFalse(new DocxPdfConversor("executavel-inexistente-fechamento",2).disponivel());
    }
    @Test void libreOfficeRealWindowsDisponivel() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        assertTrue(new DocxPdfConversor("soffice",2).disponivel());
    }
}
