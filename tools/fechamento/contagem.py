from pathlib import Path
front=Path('../frontend/src')
p=front/'hooks/useClients.ts';s=p.read_text(encoding='utf-8').replace('import { apiClient } from "@/services/apiClient";\n','').replace('type { Cliente, Emprestimo }','type { Cliente }');a=s.index('      const emprestimosPorCliente =');b=s.index('      setTotalEmprestimos(total);',a);s=s[:a]+'      const total = data.reduce((acc, cliente) => acc + (cliente.quantidadeEmprestimos ?? 0), 0);\n\n'+s[b:];p.write_text(s,encoding='utf-8')
p=front/'types/index.ts';s=p.read_text(encoding='utf-8').replace('export interface Cliente extends DadosPessoais {','export interface Cliente extends DadosPessoais {\n  quantidadeEmprestimos?: number | null;');p.write_text(s,encoding='utf-8')
