"""Conversão única do Word oficial; altera apenas w:t dos slots auditados.

P2/P23: qualificação; P9/P12/P13/P14: valores/datas; P51: data;
P54/P60: nomes nas assinaturas. Todos os demais elementos são preservados.
"""
from pathlib import Path
from zipfile import ZipFile
import re
import html
import hashlib
import copy

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/resources/templates/source/contrato-original-v1.docx'
OUTPUT = SOURCE.parent.parent / 'contrato-confissao-divida-v1.docx'
P = re.compile(r'<w:p(?:\s[^>]*)?>.*?</w:p>', re.S)
T = re.compile(r'(<w:t(?:\s[^>]*)?>)(.*?)(</w:t>)', re.S)

def patch(paragraph, old, new):
    nodes = list(T.finditer(paragraph))
    texts = [html.unescape(n[2]) for n in nodes]
    text = ''.join(texts)
    assert text.count(old) == 1, (old, text)
    start = text.index(old)
    end = start + len(old)
    offset = 0
    changes = []
    for n, value in zip(nodes, texts):
        next_offset = offset + len(value)
        if offset < end and next_offset > start:
            a, b = max(0, start-offset), min(len(value), end-offset)
            insert = new if offset <= start < next_offset else ''
            changes.append((n.start(2), n.end(2), html.escape(value[:a]+insert+value[b:], quote=False)))
        offset = next_offset
    for a, b, value in reversed(changes):
        paragraph = paragraph[:a] + value + paragraph[b:]
    return paragraph

def slots(role, old):
    keys = ['NOME','NACIONALIDADE','ESTADO_CIVIL','PROFISSAO','RG','RG_ORGAO','CPF','ENDERECO']
    return [(value, '{{'+role+'_'+key+'}}') for key, value in zip(keys, old)]

with ZipFile(SOURCE) as z:
    original = z.read('word/document.xml').decode('utf-8')
    paragraphs = list(P.finditer(original))
    assert len(paragraphs) == 66
    replacements = {
        2: slots('DEVEDOR', ['Marcia aparecida Fonseca Sampaio','brasileira','casada','do lar',
            '32.509.251-5','SSP/SP','319.746.898-83',
            'Rua Músico Ageo Caldeira, Nº 101, Jardim Lauro Bueno de Camargo, Cep 13348-651, Indaiatuba — SP']),
        23: slots('FIADOR', ['Sandra Regina Ferreira','brasileira','amasiada','agente administrativo de suporte',
            '25.927.789.7','SSP/SP','184.248.818.00',
            'Rua Antonio José Pozzan, Nº 88, Jardim Portal do Sol, Cep 13349-726, Indaiatuba — SP']),
        9: [('2.160,00','{{VALOR_TOTAL}}'),('dois mil cento e sessenta reais','{{VALOR_TOTAL_EXTENSO}}')],
        12: [('8 (oito)','{{QUANTIDADE_PARCELAS}} ({{QUANTIDADE_PARCELAS_EXTENSO}})'),
             ('parcelas,','{{PARCELA_SUBSTANTIVO}},'),
             ('362,00','{{VALOR_PARCELA}}'),('trezentos e sessenta e dois reais','{{VALOR_PARCELA_EXTENSO}}'),
             ('dia 21','dia {{DIA_VENCIMENTO}}')],
        13: [('21/07/2024','{{PRIMEIRA_PARCELA}}'),('21/02/2025','{{ULTIMA_PARCELA}}')],
        14: [('2.160,00','{{VALOR_PROMISSORIA}}'),('dois mil cento e sessenta reais','{{VALOR_PROMISSORIA_EXTENSO}}')],
        51: [('21 de junho de 2024','{{DATA_CONTRATO_EXTENSO}}')],
        54: [('Marcia aparecida Fonseca Sampaio','{{DEVEDOR_NOME}}')],
        60: [('Sandra Regina Ferreira','{{FIADOR_NOME}}')],
    }
    edited = original
    for index in sorted(replacements, reverse=True):
        match = paragraphs[index]
        value = match[0]
        for old, new in replacements[index]:
            value = patch(value, old, new)
        edited = edited[:match.start()] + value + edited[match.end():]
    # Toda estrutura XML, incluindo runs/propriedades, deve ser idêntica sem os textos.
    assert T.sub(r'\1\3', original) == T.sub(r'\1\3', edited)
    with ZipFile(OUTPUT, 'w') as out:
        for info in z.infolist():
            out.writestr(copy.copy(info), edited.encode('utf-8') if info.filename == 'word/document.xml' else z.read(info.filename))
    with ZipFile(OUTPUT) as out:
        for info in z.infolist():
            if info.filename != 'word/document.xml':
                assert z.read(info.filename) == out.read(info.filename)
print('Original SHA-256:', hashlib.sha256(SOURCE.read_bytes()).hexdigest())
print('Template:', OUTPUT)
