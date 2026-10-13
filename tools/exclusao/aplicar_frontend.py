"""Apply the scoped deletion UI using the existing frontend services and lists."""
from pathlib import Path

BACK = Path(__file__).resolve().parents[2]
FRONT = BACK.parent / "frontend/src"

def edit(path, replacements):
    target = FRONT / path
    content = target.read_text(encoding="utf-8")
    for old, new in replacements:
        assert content.count(old) == 1, (path, old)
        content = content.replace(old, new)
    target.write_text(content, encoding="utf-8")

component = FRONT / "components/contratos/ExcluirContratoButton.tsx"
component.parent.mkdir(parents=True, exist_ok=True)
assert not component.exists(), "New component already exists; inspect before applying again"
component.write_text((Path(__file__).parent / component.name).read_text(encoding="utf-8"), encoding="utf-8")

edit("services/contrato.service.ts", [(
    "  baixarPdf:", "  excluir: (clienteId: number, emprestimoId: number, contratoId: number) =>\n    apiClient.delete<void>(`${rota(clienteId, emprestimoId)}/contratos/${contratoId}`),\n  baixarPdf:")])

edit("pages/Documentos/components/DocumentosHistorico.tsx", [
    ('import { Button }', 'import { ExcluirContratoButton } from "@/components/contratos/ExcluirContratoButton";\nimport { Button }'),
    ('{ documentos, loading, error, disabled, baixando, onRetry, onDownload }', '{ documentos, loading, error, disabled, baixando, onRetry, onDownload, clienteId, emprestimoId, onExcluido }'),
    ('  documentos: ContratoGeradoMetadados[];', '  clienteId: number; emprestimoId: number; onExcluido: (id: number) => void;\n  documentos: ContratoGeradoMetadados[];'),
    ('      </div>\n    </li>)}</ul>', '        <ExcluirContratoButton clienteId={clienteId} emprestimoId={emprestimoId} contratoId={documento.id} disabled={disabled || loading} onExcluido={onExcluido} />\n      </div>\n    </li>)}</ul>')])

edit("pages/Documentos/components/DocumentosEmprestimo.tsx", [
    ('import { Link }', 'import { AvisoContratoExcluido } from "@/components/contratos/ExcluirContratoButton";\nimport { Link }'),
    ('  const [sucesso, setSucesso]', '  const [excluido, setExcluido] = useState(false);\n  const [sucesso, setSucesso]'),
    ('    <DocumentosHistorico documentos=', '    {excluido && <AvisoContratoExcluido onClose={() => setExcluido(false)} />}\n    <DocumentosHistorico clienteId={clienteId} emprestimoId={emprestimoId} onExcluido={(id) => { historico.setData((lista) => (lista ?? []).filter((item) => item.id !== id)); setExcluido(true); }} documentos=')])

edit("pages/Emprestimo/components/ContratosGerados.tsx", [
    ('import { Button }', 'import { ExcluirContratoButton, AvisoContratoExcluido } from "@/components/contratos/ExcluirContratoButton";\nimport { Button }'),
    ('  const [aviso, setAviso]', '  const [excluido, setExcluido] = useState(false);\n  const [aviso, setAviso]'),
    ('    {aviso &&', '    {excluido && <AvisoContratoExcluido onClose={() => setExcluido(false)} />}\n    {aviso &&'),
    ('Nenhum contrato gerado.', 'Nenhum documento gerado para este empréstimo.'),
    ('<div className="flex gap-2">', '<div className="flex flex-wrap gap-2">'),
    ('        </div>\n      </li>)}', '          <ExcluirContratoButton clienteId={clienteId} emprestimoId={emprestimoId} contratoId={contrato.id} disabled={gerando || baixando !== null || loading} onExcluido={(id) => { setHistorico((lista) => lista.filter((item) => item.id !== id)); setExcluido(true); }} />\n        </div>\n      </li>)}')])
print("Updated deletion service, shared confirmation/toast and both history lists.")
