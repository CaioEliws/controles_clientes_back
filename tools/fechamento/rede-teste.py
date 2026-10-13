from pathlib import Path
p=Path('tools/fechamento/rede.mjs');s=p.read_text(encoding='utf-8');s=s.replace(";failure=(p,m)=>", ";await page.evaluate(()=>window.__timeoutTest=true);failure=(p,m)=>");p.write_text(s,encoding='utf-8')
