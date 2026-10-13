import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { contratoService, type ContratoGeradoMetadados } from "@/services/contrato.service";

type Props = { clienteId: number; emprestimoId: number; podeGerar: boolean };
const mensagem = (error: unknown) => error instanceof Error ? error.message : "Não foi possível acessar os contratos.";

export function ContratosGerados({ clienteId, emprestimoId, podeGerar }: Props) {
  const [historico, setHistorico] = useState<ContratoGeradoMetadados[]>([]);
  const [loading, setLoading] = useState(true);
  const [gerando, setGerando] = useState(false);
  const [baixando, setBaixando] = useState<number | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const mounted = useRef(false);
  const lock = useRef(false);

  useEffect(() => {
    let alive = true;
    mounted.current = true;
    contratoService.listar(clienteId, emprestimoId)
      .then((response) => { if (alive) setHistorico(response); })
      .catch((error: unknown) => { if (alive) setErro(mensagem(error)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; mounted.current = false; };
  }, [clienteId, emprestimoId]);

  async function gerar() {
    if (lock.current || !podeGerar || !window.confirm("Gerar um novo contrato com os dados exibidos atualmente?")) return;
    lock.current = true;
    setGerando(true);
    setErro(null);
    setAviso(null);
    try {
      const contrato = await contratoService.gerar(clienteId, emprestimoId);
      if (mounted.current) {
        setHistorico((atual) => [contrato, ...atual]);
        setAviso(`Contrato #${contrato.id} gerado. O documento foi salvo no histórico.`);
      }
    } catch (error) { if (mounted.current) setErro(mensagem(error)); }
    finally { lock.current = false; if (mounted.current) setGerando(false); }
  }

  async function baixar(id: number) {
    if (lock.current) return;
    lock.current = true;
    setBaixando(id);
    setErro(null);
    try {
      const { blob, nomeArquivo } = await contratoService.baixar(clienteId, emprestimoId, id);
      if (!mounted.current) return;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = nomeArquivo;
      document.body.append(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { if (mounted.current) setErro(mensagem(error)); }
    finally { lock.current = false; if (mounted.current) setBaixando(null); }
  }

  return <section className="space-y-3 rounded-xl border p-4" aria-label="Contratos gerados">
    <h3 className="font-semibold">Contratos gerados</h3>
    <p className="text-sm text-slate-600">Neste modelo Word, o credor é Edson Antonio Vieira e a testemunha é Ivani Elias Machado Vieira. Os dados do perfil exibidos acima não substituem essas pessoas no documento.</p>
    {podeGerar && <Button type="button" disabled={gerando || baixando !== null || loading} onClick={() => { void gerar(); }}>
      {gerando ? "Gerando contrato..." : "Gerar contrato"}
    </Button>}
    {erro && <p role="alert" className="text-sm text-red-600">{erro}</p>}
    {aviso && <p role="status" className="text-sm text-emerald-700">{aviso}</p>}
    {loading && <p role="status" className="text-sm">Carregando histórico...</p>}
    {!loading && historico.length === 0 && <p className="text-sm text-slate-500">Nenhum contrato gerado.</p>}
    <ul className="space-y-2">
      {historico.map((contrato) => <li key={contrato.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 p-3">
        <div><p className="font-medium">Contrato #{contrato.id}</p><p className="text-sm text-slate-500">{new Date(contrato.dataGeracao).toLocaleString("pt-BR")}</p></div>
        <Button type="button" variant="outline" disabled={gerando || baixando !== null} onClick={() => { void baixar(contrato.id); }}>
          {baixando === contrato.id ? "Baixando..." : "Baixar Word"}
        </Button>
      </li>)}
    </ul>
  </section>;
}
