# Importação de dados cadastrais de clientes via CSV

Implementada em 13/10/2026. Esta funcionalidade é separada da importação Excel existente e grava somente dados cadastrais de `Cliente`.

## Endpoints e tela

- `POST /import/clientes/preview`: valida o arquivo e calcula o resultado previsto, sem persistir alterações.
- `POST /import/clientes`: processa e confirma a importação.
- Ambos exigem `X-Perfil-Id` de um perfil existente e `multipart/form-data`, com o arquivo no campo `file`.
- Sucesso retorna HTTP 200 com o relatório; problemas de arquivo/cabeçalho/perfil retornam HTTP 400.
- `POST /import` permanece com sua implementação original: Excel com clientes, empréstimos e parcelas.

A página Backup / Import possui um card separado, **Importar dados de clientes**, com seleção de CSV, prévia, cancelamento, confirmação explícita e resumo final. A troca de perfil após a prévia exige uma nova prévia. A confirmação reavalia os dados atuais; seus totais podem mudar se o cadastro tiver sido alterado nesse intervalo.

## Formato esperado

Arquivo `.csv`, UTF-8 com ou sem BOM, delimitado por `;`. São aceitos campos entre aspas, aspas escapadas (`""`), ponto e vírgula dentro de campo entre aspas e campos multilinha. Cabeçalhos usam os nomes abaixo, independentemente da ordem.

Cabeçalhos essenciais: `nomeCompleto`, `cpf`, `statusExtracao`. Os demais são opcionais. Cabeçalhos duplicados/vazios, separador incorreto, aspas malformadas e UTF-8 inválido são rejeitados antes de gravar qualquer linha. Um arquivo sem bytes retorna 400; somente cabeçalho retorna resumo com zero linhas. Linhas totalmente vazias não entram nos totais.

Limites: **1 MB**, **10.000 registros** e até **500 detalhes** no relatório. Os totais abrangem todo o arquivo. Colunas com quantidade diferente do cabeçalho recebem erro de linha, sem impedir as outras linhas.

| Coluna CSV | Destino |
| --- | --- |
| `nomeCompleto` | `Cliente.nome` |
| `nacionalidade` | `Cliente.nacionalidade` |
| `estadoCivil` | Enum existente `EstadoCivil` |
| `profissao` | `Cliente.profissao` |
| `rg` | `Cliente.rg`, formatado pelo `RgUtil` |
| `rgOrgaoExpedidor` | `Cliente.rgOrgaoExpedidor` |
| `cpf` | `Cliente.cpf` |
| `enderecoRua` | `Cliente.enderecoRua` |
| `enderecoNumero` | Inteiro existente `Cliente.enderecoNumero` |
| `enderecoComplemento` | `Cliente.enderecoComplemento` |
| `enderecoBairro` | `Cliente.enderecoBairro` |
| `enderecoCep` | String `Cliente.enderecoCep` |
| `enderecoCidade` | `Cliente.enderecoCidade` |
| `enderecoEstado` | String UF `Cliente.enderecoEstado` |
| `sexoDocumento` | Enum existente `SexoDocumento` |
| `nomeIndicador` | `Cliente.nomeIndicador` |
| `pastaOrigem`, `arquivoOrigem`, `dataArquivo`, `observacoes` | Não persistidos |
| `statusExtracao` | Controle da importação; não persistido |

Colunas adicionais não são atribuídas à entidade. Não são criados campos no banco para metadados da extração, e esses metadados não são utilizados para modificar documentos ou datas cadastrais.

Exemplo mínimo:

```csv
nomeCompleto;cpf;statusExtracao;rg;rgOrgaoExpedidor;enderecoCep;estadoCivil;sexoDocumento;profissao
Cliente de exemplo;00000000002;OK;36956870;SSP/SP;00100000;união estável;FEMININO;motorista
```

O CPF deste exemplo é demonstrativo da estrutura textual; esta importação não implementa checksum de CPF.

## Identificação e atualização segura

A identificação começa pelo **nome normalizado dentro do perfil ativo**, com CPF como alternativa e verificação adicional de identidade. Nome é comparado sem espaços externos/duplicados, maiúsculas ou acentos simples; a grafia salva não é modificada para fazer matching. Pontos, hífens e espaços não diferenciam CPFs. CPF, RG e CEP permanecem strings, preservando zeros iniciais. A regra de unicidade existente, por perfil, não foi alterada: o mesmo CPF pode existir em outro perfil.

- Cliente identificado inequivocamente pelo nome pode ter seus campos vazios completados mesmo sem CPF na linha OK; um CPF informado deve ser válido. Cliente novo sem CPF continua bloqueado para revisão, preservando a validação anterior de criação. REVISAR/DUPLICIDADE continuam exigindo os campos finais completos segundo a política de qualidade.
- CPF inválido estruturalmente: erro de linha. São exigidos 11 dígitos após remover a máscara; não foi adicionado cálculo de dígito verificador ao cadastro manual.
- Nome não encontrado: tenta CPF normalizado. Se nenhum dos dois encontrar cliente, cria somente o cadastro, com nome e CPF válidos. Não é chamado o fluxo de criação de empréstimos.
- Cliente já existente: aplica somente a política **ATUALIZAR_VAZIOS**. Valor preenchido no CSV pode completar campo nulo ou string em branco. CSV vazio nunca apaga um valor existente. Zero numérico é valor preenchido.
- Campo preenchido diferente nos dois lados: preserva o banco e registra **CONFLITO_PRESERVADO**, com nomes dos campos diferentes. Outros campos vazios dessa mesma linha podem ser preenchidos. Não há sobrescrita automática. Nome existente nunca é trocado; grafias equivalentes para matching não geram conflito de nome.
- Mesmo CPF repetido no arquivo: mantém o primeiro registro válido processado e registra **DUPLICADO_NO_ARQUIVO** nas repetições. Uma linha inválida/ignorada não impede uma ocorrência válida posterior.
- Mais de um cliente com nome normalizado igual: **AMBIGUO_NOME**, salvo se um CPF único identificar inequivocamente um dos homônimos. CPF duplicado no banco gera **AMBIGUO_CPF**. Nenhum candidato é escolhido arbitrariamente.
- Nome e CPF apontando para pessoas diferentes, ou nome encontrado com CPF preenchido incompatível: **CONFLITO_IDENTIDADE**. Nenhum campo é atualizado nessa linha, nem mesmo campos vazios.
- Reimportação sem mudanças: cliente existente sem campos vazios é ignorado, sem duplicar.

Comparações de CPF/RG/CEP desconsideram máscara. Textos equivalentes desconsideram diferença de maiúsculas e espaços repetidos; nacionalidade brasileira aceita as variantes equivalentes já tratadas pelo formatter. Nenhuma dessas comparações reescreve campos preenchidos do banco.

O planejamento da atualização fica isolado da leitura CSV e da persistência no método `planejar`. Essa separação permite evolução futura das políticas; nesta versão não existe parâmetro para sobrescrever dados ou mudar a política segura.

## Validações e normalizadores

- RG: usa exclusivamente `RgUtil`, aceitando 7–13 caracteres significativos, zeros e X final, sem inventar DV. Ex.: `36956870` → `36.956.870`; `52779723x` → `52.779.723-X`.
- CEP: oito dígitos, com ou sem hífen; `00100000` → `00100-000`.
- Estado civil: enum existente e variações amigáveis masculinas/femininas, incluindo `viúva` e `união estável`.
- Sexo documental: MASCULINO/FEMININO, sem distinção de maiúsculas. Nunca inferido pelo nome.
- Nacionalidade: reutiliza o padrão cadastral existente; brasileira/brasileiro com sexo documental explícito é armazenado como `BRASIL`.
- Órgão: sigla/UF brasileira quando informado, sem lista fechada de órgãos. `SDP/PE` é preservado como `SDP/PE`; não é convertido para SSP. A mesma validação estrutural é utilizada nos contratos novos. RG sem órgão utiliza o fallback central.
- Endereço/UF: valida contra `UfBrasil` quando preenchido.
- Número: respeita o tipo inteiro existente. Valores como `SN` ou fora do intervalo de Integer são reportados para revisão, sem converter silenciosamente.
- Campos textuais não podem exceder 255 caracteres, limite das colunas cadastrais atuais.

`statusExtracao` é uma indicação de qualidade, avaliada pelo `QualidadeClienteCsv` junto aos campos finais e às observações. OK mantém as validações anteriores. REVISAR pode ser importável com aviso se nome, CPF, RG, nacionalidade, estado civil, profissão, rua, número, cidade, UF e sexo documental estiverem preenchidos e os valores forem válidos. Não é inferido nenhum campo. INCOMPLETO, SEM_WORD, ERRO_LEITURA e status desconhecido permanecem bloqueados.

DUPLICIDADE exige campos finais completos/válidos, um único registro do CPF no CSV e evidência de consolidação nas observações. O formato conhecido do extrator (`Documentos considerados:` e, se houver divergências, `utilizados dados mais recentes`) é reconhecido. Notas de conflito irresolvido, ausência de decisão/consolidação ou múltiplos registros finais mantêm `BLOQUEADO_DUPLICIDADE`. Essas linhas só são processadas após a confirmação, como as demais.

“Amasiado/amasiada” não foi convertido para UNIAO_ESTAVEL: não foi encontrada uma regra explícita de negócio para essa equivalência. Dados incompletos ou enum não reconhecido permanecem para revisão, sem modificar o extrator ou o CSV.

## Transações, isolamento e performance

O arquivo é validado sintaticamente antes da gravação. Cada cliente é processado em uma transação curta própria: erro de persistência reverte somente essa linha e permite continuar as próximas. A prévia cria índices de nome/CPF a partir de uma leitura do perfil e simula os efeitos no índice, sem persistir. A confirmação consulta uma projeção leve de identidade (id, nome, CPF) por transação para revalidar o matching sob o lock do perfil, sem buscar campos financeiros ou consultar cada campo cadastral individualmente.

Antes de gravar, o cliente selecionado é relido sob lock e nome/CPF são conferidos novamente. Entidades do índice são destacadas do contexto de persistência para não reutilizar estado antigo. As importações deste novo fluxo são serializadas por perfil mediante lock do perfil durante a transação de cada linha. Os índices são reavaliados antes de criar, inclusive quando outra importação concorrente já criou o registro. Não foi criada ou modificada constraint global de CPF.

O teste com 1.000 clientes passou em cerca de três segundos em H2 na rodada complementar inicial. Isso verifica processamento do lote no ambiente de testes; não é uma medição do PostgreSQL de produção.

## Relatório

DTOs públicos: `ImportacaoClientesResultadoDTO` e seu registro `Detalhe`.

Campos do resumo: `previa`, `totalLinhas`, `criados`, `existentes`, `atualizados`, `ignorados`, `conflitos`, `erros`, `detalhesTruncados`, `detalhes`, `importaveisSemRessalvas`, `importaveisComAviso`, `importadosComAviso`, `bloqueados`, `semWord`, `duplicidadesRevisar`.

Os cinco contadores de resultado são exclusivos por linha: criados + atualizados + ignorados + conflitos + erros = totalLinhas. `existentes` é informação adicional. Uma linha com conflitos conta em `conflitos`, mesmo que complete campos vazios; os campos preenchidos aparecem em `camposPreenchidos`.

Cada detalhe inclui linha física inicial do registro, nome, CPF mascarado, status, motivo, campos conflitantes e campos preenchidos. CPF completo, RG e endereço não são registrados em logs pela implementação. Motivos não incluem valores divergentes ou mensagens de exceções SQL.

Os detalhes incluem também `statusCsv`, `classificacao`, `comAviso` e `camposAusentes`. Qualidade e ação são conceitos separados: um cadastro pode ser importável com aviso e também apresentar conflito com o banco. O banco continua protegido pela política ATUALIZAR_VAZIOS. Bloqueados incluem erros; Sem Word e duplicidades para revisar são subconjuntos de bloqueados. As contagens de qualidade não devem ser somadas novamente aos totais operacionais.

O resumo acrescenta `clientesEncontradosPorNome`, `clientesEncontradosPorCpf`, `camposPreenchidos`, `conflitosPreservados`, `ambiguos`, `conflitosIdentidade` e `clientesAtualizados`. Este último inclui cadastros existentes com campos efetivamente completados, mesmo quando outros campos da linha geram CONFLITO_PRESERVADO; é uma contagem adicional, não deve ser somada aos cinco contadores exclusivos legados. A prévia mostra a previsão e a confirmação mostra o resultado efetivo.

Cada detalhe informa `match`: NOME, CPF, NOVO ou AMBIGUO. Linhas bloqueadas antes de avaliar identidade permanecem NAO_IDENTIFICADO. A escolha do cliente e a política de qualidade não foram misturadas; nome não torna um registro inválido automaticamente importável.

As observações são resumidas sem devolver a qualificação original, documentos pessoais ou caminhos de arquivos. Campos faltantes finais são listados separadamente de observações históricas sobre campos ausentes em documentos antigos. A interface possui filtros Todos, Novos, Com aviso, Conflitos, Bloqueados e Erros.

A interface permite baixar `resultado_importacao_clientes.csv`, UTF-8 com BOM e CPF mascarado. O download contém os detalhes retornados, incluindo status CSV, classificação e ação, limitado aos primeiros 500 registros quando houver truncamento, sinalizado na tela. Células do relatório são escapadas e protegidas contra fórmulas de planilha.

## O que permanece preservado

Não são criados, excluídos ou recalculados empréstimos, parcelas, pagamentos, contratos ou snapshots. Clientes existentes não são apagados. DOCX, PDF, PDF assinado, snapshot da promissória e hashes históricos permanecem iguais. A importação/exportação Excel e a criação manual mantêm seus comportamentos.

Única extração no serviço manual: a normalização de CPF foi delegada a `CpfUtil` com a mesma expressão e comportamento anteriores, para reutilizar a comparação. A regra estrutural de CPF do CSV não foi aplicada ao fluxo manual.

## Evidências de validação

Logs e screenshots em `.auditoria/importacao-clientes-csv/`:

- `testes.log`: primeira rodada focada, 84 testes aprovados.
- `testes-complementares.log`: campos inválidos, concorrência e lote de mil clientes.
- `testes-final.log`: validação final da leitura CSV, serviço e HTTP, incluindo a releitura segura por CPF.
- `duplicidade-final-testes.log`: confirma que o primeiro registro válido de cliente existente, mesmo sem mudanças, também bloqueia repetições posteriores no arquivo.
- `import-antigo-regressao.log`: cinco regressões específicas do fluxo Excel, além da leitura CSV/limite de upload.
- `ui-testes.log` / `ui-testes.json`: navegador com API mockada, verificando prévia sem gravação, cancelar, mudança de perfil, confirmação única, relatório, layout mobile e chamada separada ao import antigo. O multipart e a persistência reais foram verificados pelos testes backend.
- `frontend-lint.log`, `frontend-build.log` e `package.log`: verificações de build.

O teste documental cria dois empréstimos, dez parcelas e um contrato, completa endereço cadastral vazio e compara todos os dados financeiros, JSONs, hashes e bytes dos três downloads antigos. Nenhum banco PostgreSQL de uso real foi alterado.

Resultado consolidado: **103 testes focados distintos aprovados**, zero falhas, erros ou ignorados; **60 cenários novos** de leitura CSV, serviço e HTTP. O consolidado está em `testes-consolidados.json`. `mvn -DskipTests package`, `npm run lint` e `npm run build` passaram. O build frontend mantém o aviso de tamanho de bundle já existente, sem erro. O servidor Vite temporário da validação foi encerrado. Os seis hashes de templates e assinaturas permanecem iguais aos registros anteriores da revisão documental.

## Revisão da qualidade — CSV original de 273 registros

Arquivo somente lido: `C:/Users/caioe/Desktop/_export_clientes/clientes_extraidos.csv`. SHA-256 antes/depois: `8c7247bf6a75ebdf6c14689d3a2f942dc6c352b8ab2df99da08f6edf9fc9689f`.

| Status CSV | Sem ressalvas | Com aviso | Bloqueados |
| --- | ---: | ---: | ---: |
| OK | 149 | 0 | 0 |
| REVISAR | 0 | 79 | 22 |
| DUPLICIDADE | 0 | 7 | 1 |
| INCOMPLETO | 0 | 0 | 10 |
| SEM_WORD | 0 | 0 | 5 |
| Total | 149 | 86 | 38 |

Antes, 124 eram ignorados exclusivamente por não serem OK. O único OK inválido era a linha 70, Eliseu Torres Arcoverde da Silva: `rgOrgaoExpedidor = SDP/PE` era rejeitado pela exigência de SSP. O RG `86.104.87` tem sete dígitos significativos e é preservado. Agora esse registro é importável, sem trocar sua sigla.

**235 registros estão elegíveis após confirmação**, sem importar bloqueados. A quantidade efetivamente criada/atualizada depende dos CPFs e campos já existentes no perfil de destino; esses valores foram medidos em um perfil de teste sem CPFs correspondentes.

Validação desta revisão: 158 testes focados passaram. Após refinamento dos motivos, foram repetidos os 21 testes de qualidade e a prévia HTTP do CSV original, também aprovados. O teste manteve exatamente 100 clientes no perfil antes e depois, comparou todos os registros cadastrais e conferiu os bytes do CSV. Nenhum registro do CSV foi importado no PostgreSQL de uso real.

Logs em `.auditoria/qualidade-csv/`: `testes.log`, `motivos-final-testes.log`, `preview-original.json`, `original-sha256.txt`, `package.log`, `frontend-lint.log`, `frontend-build.log`, `ui.log` e `ui-resultado.json`. O navegador validou os seis filtros com a resposta HTTP real obtida em H2, usando API mockada; nenhum acesso de importação ao banco real. Backend package, lint e build frontend aprovados. O card tracejado permanece.

## Sequência Excel → CSV e matching por nome

Fluxo recomendado: importar primeiro o Excel antigo para criar clientes/financiamentos; depois executar a prévia CSV e confirmar o enriquecimento cadastral. O CSV procura o mesmo Cliente criado pelo Excel, inclusive quando seu CPF está vazio, e mantém o ID e todos os vínculos. Nome abreviado diferente pode ser identificado por CPF; sem CPF que confirme identidade, não existe matching aproximado por partes do nome.

O perfil autorizado para esta revisão foi **Edson, ID 1**. A leitura da API retornou zero clientes, confirmado novamente ao fim da validação. A prévia calculada com essa leitura e o CSV original retornou:

| Indicador | Quantidade |
| --- | ---: |
| Encontrados por nome | 0 |
| Encontrados por CPF | 0 |
| Candidatos novos | 235 |
| Cadastros existentes a completar | 0 |
| Bloqueados pela qualidade | 38 |

Esses números descrevem o banco consultado neste momento, antes da etapa Excel. Depois de importar o Excel, a prévia deve ser recalculada para mostrar os clientes existentes que serão enriquecidos. Nenhum CSV ou Excel foi importado no PostgreSQL real pelo agente; o acesso ao perfil foi somente leitura.

Validação do ajuste: **109 testes focados distintos aprovados**, sem falhas, erros ou ignorados (103 na rodada principal; 17 na rodada final com sobreposição). Incluem nome/case/espaços/acentos, CPF como alternativa, conflito de identidade, homônimos com e sem CPF inequívoco, novo cliente, isolamento, rollback por linha, concorrência, mil clientes, finanças e histórico documental.

O teste Excel → CSV criou um cliente sem CPF, três empréstimos e nove parcelas em H2; o CSV preencheu CPF/RG/endereço no mesmo ID. Todas as linhas financeiras permaneceram iguais. Outro teste mantém dois empréstimos, dez parcelas e um contrato histórico: compara dados financeiros, snapshots, hashes e bytes dos três downloads após enriquecer o mesmo cliente por nome. Cinco regressões específicas do import antigo também passaram.

Backend `mvn -DskipTests package`, frontend `npm run lint` e `npm run build`: aprovados. Interface conferida com API mockada e visual desktop/mobile preservado. Evidências em `.auditoria/matching-clientes/`: `perfil-edson-antes.json` (leitura autorizada), `preview-edson.json`, `testes.log`, `excel-csv-final-testes.log`, `package.log`, `frontend-lint.log`, `frontend-build.log`, `ui.log` e `ui-resultado.json`. O CSV original permanece com o SHA-256 registrado acima.
