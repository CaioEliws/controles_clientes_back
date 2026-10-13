import { useEffect, useRef, useState } from "react";
import { Trash2 } from "lucide-react";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { contratoService } from "@/services/contrato.service";

type Props = { clienteId: number; emprestimoId: number; contratoId: number; disabled?: boolean; onExcluido: (id: number) => void };

export function ExcluirContratoButton({ clienteId, emprestimoId, contratoId, disabled, onExcluido }: Props) {
  const [open, setOpen] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const lock = useRef(false);
  const mounted = useRef(true);
  const cancelar = useRef<HTMLButtonElement>(null);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  async function excluir() {
    if (lock.current || disabled) return;
    lock.current = true;
    setExcluindo(true);
    setErro(null);
    try {
      await contratoService.excluir(clienteId, emprestimoId, contratoId);
      if (mounted.current) { setOpen(false); onExcluido(contratoId); }
    } catch { if (mounted.current) setErro("Não foi possível excluir o contrato."); }
    finally { lock.current = false; if (mounted.current) setExcluindo(false); }
  }

  return <>
    <Button type="button" variant="outline" className="text-destructive hover:text-destructive" aria-label={`Excluir contrato #${contratoId}`} disabled={disabled || excluindo} onClick={() => { setErro(null); setOpen(true); }}>
      <Trash2 className="mr-1 h-4 w-4" aria-hidden="true" />{excluindo ? "Excluindo..." : "Excluir"}
    </Button>
    <Dialog open={open} onOpenChange={(value) => { if (!lock.current) setOpen(value); }}>
      <DialogContent role="alertdialog" className="w-[calc(100%-2rem)] sm:max-w-lg" onOpenAutoFocus={(event) => { event.preventDefault(); cancelar.current?.focus(); }}>
        <DialogHeader><DialogTitle>Excluir contrato?</DialogTitle><DialogDescription>O Contrato #{contratoId} e seus arquivos PDF e Word serão removidos permanentemente. O empréstimo e suas parcelas não serão alterados.</DialogDescription></DialogHeader>
        {erro && <p role="alert" className="text-sm text-red-600">{erro}</p>}
        <DialogFooter className="gap-2">
          <Button ref={cancelar} type="button" variant="outline" disabled={excluindo} onClick={() => setOpen(false)}>Cancelar</Button>
          <Button type="button" variant="destructive" disabled={disabled || excluindo} onClick={() => { void excluir(); }}>{excluindo ? "Excluindo..." : "Excluir contrato"}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </>;
}

export function AvisoContratoExcluido({ onClose }: { onClose: () => void }) {
  useEffect(() => { const timer = window.setTimeout(onClose, 4000); return () => window.clearTimeout(timer); }, [onClose]);
  return createPortal(<div role="status" aria-live="polite" className="pointer-events-none fixed right-4 top-4 z-[60] max-w-[calc(100%-2rem)] rounded-lg border border-emerald-200 bg-white p-4 text-sm text-emerald-800 shadow-lg">
    Contrato excluído com sucesso.
  </div>, document.body);
}
