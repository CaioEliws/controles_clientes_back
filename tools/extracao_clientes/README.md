# Extração local de clientes de Word

Ferramenta Python isolada. Sempre opera em DRY RUN: lê os originais, converte `.doc` em cópias temporárias locais com LibreOffice e gera CSVs. Não chama serviços, APIs ou banco de dados. Antes de publicar as saídas, verifica o SHA-256 dos Word lidos.

## Executar

Na raiz do backend, com Python 3.10 ou superior:

```powershell
python -m pip install -r tools/extracao_clientes/requirements.txt
python tools/extracao_clientes/extrair_clientes.py "F:\caio teste\financiamento\C\Edson\financiamento\contrato"
```

Se `python` não estiver instalado, o runtime do Codex usado nesta máquina é:

```powershell
& 'C:\Users\caioe\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' tools/extracao_clientes/extrair_clientes.py
```

O caminho raiz acima é o padrão. O LibreOffice é localizado no PATH ou na instalação Windows usual. Pode ser informado com `--libreoffice "C:\Program Files\LibreOffice\program\soffice.exe"`.

```powershell
python tools/extracao_clientes/extrair_clientes.py --pasta "Adriele Aparecida Bento (amiga Janaína)"
python tools/extracao_clientes/extrair_clientes.py --limite 5
python tools/extracao_clientes/extrair_clientes.py --paragrafos 40
python tools/extracao_clientes/extrair_clientes.py --saida "C:\caminho\exportacao"
python -m unittest discover -s tools/extracao_clientes -v
```

## Saídas

O padrão é `<raiz>\_export_clientes`. CSVs em UTF-8 com BOM, separados por `;`:

- `clientes_extraidos.csv`: clientes consolidados e diagnósticos sem identidade ao final.
- `clientes_revisar.csv`: todos os clientes que não são OK e diagnósticos de pastas/arquivos.
- `arquivos_processados.csv`: auditoria de todos os Word encontrados.
- `relatorio_extracao.txt`: contagens da execução e critérios.

Cada execução preserva cópias com timestamp e microssegundos. Os nomes fixos são atualizados somente após gerar todos os arquivos e verificar os originais. Falhas de um documento são registradas e não interrompem os demais. Falhas de varredura ou publicação encerram a execução com código 1; os snapshots de execuções anteriores permanecem disponíveis.

## Critérios de revisão

Cada filho direto da raiz é tratado como pasta principal de cliente e é percorrido recursivamente. Diretórios `_export_clientes*`, links e temporários `~$` não são fontes. Os primeiros 20 parágrafos não vazios são examinados, inclusive em tabelas. O limite pode ser aumentado. O nome da pasta apenas valida o nome extraído; não preenche cadastro nem indicador. `nomeIndicador` exige indicação explícita no texto.

O credor Edson é excluído por nome e CPF. Papéis CREDOR e FIADOR são excluídos, com prioridade ao DEVEDOR explícito. Qualificações de várias pessoas sem devedor inequívoco não geram cliente; a pasta entra para revisão. Um único candidato sem papel explícito recebe REVISAR.

Deduplicação por CPF com 11 dígitos; na ausência dele, nome e RG. Nunca agrupa só por nome. Dados mais completos têm preferência e a data de modificação desempata; DOCX desempata depois da recência. Em divergências, usa-se o dado mais recente por campo, mantendo o endereço como bloco para evitar misturar endereços. Todas as fontes e qualificações alternativas ficam nas observações. A data de modificação não garante a data contratual, portanto conflitos entram em REVISAR.

Identidade encontrada em múltiplas pastas recebe `DUPLICIDADE` (lista final de status da tarefa). Um CPF pode estar deduplicado mesmo que os documentos tenham nomes divergentes: revisar as observações. Documentos de pessoas diferentes na mesma pasta não são fundidos automaticamente.

Campos principais exigidos para OK: nome, CPF, RG, nacionalidade, estado civil, profissão, rua, número, cidade, UF e evidência gramatical inequívoca de sexo. Bairro, complemento, CEP e indicador podem estar vazios quando ausentes no texto. Conflitos e gramática ambígua exigem revisão. Não há inferência por nome nem validação de dígitos verificadores de CPF/RG.

Nacionalidade é texto, estados civis e sexo seguem os enums Java existentes. CPF e CEP usam as máscaras de apresentação do sistema, como strings. A formatação de RG reproduz `RgUtil.formatar` em Python, preservando pontuação e RG legado. O modelo Java tem `enderecoNumero` inteiro: números alfanuméricos e `s/n` são preservados no CSV e marcados para revisão. `nomeCompleto` corresponde a `Cliente.nome`; o importador existente importa empréstimos de XLSX e não aceita diretamente este CSV. Sua adaptação/importação fica para a segunda etapa.

Ao usar Excel, importe CPF, RG e CEP como **Texto** para preservar zeros. O CSV de revisão pode conter várias linhas de diagnóstico por pasta; essas linhas não contam como clientes únicos.

## Execução validada em 13/10/2026

Teste inicial: pasta Adriele, 4 DOCX, 1 cliente OK, 15 campos conferidos com o exemplo. Varredura completa: 271 pastas principais, 1.163 DOCX e 4 DOC, todos lidos e verificados por SHA-256. Resultado: 266 identidades consolidadas, 149 OK, 99 REVISAR, 10 INCOMPLETO e 8 DUPLICIDADE. Cinco pastas sem Word e duas pastas com Word sem devedor identificado geraram diagnósticos. Zero erros de leitura. CSV de revisão: 124 linhas (117 clientes e 7 diagnósticos).

Na varredura completa, Adriele recebe DUPLICIDADE porque seu CPF também aparece em documentos de outra pasta. Os campos do exemplo permanecem corretos, e as pastas/fontes estão nas observações.
