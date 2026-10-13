from pathlib import Path
import subprocess, json
from PIL import Image
import numpy as np
import pdfplumber

root = Path(__file__).resolve().parents[2]
out = root/'target/assinaturas-qa'
poppler = Path(r'C:\Users\caioe\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin\pdftoppm.exe')
resultados=[]
for caso in ['nomes-curtos','nomes-longos','paginacao-4']:
    normal=out/f'{caso}-normal.pdf'; assinado=out/f'{caso}-assinaturas.pdf'
    with pdfplumber.open(normal) as doc:
        paginas=[]
        for i,page in enumerate(doc.pages):
            words=page.extract_words()
            labels=[w for w in words if w['text'] in ['CREDOR:','TESTEMUNHA:']]
            if labels: paginas.append((i+1,page.width,page.height,labels))
    for pagina,width,height,labels in paginas:
        renders=[]
        for tipo,pdf in [('normal',normal),('assinaturas',assinado)]:
            prefix=out/f'{caso}-{tipo}-render'
            subprocess.run([str(poppler),'-f',str(pagina),'-l',str(pagina),'-r','150','-png',str(pdf),str(prefix)],check=True)
            renders.append(Image.open(str(prefix)+f'-{pagina}.png').convert('RGB'))
        a,b=renders
        diff=np.any(np.asarray(a)!=np.asarray(b),axis=2)
        permitida=np.zeros(diff.shape,dtype=bool)
        escala=a.width/width
        caixas=[]
        for label in labels:
            # Região em branco abaixo do label, acima da linha/nome impresso.
            # O bbox tipográfico inclui espaço de descender em labels maiúsculos.
            # Liberar esse espaço em branco sem permitir alteração dos glifos.
            y0=int((label['bottom']-3)*escala); y1=int((label['bottom']+27)*escala)
            x0=int(label['x0']*escala); x1=int((label['x0']+285)*escala)
            ly0=int(label['top']*escala); ly1=int(label['bottom']*escala)
            lx1=int(label['x1']*escala)
            preto=np.asarray(a)[ly0:ly1,x0:lx1].min(axis=2)<100
            assert not (diff[ly0:ly1,x0:lx1]&preto).any(),f'Label coberto: {label["text"]}'
            permitida[y0:y1,x0:x1]=True
            caixas.append([x0,y0,x1,y1])
            assert diff[y0:y1,x0:x1].any(),f'Assinatura ausente: {label["text"]}'
        assert not (diff & ~permitida).any(),f'Alteração fora das assinaturas: {caso}'
        lado=Image.new('RGB',(a.width*2,a.height),'white'); lado.paste(a,(0,0));lado.paste(b,(a.width,0))
        lado.save(out/f'{caso}-comparacao.png')
        Image.blend(a,b,.5).save(out/f'{caso}-overlay.png')
        resultados.append({'caso':caso,'pagina':pagina,'pixelsAlterados':int(diff.sum()),'caixasPermitidas':caixas,'somenteCredorETestemunha':True})
(out/'visual.json').write_text(json.dumps(resultados,indent=2),encoding='utf-8')
print(json.dumps(resultados,indent=2))
