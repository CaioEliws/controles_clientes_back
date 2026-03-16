package com.caio.controle_clientes.services;

import com.caio.controle_clientes.services.importService.ImportacaoPlanilhaService;
import com.caio.controle_clientes.services.importService.dto.ImportacaoResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportacaoPlanilhaService importacaoPlanilhaService;

    public ImportacaoResultado importarPlanilha(Long perfilId, InputStream file) throws Exception {
        return importacaoPlanilhaService.importar(perfilId, file);
    }
}