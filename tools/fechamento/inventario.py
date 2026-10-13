import hashlib
import json
import os
from pathlib import Path

out = Path('.auditoria/fechamento')
baseline = json.loads((out / 'baseline-sha256.json').read_text(encoding='utf-8-sig'))
baseline = {os.path.normpath(p): h for p, h in baseline.items()}
changed, deleted, added, protected = [], [], [], []
for name, old_hash in baseline.items():
    path = Path(name)
    if not path.exists():
        deleted.append(name)
    elif hashlib.sha256(path.read_bytes()).hexdigest() != old_hash:
        changed.append(name)
    if '/templates/' in name.replace('\\', '/') or '/assinaturas/' in name.replace('\\', '/'):
        protected.append(name)
for root in (Path('src'), Path('../frontend/src')):
    for path in root.rglob('*'):
        if path.is_file():
            name = os.path.normpath(str(path.absolute()))
            if name not in baseline:
                added.append(name)
result = dict(alterados=changed, removidos=deleted, novos=added,
              arquivosProtegidos=len(protected),
              protegidosInalterados=not any(p in changed or p in deleted for p in protected))
(out / 'alteracoes-final.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print({k: len(v) if isinstance(v, list) else v for k, v in result.items()})
