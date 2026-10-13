from pathlib import Path
root=Path(__file__).resolve().parents[2]; front=root.parent/'frontend/src'
def read(n):return (front/n).read_text(encoding='utf-8')
def write(n,s):(front/n).write_text(s,encoding='utf-8')
s=read('services/apiClient.ts')
pos=s.index('async function request<T>')
helper='''function mensagemOperacao(endpoint: string, method = "GET") {
  if (endpoint.includes("/download") || endpoint === "/export") return "Não foi possível baixar o arquivo.";
  if (method === "DELETE") return endpoint.includes("/contratos/") ? "Não foi possível excluir o contrato." : endpoint.includes("/emprestimos/") ? "Não foi possível excluir o empréstimo." : "Não foi possível excluir o cliente.";
  if (endpoint.includes("/contratos") && method === "POST") return "Não foi possível gerar o documento.";
  if (endpoint.startsWith("/parcelas/pagar")) return "Não foi possível registrar o pagamento.";
  if (endpoint.startsWith("/parcelas/desfazer")) return "Não foi possível estornar o pagamento.";
  if (endpoint.includes("/emprestimos") && method === "POST") return "Não foi possível criar o empréstimo.";
  if (endpoint.startsWith("/clientes") && ["POST", "PUT", "PATCH"].includes(method)) return "Não foi possível salvar o cliente.";
  if (method === "GET") return endpoint.startsWith("/parcelas") ? "Não foi possível carregar as parcelas." : endpoint.includes("/emprestimos") ? "Não foi possível carregar os empréstimos." : endpoint.startsWith("/clientes") ? "Não foi possível carregar os clientes." : "Não foi possível carregar os dados.";
  return "Não foi possível concluir a operação.";
}

function mensagemSegura(data: unknown, fallback: string) {
  const obj = data && typeof data === "object" ? data as { message?: unknown; error?: unknown } : null;
  const message = obj?.message ?? obj?.error;
  return typeof message === "string" && message.trim() && !/SQL|Hibernate|constraint|stacktrace|java\\.|org\\.|<html|Exception:|jdbc:/i.test(message) ? message : fallback;
}

async function fetchComLimite(endpoint: string, options: RequestInit) {
  const fallback = mensagemOperacao(endpoint, options.method);
  const controller = new AbortController();
  const forwardAbort = () => controller.abort();
  options.signal?.addEventListener("abort", forwardAbort, { once: true });
  if (options.signal?.aborted) controller.abort();
  const timer = window.setTimeout(() => controller.abort(), endpoint.endsWith("/contratos") && options.method === "POST" ? 150000 : 30000);
  try { return await fetch(`${API_URL}${endpoint}`, { ...options, signal: controller.signal, headers: buildHeaders(options) }); }
  catch { throw new Error(fallback); }
  finally { window.clearTimeout(timer); options.signal?.removeEventListener("abort", forwardAbort); }
}

'''
s=s[:pos]+helper+s[pos:]
s=s.replace('const response = await fetch(`${API_URL}${endpoint}`, {\n    ...options,\n    headers: buildHeaders(options),\n  });','const response = await fetchComLimite(endpoint, options);')
a,b=s.index('  if (!response.ok) {'),s.index('  return data as T;')
s=s[:a]+'''  if (!response.ok) throw new Error(mensagemSegura(data, mensagemOperacao(endpoint, options.method)));

'''+s[b:]
s=s.replace('await fetch(`${API_URL}${endpoint}`, { method: "GET", headers: buildHeaders() })','await fetchComLimite(endpoint, { method: "GET" })')
s=s.replace('    const error = data as { message?: string; error?: string } | null;\n    throw new Error(error?.message ?? error?.error ?? "Não foi possível baixar o contrato.");','    throw new Error(mensagemSegura(data, "Não foi possível baixar o arquivo."));')
write('services/apiClient.ts',s)

for name,data,label in [('hooks/useClients.ts','clientes','clientes'),('hooks/useParcelas.ts','parcelas','parcelas')]:
 s=read(name);s=s.replace('  const clearState =', '  const [erro, setErro] = useState<string | null>(null);\n\n  const clearState =',1)
 s=s.replace('      setLoading(true);','      setErro(null);\n      setLoading(true);',1)
 s=s.replace('      clearState();\n    } finally',f'      clearState();\n      setErro(error instanceof Error ? error.message : "Não foi possível carregar {label}.");\n    }} finally',1)
 s=s.replace('return { clientes,','return { erro, clientes,') if data=='clientes' else s.replace('    parcelas,\n    loading,','    erro,\n    parcelas,\n    loading,')
 write(name,s)
s=read('pages/Clients/index.tsx').replace('    clientes,\n    loading,','    erro,\n    clientes,\n    loading,')
s=s.replace('        <div className="grid grid-cols-1', '        {erro && <p role="alert" className="text-sm text-red-600">{erro}</p>}\n        <div className="grid grid-cols-1',1)
write('pages/Clients/index.tsx',s)
s=read('hooks/useParcelasPage.ts').replace('const { parcelas, loading, fetchParcelas }','const { parcelas, loading, fetchParcelas, erro }').replace('  return {\n    loading,','  return {\n    erro,\n    loading,')
write('hooks/useParcelasPage.ts',s)
s=read('pages/Parcelas/index.tsx').replace('        {vm.loading ? (','        {vm.erro && <p role="alert" className="text-sm text-red-600">{vm.erro}</p>}\n        {vm.loading ? (',1)
write('pages/Parcelas/index.tsx',s)
s=read('hooks/useEmprestimoPage.ts').replace('  const [loading, setLoading]', '  const [erro, setErro] = useState<string | null>(null);\n  const [loading, setLoading]',1)
s=s.replace('      .catch(() => {','      .catch((error: unknown) => {',1).replace('        setClientes([]);\n      });','        setClientes([]);\n        setErro(error instanceof Error ? error.message : "Não foi possível carregar os clientes.");\n      });',1)
s=s.replace('      setLoading(true);','      setErro(null);\n      setLoading(true);',1).replace('      } catch {\n        setEmprestimos([]);','      } catch (error) {\n        setErro(error instanceof Error ? error.message : "Não foi possível carregar os empréstimos.");\n        setEmprestimos([]);',1)
s=s.replace('  return {\n    clientes,','  return {\n    erro,\n    clientes,')
write('hooks/useEmprestimoPage.ts',s)
s=read('pages/Emprestimo/index.tsx').replace('      <section className="space-y-2">','      {vm.erro && <p role="alert" className="text-sm text-red-600">{vm.erro}</p>}\n      <section className="space-y-2">',1)
write('pages/Emprestimo/index.tsx',s)
