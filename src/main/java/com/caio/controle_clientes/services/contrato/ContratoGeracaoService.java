package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.dto.contrato.ContratoGeradoMetadadosDTO;
import com.caio.controle_clientes.dto.contrato.ContratoPreviaDTO;
import com.caio.controle_clientes.dto.contrato.ContratoSnapshotDTO;
import com.caio.controle_clientes.dto.contrato.DadosContratoDTO;
import com.caio.controle_clientes.models.*;
import com.caio.controle_clientes.repository.*;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.exceptions.DocumentoNaoEncontradoException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContratoGeracaoService {
    private final ContratoService dadosService;
    private final ContratoSnapshotService snapshotService;
    private final ContratoTemplateService templateService;
    private final ContratoGeradoRepositorio contratos;
    private final EmprestimoRepositorio emprestimos;
    private final PerfilRepositorio perfis;
    private final EntityManager entityManager;
    private final ObjectMapper json;
    private final ContratoPdfService pdfs;
    private final AssinaturasPdfService assinaturas;

    @Transactional
    public ContratoGeradoMetadadosDTO gerar(Long perfilId, Long clienteId, Long emprestimoId) {
        var dataGeracao = OffsetDateTime.now(ZoneOffset.UTC);
        var snapshot = prepararSnapshot(perfilId, clienteId, emprestimoId, dataGeracao);
        byte[] bytes = templateService.gerar(snapshot);
        String snapshotJson = json.writeValueAsString(snapshot);
        var pdf = pdfs.gerar(bytes, snapshot);
        byte[] pdfAssinado = assinaturas.gerar(pdf.pdfFinal());
        String arquivo = "contrato-emprestimo-" + emprestimoId + "-" + UUID.randomUUID() + ".docx";
        ContratoGerado contrato = new ContratoGerado(
                entityManager.getReference(Emprestimo.class, emprestimoId),
                entityManager.getReference(Perfil.class, perfilId), snapshot.versaoTemplate(),
                dataGeracao, snapshotJson, hash(bytes), arquivo, bytes,
                json.writeValueAsString(pdf.snapshot()), hash(pdf.pdfFinal()), pdf.pdfFinal(),
                hash(pdfAssinado), pdfAssinado);
        contratos.saveAndFlush(contrato);
        log.info("Contrato gerado: contratoId={}, emprestimoId={}, templateVersion={}",
                contrato.getId(), emprestimoId, contrato.getVersaoTemplate());
        return metadados(contrato);
    }

    @Transactional(readOnly = true)
    public ContratoPreviaDTO previa(Long perfilId, Long clienteId, Long emprestimoId) {
        var snapshot = prepararSnapshot(perfilId, clienteId, emprestimoId, OffsetDateTime.now(ZoneOffset.UTC));
        return new ContratoPreviaDTO(true, new DadosContratoDTO(snapshot.emprestimoId(),
                snapshot.credorFixo(), snapshot.devedor(), snapshot.fiador(), snapshot.emprestimo(),
                snapshot.parcelas()), snapshot.testemunhaFixa());
    }

    private ContratoSnapshotDTO prepararSnapshot(Long perfilId, Long clienteId, Long emprestimoId,
            OffsetDateTime dataGeracao) {
        var dados = dadosService.buscarDadosContratoModeloFixo(perfilId, clienteId, emprestimoId,
                ContratoSnapshotService.CREDOR);
        var dataContrato = dataGeracao.atZoneSameInstant(ZoneId.of("America/Sao_Paulo")).toLocalDate();
        return snapshotService.montar(dados, dataContrato);
    }

    @Transactional(readOnly = true)
    public List<ContratoGeradoMetadadosDTO> listar(Long perfilId, Long clienteId, Long emprestimoId) {
        validarEmprestimo(perfilId, clienteId, emprestimoId);
        return contratos.listar(perfilId, clienteId, emprestimoId);
    }

    @Transactional(readOnly = true)
    public DocumentoContrato baixar(Long perfilId, Long clienteId, Long emprestimoId, Long contratoId) {
        ContratoGerado contrato = contratos.buscar(contratoId, perfilId, clienteId, emprestimoId)
                .orElseThrow(() -> new DocumentoNaoEncontradoException("Contrato não encontrado para o empréstimo e perfil informados."));
        byte[] bytes = contrato.getDocx();
        if (!hash(bytes).equals(contrato.getHashDocumento())) {
            throw new RegraNegocioException("Falha na verificação de integridade do documento armazenado.");
        }
        return new DocumentoContrato(contrato.getNomeArquivo(), bytes);
    }

    @Transactional
    public void excluir(Long perfilId, Long clienteId, Long emprestimoId, Long contratoId) {
        if (!perfis.existsById(perfilId)) {
            throw new RegraNegocioException("Perfil não encontrado.");
        }
        var contrato = contratos.buscar(contratoId, perfilId, clienteId, emprestimoId)
                .orElseThrow(() -> new DocumentoNaoEncontradoException("Contrato não encontrado para o empréstimo e perfil informados."));
        var cliente = contrato.getEmprestimo().getCliente();
        if (cliente.getPerfil() == null || !perfilId.equals(cliente.getPerfil().getId())) {
            throw new RegraNegocioException("Cliente não encontrado para o perfil informado.");
        }
        contratos.delete(contrato);
        contratos.flush();
        log.info("Contrato excluído: contratoId={}, emprestimoId={}, perfilId={}", contratoId, emprestimoId, perfilId);
    }

    private void validarEmprestimo(Long perfilId, Long clienteId, Long emprestimoId) {
        Emprestimo e = emprestimos.findByIdAndPerfilId(emprestimoId, perfilId)
                .orElseThrow(() -> new RegraNegocioException("Empréstimo não encontrado para o perfil informado."));
        if (e.getCliente() == null || !clienteId.equals(e.getCliente().getId())) {
            throw new RegraNegocioException("Este empréstimo não pertence ao cliente informado.");
        }
    }

    @Transactional(readOnly = true)
    public DocumentoContrato baixarPdf(Long perfilId, Long clienteId, Long emprestimoId, Long contratoId) {
        var c = contratos.buscar(contratoId, perfilId, clienteId, emprestimoId)
                .orElseThrow(() -> new DocumentoNaoEncontradoException("Contrato não encontrado para o empréstimo e perfil informados."));
        byte[] bytes = c.getPdfFinal();
        if (bytes == null) throw new RegraNegocioException("Este contrato histórico não possui PDF armazenado.");
        if (!hash(bytes).equals(c.getHashPdfFinal())) throw new RegraNegocioException("Falha na verificação de integridade do PDF armazenado.");
        return new DocumentoContrato("contrato-emprestimo-" + emprestimoId + "-" + contratoId + ".pdf", bytes);
    }

    private ContratoGeradoMetadadosDTO metadados(ContratoGerado c) {
        return new ContratoGeradoMetadadosDTO(c.getId(), c.getEmprestimo().getId(), c.getVersaoTemplate(),
                c.getDataGeracao(), c.getNomeArquivo(), c.getHashDocumento(), c.getHashPdfFinal() != null,
                c.getHashPdfAssinado() != null);
    }

    @Transactional(readOnly = true)
    public DocumentoContrato baixarPdfAssinado(Long perfilId, Long clienteId, Long emprestimoId, Long contratoId) {
        var c = contratos.buscar(contratoId, perfilId, clienteId, emprestimoId)
            .orElseThrow(() -> new DocumentoNaoEncontradoException("Contrato não encontrado para o empréstimo e perfil informados."));
        byte[] bytes = c.getPdfFinalAssinado();
        if (bytes == null) throw new RegraNegocioException("PDF com assinaturas indisponível para este contrato antigo.");
        if (!hash(bytes).equals(c.getHashPdfAssinado()))
            throw new RegraNegocioException("Falha na verificação de integridade do PDF com assinaturas armazenado.");
        return new DocumentoContrato("contrato-emprestimo-"+emprestimoId+"-"+contratoId+"-assinado.pdf",bytes);
    }

    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 indisponível", e); }
    }

    public record DocumentoContrato(String nomeArquivo, byte[] bytes) {
        public DocumentoContrato { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
