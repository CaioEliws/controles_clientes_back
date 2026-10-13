from pathlib import Path
import re,json,hashlib
root=Path(__file__).resolve().parents[2]
front=root.parent/'frontend'
out=root/'.auditoria/evidencias';out.mkdir(parents=True,exist_ok=True)
java=root/'src/main/java/com/caio/controle_clientes'
grupos={}
for area in ['controllers','services','repository','models','dto','exceptions','config','validation']:
    grupos[area]=[str(p.relative_to(root)).replace('\\','/') for p in sorted((java/area).rglob('*.java'))]
arquivos=list((root/'src').rglob('*'))+list((front/'src').rglob('*'))
hashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in arquivos if p.is_file()}
(out/'fontes-baseline-sha256.json').write_text(json.dumps(hashes,indent=2),encoding='utf-8')
rows=[]
for p in sorted((java/'controllers').glob('*.java')):
    s=p.read_text(encoding='utf-8');base=re.search(r'@RequestMapping\("([^"]*)"\)',s)
    base=base.group(1) if base else ''
    servicos=re.findall(r'private final (\w+(?:Service|Repositorio)) (\w+)',s)
    for m in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\((.*?)\))?',s,re.S):
        arg=m.group(2) or '';path=re.search(r'"([^"]*)"',arg)
        path=path.group(1) if path and ('produces' not in arg[:path.start()] and 'consumes' not in arg[:path.start()]) else ''
        trecho=s[m.end():]; nextmap=re.search(r'@(Get|Post|Put|Patch|Delete)Mapping',trecho)
        trecho=trecho[:nextmap.start()] if nextmap else trecho
        chamadas=[f'{tipo}.{method}' for tipo,var in servicos for method in re.findall(r'\b'+var+r'\.(\w+)\(',trecho)]
        rows.append({'controller':p.stem,'metodo':m.group(1).upper(),'rota':base+path,'service':chamadas})
inventario={'backend':grupos,'endpoints':rows,'frontend':{a:[str(p.relative_to(front)).replace('\\','/') for p in sorted((front/'src'/a).rglob('*')) if p.is_file()] for a in ['pages','services','hooks','contexts','components']},'testes':[p.stem for p in sorted((root/'src/test/java').rglob('*Tests.java'))]}
(out/'inventario.json').write_text(json.dumps(inventario,ensure_ascii=False,indent=2),encoding='utf-8')
md=['# Inventário inicial — antes das correções da auditoria','',f'Endpoints declarados: {len(rows)}.','', '| Funcionalidade / controller | Endpoint | Service |','|---|---|---|']
md += [f"| {r['controller']} | {r['metodo']} `{r['rota']}` | {', '.join(r['service'])} |" for r in rows]
for a,paths in grupos.items():md+=['',f'## Backend: {a} ({len(paths)})','']+['- '+p for p in paths]
for a,paths in inventario['frontend'].items():md+=['',f'## Frontend: {a} ({len(paths)})','']+['- '+p for p in paths]
md+=['','## Testes existentes','']+['- '+t for t in inventario['testes']]
(out/'inventario-inicial.md').write_text('\n'.join(md),encoding='utf-8')
print(json.dumps({'backend':{k:len(v) for k,v in grupos.items()},'endpoints':len(rows),'frontend':{k:len(v) for k,v in inventario['frontend'].items()},'classesTestes':len(inventario['testes'])},indent=2))
