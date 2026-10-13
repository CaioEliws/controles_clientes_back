from pathlib import Path

root = Path(__file__).resolve().parents[3] / "frontend/src"
service = root / "services/contrato.service.ts"
text = service.read_text(encoding="utf-8")
assert "pdfDisponivel" not in text
text = text.replace("  hashDocumento: string;", "  hashDocumento: string;\n  pdfDisponivel: boolean;")
text = text.replace("  baixar: (clienteId:", "  baixarPdf: (clienteId: number, emprestimoId: number, contratoId: number) =>\n    apiClient.getBlob(`${rota(clienteId, emprestimoId)}/contratos/${contratoId}/download-pdf`),\n  baixar: (clienteId:")
service.write_text(text, encoding="utf-8")
component = root / "pages/Emprestimo/components/ContratosGerados.tsx"
text = component.read_text(encoding="utf-8")
assert "async function baixar(id: number)" in text
text = text.replace("async function baixar(id: number)", "async function baixar(id: number, tipo: \"pdf\" | \"word\")")
text = text.replace("await contratoService.baixar(clienteId, emprestimoId, id)", "await (tipo === \"pdf\" ? contratoService.baixarPdf : contratoService.baixar)(clienteId, emprestimoId, id)")
old = '''        <Button type="button" variant="outline" disabled={gerando || baixando !== null} onClick={() => { void baixar(contrato.id); }}>
          {baixando === contrato.id ? "Baixando..." : "Baixar Word"}
        </Button>'''
new = '''        <div className="flex gap-2">
          {contrato.pdfDisponivel && <Button type="button" disabled={gerando || baixando !== null} onClick={() => { void baixar(contrato.id, "pdf"); }}>
            {baixando === contrato.id ? "Baixando..." : "Baixar PDF"}
          </Button>}
          <Button type="button" variant="outline" disabled={gerando || baixando !== null} onClick={() => { void baixar(contrato.id, "word"); }}>
            {baixando === contrato.id ? "Baixando..." : "Baixar Word"}
          </Button>
        </div>'''
assert old in text
text = text.replace(old, new)
component.write_text(text, encoding="utf-8")
print("Updated only contract service and generated contract history component")
