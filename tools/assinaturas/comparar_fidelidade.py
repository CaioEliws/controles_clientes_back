"""QA da única referência vigente: assinaturas.jpg, sem reutilizar assets antigos."""
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np
import json, subprocess
import pdfplumber

root = Path(__file__).resolve().parents[2]
out = root/'target/assinaturas-limpas-qa'
qa = root/'target/assinaturas-qa'
manifesto = json.loads((out/'assets-recortes.json').read_text())
poppler = Path(r'C:\Users\caioe\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin\pdftoppm.exe')
pdf = qa/'nomes-curtos-assinaturas.pdf'
with pdfplumber.open(pdf) as doc:
    for pagina, page in enumerate(doc.pages, 1):
        labels = {w['text']: w for w in page.extract_words() if w['text'] in ['CREDOR:', 'TESTEMUNHA:']}
        if not labels: continue
        for tipo in ['normal','assinaturas']:
            subprocess.run([str(poppler),'-f',str(pagina),'-l',str(pagina),'-r','300','-png',
                            str(qa/f'nomes-curtos-{tipo}.pdf'), str(out/tipo)],check=True)
        normal = Image.open(out/f'normal-{pagina}.png').convert('RGB')
        gerado = Image.open(out/f'assinaturas-{pagina}.png').convert('RGB')
        escala = gerado.width/page.width
        for nome, label in [('edson','CREDOR:'),('ivani','TESTEMUNHA:')]:
            if label not in labels: continue
            pos = labels[label]
            regiao = tuple(round(v*escala) for v in (pos['x0'],pos['bottom']-3,pos['x0']+285,pos['bottom']+27))
            a, b = normal.crop(regiao), gerado.crop(regiao)
            diff = np.any(np.asarray(a)!=np.asarray(b),axis=2)
            ys, xs = np.nonzero(diff)
            bbox = (max(0,int(xs.min())-3), max(0,int(ys.min())-3),
                    min(b.width,int(xs.max())+4), min(b.height,int(ys.max())+4))
            impresso = b.crop(bbox)
            fonte = Image.open(out/f'{nome}-fonte.png').crop(manifesto[nome]['recorte']).convert('RGB')
            asset = Image.open(root/f'src/main/resources/assinaturas/{nome}.png').convert('RGBA')
            branco = Image.new('RGB',asset.size,'white'); branco.paste(asset,mask=asset.getchannel('A'))
            painel = Image.new('RGB',(max(fonte.width,impresso.width),fonte.height*2+impresso.height+90),'white')
            draw = ImageDraw.Draw(painel); y=0
            for titulo, img in [('FONTE LIMPA - pixels nativos',fonte),('PNG TRANSPARENTE SOBRE BRANCO - pixels nativos',branco),('PDF GERADO - render 300 dpi, escala do contrato',impresso)]:
                draw.text((5,y+5),titulo,fill='black'); painel.paste(img,(0,y+30)); y+=img.height+30
            painel.save(out/f'{nome}-fonte-asset-pdf.png')
            impresso.save(out/f'{nome}-pdf-300.png')

resultados=[]
for nome in ['edson','ivani']:
    info=manifesto[nome]
    fonte=np.asarray(Image.open(out/f'{nome}-fonte.png').crop(info['recorte']).convert('RGB')).astype(float)
    preview=np.asarray(Image.open(out/f'{nome}-preview.png')).astype(float)
    escuro=255-fonte.min(axis=2)
    pureza=np.minimum(fonte[:,:,2]-fonte[:,:,0],2*(fonte[:,:,2]-fonte[:,:,1]))/np.maximum(escuro,1)
    tinta=(pureza>=.2)&(escuro>5)
    erro=float(np.abs(preview-fonte).mean(axis=2)[tinta].mean())
    assert erro<1, f'Alteração da cor da tinta: {nome} {erro}'
    resultados.append({'assinatura':nome,'fonte':info['fonte'],'resolucaoNativa':info['resolucaoAsset'],
                        'erroMedioRGBTintaAzulSobreBranco':round(erro,3),'semReamostragemAsset':True})
(out/'fidelidade-assets.json').write_text(json.dumps(resultados,indent=2),encoding='utf-8')
print(json.dumps(resultados,indent=2))
