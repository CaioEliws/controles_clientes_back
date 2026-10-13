"""Extração local DRY RUN: originais somente leitura, sem API ou banco."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from docx import Document

DEFAULT_ROOT = Path(r"F:\caio teste\financiamento\C\Edson\financiamento\contrato")
FIELDS = "nomeCompleto nacionalidade estadoCivil profissao rg rgOrgaoExpedidor cpf enderecoRua enderecoNumero enderecoComplemento enderecoBairro enderecoCep enderecoCidade enderecoEstado sexoDocumento nomeIndicador pastaOrigem arquivoOrigem dataArquivo statusExtracao observacoes".split()
AUDIT = "pasta arquivo tipo dataModificacao processado clienteIdentificado cpfIdentificado motivoSelecao erro".split()
DATA = FIELDS[:16]
REQUIRED = "nomeCompleto cpf rg nacionalidade estadoCivil profissao enderecoRua enderecoNumero enderecoCidade enderecoEstado".split()
UF = set("AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split())
NUM = r"(?:n\s*[º°o.]|nro\.?|n[uú]mero)\s*:?\s*"


def fold(value):
    return "".join(c for c in unicodedata.normalize("NFD", value.casefold()) if unicodedata.category(c) != "Mn")


def clean(value):
    return re.sub(r"\s+", " ", value).strip(" ,;:\t\r\n")


def digits(value):
    return re.sub(r"\D", "", value)


def filesystem_path(path):
    """Suporte a caminhos Windows acima de 260 caracteres, sem renomear."""
    value = str(path.absolute())
    if os.name == 'nt' and not value.startswith('\\\\?\\'):
        value = '\\\\?\\UNC\\' + value[2:] if value.startswith('\\\\') else '\\\\?\\' + value
    return Path(value)


def match(pattern, text):
    m = re.search(pattern, text, re.I)
    return clean(m.group(1)) if m else ""


def format_rg(value):
    # Port fiel do RgUtil.formatar: preserva pontuação e aceita legados.
    value = re.sub(r"\s", "", value).upper()
    normalized = re.sub(r"[.\-/]", "", value)
    if value != normalized or not re.fullmatch(r"(?:[0-9]{7,13}|[0-9]{6,12}X)", normalized):
        return value
    def group(n):
        return re.sub(r"(?<=\d)(?=(?:\d{3})+$)", ".", n)
    if len(normalized) == 9:
        return group(normalized[:8]) + "-" + normalized[8:]
    return group(normalized.rstrip("X")) + ("X" if normalized.endswith("X") else "")


def parse_address(text, row, notes):
    text = re.split(r"\bdoravante\b|\bdenominad[oa]\b", text, flags=re.I)[0].strip(" .;,\u201d\u201c")
    cep = match(r"\bcep\s*:?\s*(\d{5}[ -]?\d{3})(?!\d)", text)
    if cep:
        d = digits(cep)
        row['enderecoCep'] = d[:5] + '-' + d[5:]
        text = re.sub(r"\bcep\s*:?\s*\d{5}[ -]?\d{3}\s*,?", "", text, flags=re.I)
    city = re.search(r"(?:,\s*|\bem\s+)([^,]+?)\s*[-–—/]\s*([A-Z]{2})\.?$", text, re.I)
    if city and city[2].upper() in UF:
        row['enderecoCidade'] = clean(city[1])
        row['enderecoEstado'] = city[2].upper()
        text = text[:city.start()]
    parts = [clean(p) for p in text.split(',') if clean(p)]
    if not parts:
        return
    row['enderecoRua'] = parts.pop(0)
    number = re.search(r"(?:^|\s)" + NUM + r"(\d+[A-Za-z]?|s/?n)\b(.*)", row['enderecoRua'], re.I)
    if number:
        row['enderecoRua'] = clean(row['enderecoRua'][:number.start()])
    elif parts:
        number = re.match(r"(?:" + NUM + r")?(\d+[A-Za-z]?|s/?n)\b(.*)", parts[0], re.I)
        if number:
            parts.pop(0)
    if number:
        row['enderecoNumero'] = number[1]
        row['enderecoComplemento'] = clean(number[2])
        if not number[1].isdigit():
            notes.append('Número de endereço não inteiro; revisar compatibilidade com modelo Java.')
    for part in parts:
        if re.match(r"(?:casa|ap(?:to|artamento)?\.?|bloco|fundos|complemento|sala|lote|quadra)\b", part, re.I):
            row['enderecoComplemento'] = clean(row['enderecoComplemento'] + ' ' + part)
        elif not row['enderecoBairro']:
            row['enderecoBairro'] = re.sub(r"^bairro\s*:?\s*", "", part, flags=re.I)
        else:
            notes.append('Trecho de endereço não reconhecido: ' + part)


def parse_qualification(text):
    text = clean(text)
    row = dict.fromkeys(FIELDS, '')
    notes = []
    name = text.split(',')[0]
    name = re.sub(r"^.*?\b(?:devedor(?:a)?|mutu[aá]ri[oa])\s*[:–—-]\s*", "", name, flags=re.I)
    row['nomeCompleto'] = clean(name).strip('“”"')
    row['nacionalidade'] = match(r"nacionalidade\s*:\s*([^,;]+)", text).lower()
    if not row['nacionalidade']:
        row['nacionalidade'] = match(r",\s*(brasileir[oa])\b", text).lower()
    civil = match(r"\b(solteir[oa]|casad[oa]|divorciad[oa]|vi[uú]v[oa]|separad[oa]|uni[aã]o\s+est[aá]vel)\b", text)
    if civil:
        row['estadoCivil'] = {'solteir':'SOLTEIRO', 'casad':'CASADO', 'divorciad':'DIVORCIADO', 'viuv':'VIUVO', 'separad':'SEPARADO'}.get(fold(civil)[:-1], 'UNIAO_ESTAVEL')
    else:
        unusual = match(r"estado\s+civil\s*:\s*([^,;]+)", text)
        if unusual:
            notes.append('Estado civil não reconhecido: ' + unusual)
    gender = set()
    for word in (row['nacionalidade'], civil):
        if re.fullmatch(r"brasileir[oa]|solteir[oa]|casad[oa]|divorciad[oa]|vi[uú]v[oa]|separad[oa]", word, re.I):
            gender.add('FEMININO' if word.lower().endswith('a') else 'MASCULINO')
    if len(gender) == 1:
        row['sexoDocumento'] = next(iter(gender))
    else:
        notes.append('Sexo documental ausente ou evidência gramatical conflitante.')
    row['profissao'] = match(r"profiss[aã]o\s*:\s*([^,;]+)", text)
    row['rg'] = format_rg(match(r"\bRG\)?\s*[:.]?\s*(?:" + NUM + r")?([0-9][0-9. /-]*[0-9Xx])", text))
    row['rgOrgaoExpedidor'] = match(r"expedid[oa]\s+por\s+([^,;]+)", text).upper()
    raw_cpf = match(r"\bCPF\s*[:.]?\s*(?:" + NUM + r")?([0-9][0-9. -]*[0-9])", text)
    d = digits(raw_cpf)
    row['cpf'] = f'{d[:3]}.{d[3:6]}.{d[6:9]}-{d[9:]}' if len(d) == 11 else raw_cpf
    if raw_cpf and len(d) != 11:
        notes.append('CPF com estrutura incompleta; preservado texto original.')
    address = match(r"endere[cç]o\s*:\s*(.+)$", text)
    if not address:
        address = match(r"resident[e]\s+e\s+domiciliad[oa]\s+(?:na|no|[aà])\s+(.+)$", text)
    if address:
        parse_address(address, row, notes)
    row['nomeIndicador'] = match(r"(?:indicado\s+por|indicador[a]?|indica[cç][aã]o)\s*:\s*([^,;]+)", text)
    missing = [k for k in REQUIRED if not row[k]]
    if missing:
        notes.append('Campos ausentes: ' + ', '.join(missing))
    # Qualificação integral mantém informações sem mapeamento para revisão local.
    notes.append('Qualificação original: ' + text)
    row['statusExtracao'] = 'REVISAR' if len(gender) != 1 or any('não reconhecido' in n or 'não inteiro' in n or 'estrutura' in n for n in notes) else ('INCOMPLETO' if missing else 'OK')
    row['observacoes'] = ' | '.join(notes)
    return row


def extract_candidates(paragraphs):
    results = []
    guarantors = []
    for i, text in enumerate(paragraphs):
        if not re.search(r'\bCPF\b', text, re.I):
            continue
        row = parse_qualification(text)
        if fold(row['nomeCompleto']) == 'edson antonio vieira' or digits(row['cpf']) == '07284805811':
            continue
        # Papel no parágrafo ou declaração imediatamente seguinte tem precedência.
        role_text = text + ' ' + (paragraphs[i+1] if i+1 < len(paragraphs) else '')
        role = re.search(r'\b(?:denominad[oa]\s*[“"\u201d]?\s*|^\s*)(DEVEDOR[A]?|CREDOR[A]?|FIADOR[A]?|MUTU[AÁ]RI[OA])\b', role_text, re.I)
        role = role[1].upper() if role else ''
        if not role and i and re.fullmatch(r'\s*(?:FIADOR[A]?|CREDOR[A]?|DEVEDOR[A]?)\s*[:.-]?\s*', paragraphs[i-1], re.I):
            role = clean(paragraphs[i-1]).strip(':.-').upper()
        if role.startswith('FIADOR'):
            guarantors.append(row['nomeCompleto'])
            continue
        if role.startswith('CREDOR'):
            continue
        if not row['nomeCompleto'] or len(row['nomeCompleto'].split()) < 2 or not re.search(r'\bRG\b|profiss[aã]o|nacionalidade', text, re.I):
            continue
        row['_explicit_debtor'] = role.startswith(('DEVEDOR', 'MUTU'))
        results.append(row)
    explicit = [r for r in results if r['_explicit_debtor']]
    results = explicit or results
    # Sem papel explícito só aceitar identidade única, nunca escolher fiador por completude.
    identities = {(digits(r['cpf']), fold(r['nomeCompleto'])) for r in results}
    if len(identities) > 1:
        return [], 'Qualificações ambíguas; devedor principal não identificado com segurança.'
    for row in results:
        if not row['_explicit_debtor']:
            row['statusExtracao'] = 'REVISAR'
            row['observacoes'] += ' | Papel DEVEDOR não explícito; validar.'
        if guarantors:
            row['observacoes'] += ' | Fiador encontrado: ' + ', '.join(guarantors)
    return results, ''


def read_word(path, soffice, limit):
    def paragraphs(p):
        doc = Document(filesystem_path(p))
        # Ordem XML inclui parágrafos em tabelas, sem duplicar células mescladas.
        texts = []
        for para in doc.element.body.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p'):
            t = ''.join(n.text or '' for n in para.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'))
            if clean(t):
                texts.append(clean(t))
            if len(texts) >= limit:
                break
        return texts
    if path.suffix.lower() == '.docx':
        return paragraphs(path)
    if not soffice:
        raise RuntimeError('LibreOffice não encontrado; informe --libreoffice.')
    with tempfile.TemporaryDirectory(prefix='extracao_clientes_') as tmp:
        base = Path(tmp)
        profile = (base / 'profile').as_uri()
        local_copy = base / 'fonte.doc'
        local_copy.write_bytes(filesystem_path(path).read_bytes())
        converted = base / 'fonte.docx'
        run = subprocess.run([str(soffice), '-env:UserInstallation=' + profile, '--headless', '--convert-to', 'docx', '--outdir', str(base), str(local_copy)], capture_output=True, timeout=90, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        if run.returncode or not converted.exists():
            raise RuntimeError('Falha na conversão temporária LibreOffice.')
        return paragraphs(converted)


def completeness(row):
    return sum(bool(row[k]) for k in DATA)


def consolidate(candidates):
    groups = defaultdict(list)
    for row in candidates:
        cpf = digits(row['cpf'])
        rg = re.sub(r'\W', '', row['rg'])
        key = ('cpf', cpf) if len(cpf) == 11 else (('nome_rg', fold(row['nomeCompleto']), rg) if rg else ('arquivo', row['arquivoOrigem']))
        groups[key].append(row)
    rows = []
    selected = set()
    for group in groups.values():
        best = max(group, key=lambda r: (completeness(r), r['_mtime'], r['_docx'], r['arquivoOrigem']))
        row = best.copy()
        selected.add(best['arquivoOrigem'])
        notes = [best['observacoes']]
        conflicts = []
        # Não criar endereço híbrido: bloco inteiro do documento mais recente com endereço.
        blocks = [[k for k in DATA if k.startswith('endereco')]] + [[k] for k in DATA if not k.startswith('endereco')]
        for block in blocks:
            available = [r for r in group if any(r[k] for k in block)]
            if not available:
                continue
            variants = {tuple(fold(r[k]) for k in block) for r in available}
            if len(variants) > 1:
                source = max(available, key=lambda r: (r['_mtime'], completeness(r), r['_docx']))
                conflicts.append('/'.join(block))
            else:
                source = max(available, key=lambda r: (completeness(r), r['_mtime'], r['_docx']))
            if any(source[k] != row[k] for k in block):
                notes.append('Fonte de ' + '/'.join(block) + ': ' + source['arquivoOrigem'])
                selected.add(source['arquivoOrigem'])
            for k in block:
                row[k] = source[k]
        folders = sorted({r['pastaOrigem'] for r in group})
        if len(group) > 1:
            notes.append('Documentos considerados: ' + '; '.join(sorted({r['arquivoOrigem'] for r in group})))
            # Guardar qualificações alternativas, incluindo dados não reconhecidos.
            notes.extend(r['observacoes'] for r in group if r['observacoes'] != best['observacoes'])
        if conflicts:
            notes.append('Divergências: ' + ', '.join(conflicts) + '; utilizados dados mais recentes por campo/bloco de endereço. Data = modificação do arquivo, não data contratual.')
        missing = [k for k in REQUIRED if not row[k]]
        review = conflicts or not row['sexoDocumento'] or any(r['statusExtracao'] == 'REVISAR' for r in group)
        row['statusExtracao'] = 'REVISAR' if review else ('INCOMPLETO' if missing else 'OK')
        if len(folders) > 1:
            row['statusExtracao'] = 'DUPLICIDADE'
            notes.append('Identidade encontrada em múltiplas pastas: ' + '; '.join(folders))
        row['observacoes'] = ' | '.join(dict.fromkeys(notes))
        rows.append(row)
    return rows, selected


def csv_write(path, fields, rows):
    with path.open('x', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=fields, delimiter=';', extrasaction='ignore')
        writer.writeheader()
        writer.writerows(rows)


def run(args):
    root = args.raiz.resolve(strict=True)
    out = (args.saida or root / '_export_clientes').resolve()
    if args.limite is not None and args.limite < 1:
        raise ValueError('--limite deve ser positivo.')
    if args.paragrafos < 2:
        raise ValueError('--paragrafos deve ser pelo menos 2.')
    # Uma pasta de cliente é um filho direto da raiz; todo seu conteúdo é recursivo.
    folders = sorted([p for p in root.iterdir() if p.is_dir() and not p.name.startswith('_export_clientes') and p.resolve() != out and not p.is_symlink()], key=lambda p: fold(p.name))
    total_folders = len(folders)
    if args.pasta:
        folders = [p for p in folders if p.name == args.pasta]
        if not folders:
            raise ValueError('Pasta solicitada não encontrada entre os filhos da raiz.')
    if args.limite:
        folders = folders[:args.limite]
    # Saída nunca pode coincidir com uma pasta de cliente ou ficar dentro dela.
    for p in root.iterdir():
        if p.is_dir() and not p.name.startswith('_export_clientes') and (out == p.resolve() or p.resolve() in out.parents):
            raise ValueError('Saída deve ficar fora das pastas de clientes.')
    if out == root:
        raise ValueError('Use um diretório de saída dedicado.')
    soffice = args.libreoffice or shutil.which('soffice')
    if not soffice:
        for path in (Path(r'C:\Program Files\LibreOffice\program\soffice.exe'), Path(r'C:\Program Files (x86)\LibreOffice\program\soffice.exe')):
            if path.is_file():
                soffice = path
                break
    audit, candidates, pending = [], [], []
    before = {}
    word_count = processed = errors = 0
    def scan_error(exc):
        raise exc  # Nunca omitir silenciosamente um diretório inacessível.
    print('DRY RUN: originais somente leitura; processamento local, sem banco/API.')
    for index, folder in enumerate(folders, 1):
        words = []
        for current, dirs, files in os.walk(filesystem_path(folder), followlinks=False, onerror=scan_error):
            dirs[:] = sorted(d for d in dirs if not d.startswith('_export_clientes') and not (Path(current)/d).is_symlink() and (Path(current)/d).resolve() != out)
            for name in files:
                display_current = current[4:] if current.startswith('\\\\?\\') else current
                path = Path(display_current)/name
                if path.suffix.lower() in {'.doc', '.docx'} and not filesystem_path(path).is_symlink():
                    words.append(path)
        words.sort(key=lambda p: (p.suffix.lower() != '.docx', str(p)))
        print(f'[{index}/{len(folders)}] Pasta: {folder.name}; Word encontrados: {len(words)}')
        found = []
        folder_errors = 0
        for path in words:
            date = ''
            item = dict(zip(AUDIT, [str(folder), str(path), path.suffix.lower(), date, 'NAO', '', '', '', '']))
            word_count += 1
            try:
                stat = filesystem_path(path).stat()
                date = datetime.fromtimestamp(stat.st_mtime, ZoneInfo('America/Sao_Paulo')).isoformat()
                item['dataModificacao'] = date
                before[str(path)] = hashlib.sha256(filesystem_path(path).read_bytes()).hexdigest()
                if path.name.startswith('~$'):
                    item['motivoSelecao'] = 'Arquivo temporário de bloqueio do Word; ignorado.'
                    continue
                paragraphs = read_word(path, soffice, args.paragrafos)
                processed += 1
                item['processado'] = 'SIM'
                parsed, reason = extract_candidates(paragraphs)
                item['motivoSelecao'] = reason or ('Sem qualificação de devedor nos primeiros parágrafos.' if not parsed else 'Candidato; aguardando consolidação.')
                if not parsed:
                    relevant = [p for p in paragraphs if re.search(r'\bCPF\b|\bRG\b|profiss[aã]o|nacionalidade|endere[cç]o', p, re.I)]
                    if relevant:
                        item['motivoSelecao'] += ' Trechos preservados para revisão local: ' + ' | '.join(relevant)
                for row in parsed:
                    row.update(pastaOrigem=str(folder), arquivoOrigem=str(path), dataArquivo=date, _mtime=stat.st_mtime, _docx=path.suffix.lower() == '.docx')
                    # Pasta apenas valida; não preenche nome nem indicador.
                    hint = clean(folder.name.split('(')[0])
                    if fold(hint) != fold(row['nomeCompleto']):
                        row['statusExtracao'] = 'REVISAR'
                        row['observacoes'] += ' | Nome da pasta divergente do documento: ' + folder.name
                    found.append(row)
                    item['clienteIdentificado'] = row['nomeCompleto']
                    item['cpfIdentificado'] = row['cpf']
            except Exception as exc:
                errors += 1
                folder_errors += 1
                item['erro'] = f'{type(exc).__name__}: {exc}'
                item['motivoSelecao'] = 'Erro de leitura/conversão; execução continuou.'
                failure = dict.fromkeys(FIELDS, '')
                failure.update(pastaOrigem=str(folder), arquivoOrigem=str(path), dataArquivo=date, statusExtracao='ERRO_LEITURA', observacoes=item['erro'])
                pending.append(failure)
            finally:
                audit.append(item)
        if not found:
            row = dict.fromkeys(FIELDS, '')
            status = 'ERRO_LEITURA' if folder_errors else ('REVISAR' if words else 'SEM_WORD')
            row.update(pastaOrigem=str(folder), statusExtracao=status, observacoes='Nenhum devedor extraído com segurança; conferir auditoria.' if words else 'Pasta sem arquivos Word.')
            pending.append(row)
        candidates.extend(found)
        if found:
            best = max(found, key=lambda r: (completeness(r), r['_mtime'], r['_docx']))
            cpf = digits(best['cpf'])
            print('  Candidato: ' + best['nomeCompleto'] + '; CPF: ***.***.' + (cpf[6:9] if len(cpf)==11 else '***') + '-**; Status: ' + best['statusExtracao'])
    rows, selected = consolidate(candidates)
    for item in audit:
        if item['clienteIdentificado']:
            item['motivoSelecao'] = 'Selecionado como fonte principal ou de campo consolidado.' if item['arquivo'] in selected else 'Histórico considerado; outro arquivo selecionado por completude/recência.'
    # Nenhuma escrita de saída antes da verificação dos originais.
    for file, digest in before.items():
        if hashlib.sha256(filesystem_path(Path(file)).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Documento original mudou durante execução; saída não publicada.')
    rows.sort(key=lambda r: fold(r['nomeCompleto']))
    # CSV principal inclui diagnósticos sem identidade; eles não contam como clientes.
    main = rows + [r for r in pending if not r['arquivoOrigem']]
    review = [r for r in rows if r['statusExtracao'] != 'OK'] + pending
    counts = Counter(r['statusExtracao'] for r in rows)
    summary = {'pastas_encontradas': total_folders, 'pastas_processadas': len(folders), 'word_encontrados': word_count, 'word_processados': processed, 'clientes_unicos': len(rows), 'OK': counts['OK'], 'REVISAR': counts['REVISAR'], 'INCOMPLETO': counts['INCOMPLETO'], 'DUPLICIDADE': counts['DUPLICIDADE'], 'erros_leitura': errors, 'pendencias_csv': len(review), 'pastas_sem_word': sum(r['statusExtracao']=='SEM_WORD' for r in pending), 'originais_sha256_verificados': len(before)}
    stamp = datetime.now(ZoneInfo('America/Sao_Paulo')).strftime('%Y-%m-%d_%H%M%S_%f')
    out.mkdir(parents=True, exist_ok=True)
    names = ('clientes_extraidos.csv', 'clientes_revisar.csv', 'arquivos_processados.csv', 'relatorio_extracao.txt')
    # Cada execução fica preservada; aliases atualizados apenas após todos os arquivos prontos.
    versioned = {}
    with tempfile.TemporaryDirectory(prefix='.extracao_', dir=out) as tmp:
        stage = Path(tmp)
        csv_write(stage/names[0], FIELDS, main)
        csv_write(stage/names[1], FIELDS, review)
        csv_write(stage/names[2], AUDIT, audit)
        report = 'DRY RUN — processamento local; nenhum banco/API; originais SHA256 verificados.\n' + json.dumps(summary, ensure_ascii=False, indent=2) + '\nData de recência: modificação do arquivo.\nStatus de duplicidade global: DUPLICIDADE.\nLinhas sem identidade são diagnósticos de pasta, não clientes.\nCPF/RG/CEP são strings; ao abrir no Excel, importar essas colunas como Texto.\n'
        (stage/names[3]).write_text(report, encoding='utf-8')
        for name in names:
            target = out / (Path(name).stem + '_' + stamp + Path(name).suffix)
            with target.open('xb') as f:
                f.write((stage/name).read_bytes())
            versioned[name] = str(target)
        for name in names:
            os.replace(stage/name, out/name)
    print(json.dumps(summary, ensure_ascii=False))
    print('Saída: ' + str(out))
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('raiz', nargs='?', type=Path, default=DEFAULT_ROOT)
    parser.add_argument('--saida', type=Path)
    parser.add_argument('--limite', type=int)
    parser.add_argument('--pasta')
    parser.add_argument('--paragrafos', type=int, default=20)
    parser.add_argument('--libreoffice', type=Path)
    parser.add_argument('--dry-run', action='store_true', default=True, help='Sempre ativo: só gera saídas; nunca escreve nos originais.')
    args = parser.parse_args()
    try:
        run(args)
    except Exception as exc:
        parser.exit(1, f'Falha: {type(exc).__name__}: {exc}\n')


if __name__ == '__main__':
    main()
