from pathlib import Path
import hashlib, json

root = Path(__file__).resolve().parents[2]
front = root.parent / 'frontend'
out = root / '.auditoria/fechamento'
out.mkdir(parents=True, exist_ok=True)
baseline = out / 'baseline-sha256.json'
if not baseline.exists():
    files = list((root/'src').rglob('*')) + list((front/'src').rglob('*')) + [front/'eslint.config.js', root/'AUDITORIA_COMPLETA_SISTEMA.md']
    baseline.write_text(json.dumps({str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file()}, indent=2), encoding='utf-8')
    (out/'auditoria-anterior.md').write_bytes((root/'AUDITORIA_COMPLETA_SISTEMA.md').read_bytes())

def read(name): return (front/'src'/name).read_text(encoding='utf-8')
def write(name, text): (front/'src'/name).write_text(text, encoding='utf-8')

s = read('components/ui/badge.tsx')
a, b = s.index('const badgeVariants'), s.index('export interface BadgeProps')
write('components/ui/badgeVariants.ts', 'import { cva } from "class-variance-authority";\n\n'+s[a:b].replace('const badgeVariants', 'export const badgeVariants'))
s = s[:a]+s[b:]
s = s.replace('cva, type VariantProps','type VariantProps').replace('export { Badge, badgeVariants };','export { Badge };')
s = s.replace('import { cn }', 'import { badgeVariants } from "./badgeVariants";\nimport { cn }')
write('components/ui/badge.tsx', s)

s = read('contexts/ProfileContext.tsx')
a, b, c = s.index('export interface Perfil'), s.index('const PERFIL_STORAGE_KEY'), s.index('export function useProfile')
state = s[a:b].replace('const ProfileContext =', 'export const ProfileContext =')
write('contexts/profileContextState.ts', 'import { createContext } from "react";\nimport type { DadosPessoais } from "@/types";\n\n'+state)
write('hooks/useProfile.ts', 'import { useContext } from "react";\nimport { ProfileContext } from "@/contexts/profileContextState";\n\n'+s[c:])
provider = s[:a].replace('  createContext,\n','').replace('  useContext,\n','').replace('import type { DadosPessoais } from "@/types";', 'import { ProfileContext, type Perfil, type PerfilUpdatePayload } from "./profileContextState";')+s[b:c]
write('contexts/ProfileProvider.tsx', provider)
write('contexts/ProfileContext.ts', 'export { ProfileProvider } from "./ProfileProvider";\nexport { useProfile } from "@/hooks/useProfile";\nexport type { Perfil, PerfilUpdatePayload } from "./profileContextState";\n')
(front/'src/contexts/ProfileContext.tsx').unlink()

s = read('hooks/useParcelasPage.ts').replace('useCallback, useEffect','useCallback').replace('import { useProfile } from "@/contexts/ProfileContext";\n','').replace('  const { perfilAtivo } = useProfile();\n','')
a, b = s.index('  useEffect(() => {'), s.index('  const parcelasBase')
s = s[:a]+s[b:]
s = s.replace('const [page, setPage]', 'const [requestedPage, setPage]')
a, b = s.index('  useEffect(() => {'), s.index('  const paginated')
s = s[:a]+'  const page = Math.min(Math.max(requestedPage, 1), totalPages);\n\n'+s[b:]
s = s.replace('setPage((prev) => Math.max(1, prev - 1));\n  }, []);','setPage(Math.max(1, page - 1));\n  }, [page]);').replace('setPage((prev) => Math.min(totalPages, prev + 1));\n  }, [totalPages]);','setPage(Math.min(totalPages, page + 1));\n  }, [page, totalPages]);')
write('hooks/useParcelasPage.ts', s)
s = read('pages/Parcelas/index.tsx').replace('export function Parcelas() {', 'export function Parcelas() {\n  const { perfilAtivo } = useProfile();\n  return <ParcelasDoPerfil key={perfilAtivo?.id ?? "sem-perfil"} />;\n}\n\nfunction ParcelasDoPerfil() {')
s = 'import { useProfile } from "@/contexts/ProfileContext";\n'+s
write('pages/Parcelas/index.tsx', s)

s = read('pages/Emprestimo/components/EditEmprestimoDialog.tsx').replace('useEffect, useRef', 'useRef')
s = s.replace('export function EditEmprestimoDialog({', 'export function EditEmprestimoDialog(props: Props) {\n  return <EditEmprestimoForm key={props.open ? props.emprestimo?.id : "fechado"} {...props} />;\n}\n\nfunction EditEmprestimoForm({')
s = s.replace('useState("");\n  const [novoValorParcela, setNovoValorParcela] = useState("");', 'useState(() => toInputDate(emprestimo?.inicioPagamento));\n  const [novoValorParcela, setNovoValorParcela] = useState(() => emprestimo ? formatCurrency(emprestimo.valorParcela) : "");')
a,b = s.index('  useEffect(() => {'),s.index('  async function handleSubmit')
s=s[:a]+s[b:]
write('pages/Emprestimo/components/EditEmprestimoDialog.tsx',s)

s = read('pages/Home/components/FinancialChart.tsx').replace('// eslint-disable-next-line @typescript-eslint/no-explicit-any\nfunction CustomTooltip({ active, payload }: any)', 'function CustomTooltip({ active, payload }: { active?: boolean; payload?: readonly { payload: { label: string; valueLabel: string } }[] })')
s = s.replace('    setContainer(node);', '    setContainer(node);\n    const rect = node?.getBoundingClientRect();\n    setSize({ width: Math.max(0, Math.floor(rect?.width ?? 0)), height: Math.max(0, Math.floor(rect?.height ?? 0)) });')
s = s.replace('    if (!container) {\n      setSize({ width: 0, height: 0 });\n      return;\n    }','    if (!container) return;').replace('    updateSize();\n\n','')
write('pages/Home/components/FinancialChart.tsx', s)

s = read('components/AlterarParcelaDialog.tsx').replace('useEffect, useRef, useState','useCallback, useEffect, useRef, useState').replace('  // eslint-disable-next-line react-hooks/incompatible-library\n','')
a,b = s.index('  async function carregarObservacoes()'),s.index('  const onSubmit')
callback = s[a:b].replace('async function carregarObservacoes() {','const carregarObservacoes = useCallback(async () => {').rstrip()
callback = callback[:-1]+'}, [idEmprestimo, numeroParcela]);\n\n'
s = s[:a]+s[b:]
a = s.index('  useEffect(() => {')
s = s[:a]+callback+s[a:]
s = s.replace('[open, dataAtual, valorAtual, reset, idEmprestimo, numeroParcela]', '[open, dataAtual, valorAtual, reset, carregarObservacoes]')
write('components/AlterarParcelaDialog.tsx',s)

s = read('pages/Emprestimo/index.tsx')
# URL hydration is already owned by useEmprestimosPage; the duplicate effect overwrote sorting and fetched twice.
a,b=s.index('  useEffect(() => {'),s.index('  const updateFiltersInUrl')
s=s[:a]+s[b:]
s=s.replace('import { useEffect } from "react";\n','')
write('pages/Emprestimo/index.tsx',s)
