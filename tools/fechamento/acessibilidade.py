from pathlib import Path
front=Path(__file__).resolve().parents[3]/'frontend/src'
def edit(n,fn):
 p=front/n;p.write_text(fn(p.read_text(encoding='utf-8')),encoding='utf-8')
edit('pages/Parcelas/components/ParcelasHeader.tsx',lambda s:s.replace('flex justify-between items-end','flex flex-col gap-4 sm:flex-row sm:justify-between sm:items-end').replace('flex items-center gap-3','flex w-full flex-wrap items-center gap-3 sm:w-auto').replace('relative space-y-1','relative w-full space-y-1 sm:w-auto').replace('pl-10 w-72','pl-10 w-full sm:w-72').replace('placeholder="Buscar cliente..."','aria-label="Buscar cliente"\n            placeholder="Buscar cliente..."'))
edit('pages/Clients/index.tsx',lambda s:s.replace('flex-1 p-10 space-y-10','min-w-0 flex-1 p-4 sm:p-10 space-y-10').replace('flex justify-between items-center','flex flex-col gap-4 sm:flex-row sm:justify-between sm:items-center').replace('flex gap-4','flex w-full flex-wrap gap-4 sm:w-auto').replace('w-64 bg-white','min-w-0 flex-1 sm:w-64 bg-white').replace('placeholder="Pesquisar..."','aria-label="Pesquisar clientes"\n              placeholder="Pesquisar..."'))
edit('pages/Emprestimo/components/table/EmprestimosTableRow.tsx',lambda s:s.replace('onClick={() => onEdit(emprestimo)}','aria-label="Editar empréstimo"\n            onKeyDown={(event) => event.stopPropagation()}\n            onClick={() => onEdit(emprestimo)}').replace('onClick={() => onDelete(emprestimo)}','aria-label="Excluir empréstimo"\n            onKeyDown={(event) => event.stopPropagation()}\n            onClick={() => onDelete(emprestimo)}'))
def loan(s):
 for label,field in [('Valor emprestado','valorEmprestado'),('Quantidade de parcelas','quantidadeParcelas'),('Juros (%)','jurosCobrado'),('Forma de pagamento','formaPagamento'),('Frequência de pagamento','frequenciaPagamento'),('Tipo de contrato','tipoContrato')]:
  s=s.replace(f'<Label className="text-sm">{label}</Label>',f'<Label htmlFor="novo-{field}" className="text-sm">{label}</Label>')
  s=s.replace(f'{{...form.register("{field}")}}',f'id="novo-{field}" {{...form.register("{field}")}}')
 return s
edit('pages/Emprestimo/components/NovoEmprestimoDialog.tsx',loan)
edit('pages/Emprestimo/components/EditEmprestimoDialog.tsx',lambda s:s.replace('<label className="text-sm font-semibold text-slate-700">\n              Nova Data Inicial','<label htmlFor="reprogramar-data" className="text-sm font-semibold text-slate-700">\n              Nova Data Inicial').replace('<label className="text-sm font-semibold text-slate-700">\n              Novo Valor da Parcela','<label htmlFor="reprogramar-valor" className="text-sm font-semibold text-slate-700">\n              Novo Valor da Parcela').replace('ref={inputRef}\n                type="date"','id="reprogramar-data"\n                ref={inputRef}\n                type="date"').replace('type="text"\n              inputMode="numeric"','id="reprogramar-valor"\n              type="text"\n              inputMode="numeric"').replace('className="absolute right-3 top-1/2','aria-label="Abrir calendário"\n                className="absolute right-3 top-1/2'))
edit('components/ProfileSwitcher.tsx',lambda s:s.replace('<select\n','<select\n        aria-label="Perfil ativo"\n',1))
def client(s):
 s=s.replace('  const mounted = useRef(true);','  const mounted = useRef(true);\n  const focoAnterior = useRef(document.activeElement instanceof HTMLElement ? document.activeElement : null);')
 return s.replace('<DialogContent className=', '<DialogContent onCloseAutoFocus={(event) => { event.preventDefault(); focoAnterior.current?.focus(); }} className=',1)
edit('components/EditarClienteDialog.tsx',client)
