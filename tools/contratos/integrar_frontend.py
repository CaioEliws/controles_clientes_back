"""Aplica somente a integração de contratos, preservando as alterações existentes."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FRONT = ROOT.parent / 'frontend'
STAGE = ROOT / 'tools/contratos/frontend'

def replace_file(path, old, new):
    p = FRONT / path
    text = p.read_text(encoding='utf-8')
    if new in text:
        return
    assert text.count(old) == 1, str(p)
    p.write_text(text.replace(old, new), encoding='utf-8')

replace_file('src/services/apiClient.ts',
    'errorObj.error ?? errorObj.message ?? "Erro na requisição"',
    'errorObj.message ?? errorObj.error ?? "Erro na requisição"')
replace_file('src/services/apiClient.ts', 'export const apiClient = {',
    (STAGE/'api-blob.txt').read_text(encoding='utf-8') + '\nexport const apiClient = {\n  getBlob: downloadBlob,')
replace_file('src/pages/Emprestimo/components/DadosContratoDialog.tsx',
    'import { DadosContratoView } from "./DadosContratoView";',
    'import { DadosContratoView } from "./DadosContratoView";\nimport { ContratosGerados } from "./ContratosGerados";')
replace_file('src/pages/Emprestimo/components/DadosContratoDialog.tsx',
    '        {!loading && dados && <DadosContratoView dados={dados} />}',
    '        {!loading && dados && <DadosContratoView dados={dados} />}\n'
    '        <ContratosGerados key={`${clienteId}-${emprestimoId}`} clienteId={clienteId} emprestimoId={emprestimoId} podeGerar={!loading && !!dados} />')
for source, destination in [
    ('contrato.service.ts','src/services/contrato.service.ts'),
    ('ContratosGerados.tsx','src/pages/Emprestimo/components/ContratosGerados.tsx'),
]:
    (FRONT/destination).write_text((STAGE/source).read_text(encoding='utf-8'),encoding='utf-8')
print('Integração aplicada em quatro arquivos frontend, preservando os demais arquivos.')
