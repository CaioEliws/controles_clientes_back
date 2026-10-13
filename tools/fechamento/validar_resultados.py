import hashlib
import json
import re
from pathlib import Path

out = Path('.auditoria/fechamento')
summary = {}
for name in ('mvn-test-final', 'mvn-package-final', 'postgresql-final'):
    rows = json.loads((out / (name + '.json')).read_text(encoding='utf-8-sig'))
    summary[name] = {k: sum(int(r[k]) for r in rows) for k in ('tests', 'failures', 'errors', 'skipped')}
for name in ('frontend', 'rede', 'e2e-final', 'backup-ui', 'smoke-final', 'health', 'restore'):
    result = json.loads((out / name / 'resultado.json').read_text(encoding='utf-8-sig'))
    assert result['status'] == 'PASS'
    summary[name] = dict(status=result['status'], checks=len(result.get('checks', [])))
summary['jarSHA256'] = hashlib.sha256(Path('target/controle_clientes-0.0.1-SNAPSHOT.jar').read_bytes()).hexdigest()
assert summary['jarSHA256'] == hashlib.sha256((out / 'app-18082.jar').read_bytes()).hexdigest()
assert len(json.loads((out / 'e2e-matriz-final.json').read_text(encoding='utf-8'))) == 20
for name in ('FECHAMENTO_PRE_PRODUCAO.md', 'BACKUP_E_RESTAURACAO.md', 'AUDITORIA_COMPLETA_SISTEMA.md'):
    path = Path(name)
    source = path.read_text(encoding='utf-8')
    assert '\ufffd' not in source
    for raw in re.findall(r'\]\(<?(C:[^\n)]+)>?\)', source):
        dest = re.sub(r':\d+$', '', raw.rstrip('>'))
        assert Path(dest).exists(), (name, dest)
    summary[name] = dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
summary['limpeza'] = json.loads((out / 'limpeza-final.json').read_text(encoding='utf-8-sig'))
(out / 'validacao-final.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False, indent=2))
