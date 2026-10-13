"""Extração dos traços azuis autorizados, sem redesenhar/inventar assinatura."""
from pathlib import Path
from PIL import Image
import numpy as np
import json

root = Path(__file__).resolve().parents[2]
qa = root / 'target/assinaturas-limpas-qa'
qa.mkdir(parents=True, exist_ok=True)
out = root / 'src/main/resources/assinaturas'
out.mkdir(parents=True, exist_ok=True)
# Única fonte válida: folha limpa enviada pelo usuário, em resolução nativa.
fonte = Path('C:/Users/caioe/Downloads/assinaturas.jpg')
pagina = Image.open(fonte).convert('RGB')
regioes = {'edson': (430, 1305, 1440, 1435), 'ivani': (285, 1875, 1635, 2050)}
manifesto = {}
for nome, regiao in regioes.items():
    imagem = pagina.crop(regiao)
    rgb = np.asarray(imagem).astype(float)
    escuro = 255-rgb.min(axis=2)
    # Evidência cromática relativa à cobertura: bordas claras da tinta mantêm
    # a mesma identidade azul, mesmo quando a diferença absoluta é pequena.
    pureza = np.minimum(rgb[:,:,2]-rgb[:,:,0], 2*(rgb[:,:,2]-rgb[:,:,1])) / np.maximum(escuro,1)
    t = np.clip((pureza-.08)/.12,0,1)
    confianca = t*t*(3-2*t)  # Transição contínua, sem binarização.
    fundo = np.clip((escuro-2)/4,0,1)
    alpha = np.round(escuro*confianca*fundo).astype('uint8')
    # Não eliminar componentes nem aplicar erosão/dilatação: pontos, falhas
    # naturais da caneta e curvas finas pertencem ao manuscrito de referência.
    # Cor original compensada pelo fundo branco para bordas suaves transparentes.
    a = np.maximum(alpha[:, :, None]/255, 1/255)
    cores = np.clip((rgb-255*(1-a))/a,0,255).astype('uint8')
    rgba = Image.fromarray(np.dstack((cores,alpha)), 'RGBA')
    # A borda mínima inclui antialias; ignora apenas ruído quase invisível
    # fora do recorte útil, sem modificar pixels ou componentes no seu interior.
    ys,xs=np.nonzero(alpha>=5)
    bbox = (max(0,int(xs.min())-2),max(0,int(ys.min())-2),
            min(imagem.width,int(xs.max())+3),min(imagem.height,int(ys.max())+3)) if len(xs) else None
    if not bbox: raise ValueError(f'Assinatura {nome} ausente')
    rgba = rgba.crop(bbox)
    rgba.save(out / f'{nome}.png')
    manifesto[nome] = {'fonte':str(fonte), 'regiaoFonte':regiao, 'recorte':bbox,
                       'resolucaoFonte':pagina.size,'resolucaoAsset':rgba.size}
    imagem.save(qa / f'{nome}-fonte.png')
    preview = Image.new('RGB',rgba.size,'white'); preview.paste(rgba,mask=rgba.getchannel('A'))
    preview.save(qa / f'{nome}-preview.png')
    original = imagem.crop(bbox)
    comparacao = Image.new('RGB', (rgba.width, rgba.height*2+20), 'white')
    comparacao.paste(original, (0,0)); comparacao.paste(preview, (0,rgba.height+20))
    comparacao.save(qa / f'{nome}-fonte-vs-asset.png')
    print(nome,rgba.size)
(qa/'assets-recortes.json').write_text(json.dumps(manifesto,indent=2),encoding='utf-8')
