# Correção documental — parcela única e valores

Data: 08/10/2026. Escopo: backend documental; nenhuma alteração no frontend ou nos cálculos financeiros.

## Causa e correção

O snapshot preenchia `VALOR_TOTAL`, usado na cláusula 1, com o total da dívida, embora o modelo oficial apresente ali o principal emprestado. O mesmo campo também alimentava a promissória. O template aplicava o texto mensal e o parágrafo de primeira/última parcela mesmo quando havia somente uma parcela.

Os cinco cenários de regressão falharam antes da correção. A evidência está em `.auditoria/parcela-unica/reproducao.log`.

| Conceito | Exemplo de parcela única | Uso |
| --- | --- | --- |
| `valorEmprestado` | R$ 2.700,00 | Principal na cláusula 1 |
| `valorParcela` | R$ 2.970,00 | Pagamento na cláusula 3 |
| `valorTotalDivida` | R$ 2.970,00 | Obrigação total |
| `quantidadeParcelas` | 1 | Seleção do texto singular |

`valorTotalEmprestimo` continua disponível para compatibilidade; `valorTotalDivida` é um alias explícito desse valor persistido. Novos snapshots registram os campos documentais de principal e dívida separadamente. Não há recálculo financeiro adicional.

Para uma parcela, a cláusula 3 apresenta `1 (uma) parcela` e `com vencimento fixo no dia 07 de novembro de 2026`, usando o vencimento da parcela persistida. O parágrafo redundante de primeira/última parcela é removido; a promissória passa a `§1º.` e `uma nota promissória`, seguindo o Word oficial. A formatação vermelha dos campos permanece.

Para 2, 3, 8 e 9 parcelas, o texto mensal, primeira/última data e `§2º.` permanecem. No exemplo com oito parcelas de R$ 362,00, a dívida é R$ 2.896,00, conforme a regra existente de soma das parcelas; o principal é R$ 2.160,00.

## Preservação

- DOCX de referência analisado: `C:/Users/caioe/Downloads/Contrato de empréstimo R$ 2.200,00 - 06 de julho de 2026.docx`.
- PDF problemático analisado: `C:/Users/caioe/Downloads/contrato-emprestimo-3611-14-assinado.pdf`.
- Edson, Ivani, assinaturas, demais cláusulas e layout preservados. Nenhum arquivo de template ou assinatura foi editado nesta tarefa; os seis hashes conferem com a captura inicial.
- Contratos antigos mantêm o JSON e os bytes persistidos de DOCX, PDF e PDF assinado, inclusive após alteração do cadastro e nova geração. Os testes verificam também os hashes dos downloads.
- A adaptação singular exige o novo campo de vencimento no snapshot. Snapshots legados continuam compatíveis.
- Ajuste posterior solicitado: o valor da promissória, no contrato e no PDF, usa o principal emprestado (R$ 2.700,00 neste exemplo), inclusive no valor por extenso. A dívida e as parcelas mantêm os juros. A regra existente de vencimento permanece: data de geração + um mês + cinco dias. Seu desenho não foi alterado.
- O teste de preservação interna do pacote DOCX passou a comparar com o template de execução atual. Esse arquivo já havia sido modificado antes desta tarefa; a comparação independente das cláusulas fixas com o modelo oficial permanece.

## Arquivos de código alterados nesta tarefa

1. `src/main/java/com/caio/controle_clientes/dto/contrato/EmprestimoContratoDTO.java`
2. `src/main/java/com/caio/controle_clientes/services/contrato/ContratoSnapshotService.java`
3. `src/main/java/com/caio/controle_clientes/services/contrato/ContratoTemplateService.java`
4. `src/test/java/com/caio/controle_clientes/ContratoValoresParcelasTests.java` — novo.
5. `src/test/java/com/caio/controle_clientes/ContratoParcelaUnicaIntegrationTests.java` — novo.
6. `src/test/java/com/caio/controle_clientes/ContratoGeracaoIntegrationTests.java`
7. `src/test/java/com/caio/controle_clientes/CredorDevedorFiadorHttpTests.java` — valida o novo alias no JSON e a preservação do campo antigo.

## Validação

Os 47 testes focados documentais passaram: valores e singular/plural, snapshot, geração e download, PDF, promissória, assinaturas, prévia e formatação. Logs em `.auditoria/parcela-unica/documentais.log`.

`mvn test`: sucesso, 320 testes contabilizados, zero falhas, zero erros e um teste específico de Linux ignorado no Windows. O teste HTTP foi atualizado para conferir os 13 campos, incluindo o novo alias da dívida, e manter a validação do campo legado. Log: `.auditoria/parcela-unica/mvn-test.log`.

`mvn -DskipTests package`: sucesso. JAR gerado em `target/controle_clientes-0.0.1-SNAPSHOT.jar`. Log: `.auditoria/parcela-unica/package.log`.

As três páginas do Word oficial, as três páginas do DOCX gerado e as quatro páginas do PDF assinado (incluindo promissória) foram renderizadas e inspecionadas visualmente. O PDF problemático também foi renderizado para confirmar o erro original.

Artefatos de verificação, relativos ao backend:

- `.auditoria/parcela-unica/gerado/contrato-2700-parcela-unica.docx`
- `.auditoria/parcela-unica/gerado/contrato-2700-parcela-unica.pdf`
- `.auditoria/parcela-unica/gerado/contrato-2700-parcela-unica-assinado.pdf`
- `.auditoria/parcela-unica/oficial-render/` — páginas do modelo oficial.
- `.auditoria/parcela-unica/gerado-render/` — páginas geradas e assinadas.

Os testes utilizaram banco H2. Nenhum dado do PostgreSQL de uso real foi alterado. Não houve mudança no frontend nem necessidade de build frontend.

## Ajuste posterior — principal na promissória

A nova solicitação substitui a regra anterior que usava a dívida total na promissória. A alteração está centralizada em `ContratoSnapshotService`: `VALOR_PROMISSORIA` e `VALOR_PROMISSORIA_EXTENSO` recebem o principal e seu extenso. O contrato e o PDF da promissória consomem os mesmos campos persistidos. Os testes de valores e de parcela única foram atualizados, incluindo o cenário legado com promissória contendo juros para verificar que downloads antigos permanecem intactos.

Conferência da solicitação complementar: os testes de 1, 2, 3, 8 e 9 parcelas agora também geram o PDF real da promissória e verificam o principal numérico e por extenso, rejeitando o total da dívida no campo monetário. Em oito parcelas de R$ 362,00, o PDF e o parágrafo contratual apresentam R$ 2.160,00, enquanto a dívida continua R$ 2.896,00. A cláusula 1 mantém o principal, conforme a correção anterior baseada no Word oficial; a referência ao total na nova solicitação é condicional e não exige modificar essa regra já aprovada.

Validação deste ajuste: 36 testes documentais passaram, sem falhas, erros ou testes ignorados. Cobertura: valores em 1/2/3/8/9 parcelas, contrato/DOCX, página da promissória PDF sem o valor com juros, snapshot legado, downloads históricos, hashes, promissória visual e assinaturas. `mvn -DskipTests package` também passou. Logs: `.auditoria/parcela-unica/promissoria-principal-testes.log` e `.auditoria/parcela-unica/promissoria-principal-package.log`. A suíte completa de 320 testes acima corresponde à etapa anterior; não foi repetida neste ajuste localizado.
