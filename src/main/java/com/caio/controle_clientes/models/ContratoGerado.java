package com.caio.controle_clientes.models;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Immutable
@Table(name = "contratos_gerados", indexes = @Index(name = "idx_contrato_perfil_emprestimo",
        columnList = "perfil_id,emprestimo_id"))
@Getter
public class ContratoGerado {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emprestimo_id", nullable = false, updatable = false)
    private Emprestimo emprestimo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false, updatable = false)
    private Perfil perfil;
    @Column(nullable = false, updatable = false, length = 40)
    private String versaoTemplate;
    @Column(nullable = false, updatable = false)
    private OffsetDateTime dataGeracao;
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(nullable = false, updatable = false, length = 64)
    private String hashDocumento;
    @Column(nullable = false, updatable = false, length = 150)
    private String nomeArquivo;
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, updatable = false, columnDefinition = "bytea")
    @Getter(lombok.AccessLevel.NONE)
    private byte[] docx;
    @Column(updatable = false, columnDefinition = "text")
    private String promissoriaSnapshotJson;
    @Column(updatable = false, length = 64)
    private String hashPdfFinal;
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(updatable = false, columnDefinition = "bytea")
    @Getter(lombok.AccessLevel.NONE)
    private byte[] pdfFinal;
    @Column(updatable = false, length = 64)
    private String hashPdfAssinado;
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(updatable = false, columnDefinition = "bytea")
    @Getter(lombok.AccessLevel.NONE)
    private byte[] pdfFinalAssinado;

    protected ContratoGerado() {}

    public ContratoGerado(Emprestimo emprestimo, Perfil perfil, String versaoTemplate,
                          OffsetDateTime dataGeracao, String snapshotJson, String hashDocumento,
                          String nomeArquivo, byte[] docx) {
        this.emprestimo = emprestimo;
        this.perfil = perfil;
        this.versaoTemplate = versaoTemplate;
        this.dataGeracao = dataGeracao;
        this.snapshotJson = snapshotJson;
        this.hashDocumento = hashDocumento;
        this.nomeArquivo = nomeArquivo;
        this.docx = docx.clone();
    }

    public byte[] getDocx() { return docx.clone(); }
    public byte[] getPdfFinal() { return pdfFinal == null ? null : pdfFinal.clone(); }
    public byte[] getPdfFinalAssinado() { return pdfFinalAssinado == null ? null : pdfFinalAssinado.clone(); }
    public ContratoGerado(Emprestimo emprestimo, Perfil perfil, String versaoTemplate,
                          OffsetDateTime dataGeracao, String snapshotJson, String hashDocumento,
                          String nomeArquivo, byte[] docx, String promissoriaSnapshotJson,
                          String hashPdfFinal, byte[] pdfFinal, String hashPdfAssinado, byte[] pdfFinalAssinado) {
        this(emprestimo, perfil, versaoTemplate, dataGeracao, snapshotJson, hashDocumento, nomeArquivo,
            docx, promissoriaSnapshotJson, hashPdfFinal, pdfFinal);
        this.hashPdfAssinado = hashPdfAssinado;
        this.pdfFinalAssinado = pdfFinalAssinado.clone();
    }
    public ContratoGerado(Emprestimo emprestimo, Perfil perfil, String versaoTemplate,
                          OffsetDateTime dataGeracao, String snapshotJson, String hashDocumento,
                          String nomeArquivo, byte[] docx, String promissoriaSnapshotJson,
                          String hashPdfFinal, byte[] pdfFinal) {
        this(emprestimo, perfil, versaoTemplate, dataGeracao, snapshotJson, hashDocumento, nomeArquivo, docx);
        this.promissoriaSnapshotJson = promissoriaSnapshotJson;
        this.hashPdfFinal = hashPdfFinal;
        this.pdfFinal = pdfFinal.clone();
    }
}
