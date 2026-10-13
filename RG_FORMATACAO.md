# RG: normalização, validação e apresentação

Atualizado em 08/10/2026.

## Fonte de verdade

Toda regra de RG está em `src/main/java/com/caio/controle_clientes/validation/RgUtil.java`. O RG continua sendo `String` no cadastro, DTOs, snapshots e documentos. Não há conversão para número nem cálculo de dígito verificador.

O problema anterior era a divergência entre validação e apresentação: o backend aceitava 7–13 caracteres, mas o formatter só aplicava apresentação aos 9 caracteres sem pontuação. O frontend repetia essa máscara e a regex de validação.

Pontos mapeados e consolidados:

- `RgValidator` / `@RgValido`: criação e edição de cliente; edição de perfil usa a mesma validação.
- `ClienteService`: apresentação persistida na criação/edição, conforme o padrão já adotado pelo sistema.
- `ClienteDetalhadoDTO`: leitura de cliente e listagem formatam sem reescrever o registro legado.
- `PerfilService` / `PerfilDetalhadoDTO`: edição e leitura usam a regra central.
- `ContratoFormatador.rg`: mantém apenas a delegação ao `RgUtil`, sem máscara própria.
- `ContratoFormatador.pessoa`: devedor e fiador usam `RgUtil` com a UF extraída do órgão expedidor.
- `ContratoSnapshotService`: o snapshot novo mantém os dados cadastrais e os campos documentais formatados, como já previsto na arquitetura.
- Frontend: removidas as regras de `src/utils/rg.ts` e seus usos no schema, payload, blur e modal. O formulário mostra a apresentação fornecida pela API e envia o texto informado, sem modificar seus caracteres. O campo permite até 20 caracteres de apresentação.

## Normalização e validação

`normalizar(String)` remove espaços, pontos, hífens e barras; converte `x` para `X`; preserva todos os dígitos, inclusive zeros iniciais. Letras ou símbolos não permitidos não são apagados silenciosamente: a validação os rejeita.

`validar(String)` e o alias compatível `validarEstrutura(String)` aceitam 7–13 caracteres significativos: somente dígitos, ou dígitos seguidos por um único `X` final. O X conta no comprimento. Campo cadastral ausente/vazio continua opcional; as exigências de preenchimento dos documentos permanecem.

Entradas inválidas em criação/edição retornam HTTP 400 com mensagem do campo RG. A leitura de um legado inválido continua tolerante, evitando erro 500. Não há migração ou alteração em massa dos cadastros.

## Apresentação por camadas

1. Uma entrada estruturalmente válida já pontuada mantém sua apresentação, retirando espaços e colocando X em maiúsculo. Exemplos: `64.729.511-8` e `12/345/678` permanecem assim.
2. A API `formatar(String, String ufEmissora)` permite evolução por UF. `extrairUf(String)` reconhece uma UF brasileira isolada ou no final de órgão/UF, como `SSP/SP`. UF nula, desconhecida ou legado resulta em ausência de UF. Atualmente não existem máscaras estaduais verificadas: todas as UFs usam as mesmas regras de compatibilidade e fallback. Nenhuma tabela de 27 máscaras foi inventada.
3. Para nove caracteres sem pontuação, mantém-se a convenção já utilizada no projeto: agrupamento dos oito primeiros e hífen antes do último caractere existente. Isso é compatibilidade documental do projeto, não uma afirmação de máscara oficial nacional ou estadual, nem uma validação de DV.
4. Nos demais tamanhos sem pontuação, agrupa-se a parte numérica de três em três, da direita para a esquerda. Um X final permanece junto ao último grupo, sem hífen que sugira um DV não identificado. Nenhum caractere é adicionado, removido ou calculado além da pontuação visual.

| Entrada | Apresentação |
| --- | --- |
| `1234567` | `1.234.567` |
| `36956870` | `36.956.870` |
| `01234567` | `01.234.567` |
| `409614713` | `40.961.471-3` |
| `001234567` | `00.123.456-7` |
| `52779723x` | `52.779.723-X` |
| `64.729.511-8` | `64.729.511-8` |
| `1234567890` | `1.234.567.890` |
| `12345678901` | `12.345.678.901` |
| `123456789012` | `123.456.789.012` |
| `1234567890123` | `1.234.567.890.123` |
| `123456X` | `123.456X` |
| `123456789012X` | `123.456.789.012X` |

Não existe uma máscara nacional única de RG adotada por este sistema. UF e comprimento não autorizam inferir um verificador. O formato visual sempre preserva o conteúdo, e não valida checksum.

Propriedade obrigatória: `normalizar(formatar(entrada)) == normalizar(entrada)`. A apresentação também é idempotente: formatar novamente não modifica o resultado.

## Contratos e histórico

A regra documental vale para novas gerações. Os snapshots novos guardam `DEVEDOR_RG` e `FIADOR_RG` com a apresentação usada no DOCX/PDF. Snapshots, DOCX, PDF, PDF assinado e hashes já persistidos não são recalculados nem modificados. O teste de histórico compara JSON e bytes dos três downloads após alterar nome/RG do cadastro e gerar outro contrato.

Dados fixos de Edson/Ivani, cláusulas, assinaturas, layout, cálculos e regras da promissória não foram modificados.

## Testes e build

- Reprodução anterior à correção: 38 cenários, 12 falhas de apresentação; `.auditoria/rg-completo/reproducao.log`.
- Primeira rodada focada: 208 testes, zero falhas, erros ou ignorados; `.auditoria/rg-completo/testes.log`.
- Rodada complementar: os 30 testes de `ClienteRgHttpTests` passaram, incluindo cinco novos cenários do perfil; `.auditoria/rg-completo/perfil-http-testes.log`. Resultado consolidado: **213 testes distintos aprovados**, zero falhas, erros ou ignorados.
- Foram adicionados **44 cenários JUnit** aos três grupos específicos de RG (46 + 30 + 12 = 88, contra os 44 anteriores), além de ampliar a verificação de RG no teste existente de histórico.
- Matriz `RgUtilTests`: 46 cenários, com comprimentos 7–13, formatos existentes, X, zeros, inválidos, UF e round-trip de todos os tamanhos.
- `ContratoRgTests`: 12 cenários, devedor/fiador, snapshot e DOCX.
- `ClienteRgHttpTests`: cadastro/consulta/edição, entrada bruta e pontuada, erros HTTP 400, geração real e três formatos de download, com zeros e fiador com X.
- Frontend: `node tools/rg/testar_rg.mjs`, 14 casos de entrada/reabertura/payload e 72 verificações aprovadas. É um teste dos schemas/payloads reais; não substitui um teste manual de colagem no navegador.
- `npm run lint`: aprovado, sem erros ou avisos.
- `npm run build`: aprovado; aviso de tamanho do bundle já existente, sem mudança de escopo para tratá-lo.
- `mvn -DskipTests package`: aprovado também após a rodada complementar; `.auditoria/rg-completo/package.log`.

Nenhum banco PostgreSQL de uso real foi alterado. Os testes backend usam H2. Não foi repetida a auditoria nem a suíte completa.
