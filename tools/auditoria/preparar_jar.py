from pathlib import Path
from zipfile import ZipFile
import sys

root = Path(__file__).resolve().parents[2]
exclusoes = {
    'sem-docx': 'BOOT-INF/classes/templates/contrato-confissao-divida-v1.docx',
    'sem-promissoria': 'BOOT-INF/classes/templates/promissoria-v1.pdf',
    'sem-assinatura': 'BOOT-INF/classes/assinaturas/edson.png',
}
for nome, entrada in exclusoes.items():
    with ZipFile(root / '.auditoria/app.jar') as origem, ZipFile(root / f'.auditoria/{nome}.jar', 'w') as destino:
        assert entrada in origem.namelist()
        for info in origem.infolist():
            if info.filename != entrada:
                destino.writestr(info, origem.read(info.filename))
    print(nome)
