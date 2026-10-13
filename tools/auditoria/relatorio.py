"""Consolida evidências da auditoria; não executa consultas nem modifica o baseline."""
from pathlib import Path
import json, hashlib, re, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.auditoria/evidencias'
FRONT = ROOT.parent / 'frontend'
inv = json.loads((OUT/'inventario.json').read_text(encoding='utf-8'))

def link(path, label=None):
    p = Path(path)
    if not p.is_absolute(): p = ROOT / p
    return f'[{label or p.name}](<{p.as_posix()}>)'

def resultado(nome):
    p = OUT / nome
    if not p.exists(): return 'NÃO EXECUTADO / evidência ausente'
    s = p.read_text(encoding='utf-8', errors='replace')
    matches = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)', s)
    total = ', '.join(f'{v} {k}' for k,v in zip(['testes','falhas','erros','ignorados'],matches[-1])) if matches else ''
    status = 'APROVADO' if 'BUILD SUCCESS' in s else 'REPROVADO / incompleto'
    return f'{status}: {total}. {link(p)}'

bugs = [
('CRÍTICO','Pagamento parcial atrasado podia ser apagado junto com o empréstimo',
 'A exclusão considerava apenas os estados PAGO/PARCIAL; a atualização para ATRASADO escondia um valorPago positivo.',
 'Perda de recebimentos, parcelas e documentos associados.',
 'EmprestimoService.deletarEmprestimo verifica também valorPago > 0, antes de qualquer delete.',
 'AuditoriaRegressaoTests.pagamentoParcialAtrasadoImpedeExcluirEmprestimo; EmprestimoExclusaoContratosTests.'),
('CRÍTICO','Histórico financeiro ignorava pagamentos parciais e calculava saldo incorreto',
 'RelatorioService somava o valor integral apenas para status PAGO e o valor integral das demais como aberto.',
 'Total pago e total em aberto incorretos, especialmente após vencimento de parcela parcial.',
 'Somar o valor realmente pago e o saldo positivo de cada parcela; contagens derivadas de saldo e vencimento.',
 'AuditoriaRegressaoTests.historicoContaPagamentoParcialESaldoReal, com e sem atraso.'),
('MÉDIO','Desfazer pagamento deixava a data de pagamento antiga',
 'O fluxo zerava valorPago sem limpar dataPagamento.',
 'Parcela sem recebimento permanecia com uma data de pagamento inconsistente.',
 'ParcelaService.desfazerPagamentoParcela limpa a data na mesma transação.',
 'AuditoriaRegressaoTests.desfazerLimpaDataPagamentoERestauraFinanceiro.'),
('ALTO','Empréstimo com principal zero ou negativo era persistido',
 'Validação financeira de criação ausente no service.',
 'Empréstimos e parcelas sem base financeira válida.',
 'Validar principal, quantidade, juros, forma de pagamento e resultado positivo da parcela antes de persistir.',
 'AuditoriaRegressaoTests.naoPersisteEmprestimoComPrincipalNaoPositivo; umCentavoSemJurosEHabilitadoEParcelaZeroBloqueada.'),
('MÉDIO','Pagamento com valor null produzia erro obscuro',
 'Comparação de BigDecimal antes de validar a presença do valor.',
 'Mensagem técnica sem orientação útil.',
 'Validação explícita no service com mensagem de regra de negócio.',
 'AuditoriaRegressaoTests.pagamentoNuloTemErroDeNegocioClaro.'),
('ALTO','Rota inexistente e parâmetros inválidos eram classificados como 500',
 'Handler genérico capturava NoResourceFoundException e exceções de parâmetros.',
 'Falhas HTTP conhecidas pareciam defeitos internos; prejudicava diagnóstico.',
 'Handlers específicos para 404 e 400; corpo JSON inválido também possui resposta segura.',
 'AuditoriaRegressaoTests.rotaAusenteRetorna404ComCors/enumInvalidoRetorna400SemDetalheInterno; AuditoriaCorsHttpTests.'),
('ALTO','Resposta de violação de integridade expunha detalhes SQL',
 'GlobalExceptionHandler devolvia detalhes internos da exceção.',
 'Exposição de SQL e estrutura do banco ao consumidor.',
 'Remover details da resposta, preservar mensagem útil de exclusão e mensagem genérica para falha interna; não imprimir valores rejeitados de validação.',
 'AuditoriaRegressaoTests.erroDeIntegridadeNaoExpoeSqlAoConsumidor; AuditoriaCorsHttpTests para 409/500 e CORS.'),
('ALTO','Template Word de runtime não preservava integralmente a estrutura oficial',
 'O DOCX havia sido regravado e alterado partes OOXML e formatação do modelo.',
 'Regressão estrutural/visual do documento; teste de preservação falhava.',
 'Reexecutar tools/contratos/preparar_template.py a partir do original existente, preservando partes fixas. Nenhuma cláusula ou redação foi corrigida.',
 'ContratoTemplateTests, ContratoGeracaoIntegrationTests; renderização das três páginas DOCX e inspeção das imagens.'),
('CRÍTICO','A própria exportação não podia ser restaurada corretamente',
 'Export tinha 18 colunas; import esperava 19, incluindo TipoContrato, deslocando os campos.',
 'Backup aparentemente válido resultava em zero empréstimos restaurados.',
 'Alinhar as colunas e reconhecer explicitamente a exportação legada de 18 colunas, usando FISICO nesse formato.',
 'AuditoriaRegressaoTests.exportacaoPodeSerImportadaSemDeslocarColunas/importaExportacaoLegadaDeDezoitoColunas.'),
('CRÍTICO','Backup perdia distribuição de pagamentos parciais, datas e ajustes das parcelas',
 'A planilha possuía apenas resumo e número de parcelas pagas; import reconstruía as parcelas sem os pagamentos reais.',
 'Saldo restaurado divergente do recebimento real e perda de dados financeiros.',
 'Coluna Detalhes das parcelas com valores/datas individuais; validar completamente antes de criar dados e restaurar exatamente. Formato antigo com parcial indeterminável é rejeitado com motivo explícito, sem inventar distribuição.',
 'AuditoriaRegressaoTests.backupComPagamentoParcialMantemSaldoDasParcelas/backupLegadoSemDistribuicaoDoParcialNaoCriaFinanceiroInconsistente.'),
('ALTO','Importação mensal deslocava o dia depois de fevereiro',
 'Somava meses ao vencimento anterior, propagando o dia reduzido de fevereiro.',
 '31/01 → 28/02 → 28/03, em vez de recuperar 31/03.',
 'ImportacaoParcelaFactory calcula cada vencimento a partir da data inicial.',
 'AuditoriaRegressaoTests.importacaoMensalPreservaDiaBaseDepoisDeFevereiro; reprogramacaoMensalPreservaDiaBaseEFinanceiro em quatro datas-limite.'),
('CRÍTICO','Número decimal textual em planilha podia ser multiplicado por 100',
 'Reader removia todos os pontos, inclusive o separador decimal de 1234.56 e 0.01.',
 'Valor monetário importado incorreto.',
 'Distinguir vírgula decimal brasileira, ponto de agrupamento e ponto decimal; manter BigDecimal.',
 'ImportacaoNumericaAuditoriaTests, quatro casos; AuditoriaRegressaoTests.importacaoPreservaTaxaFracionariaETextoDecimal.'),
('CRÍTICO','Taxa fracionária de juros era arredondada para inteiro na importação',
 'Math.round na leitura da taxa.',
 'Taxa de 0,5% podia virar 1%, alterando o financiamento.',
 'Leitura decimal e interpretação do formato percentual da célula, sem arredondamento inteiro.',
 'AuditoriaRegressaoTests.importacaoPreservaTaxaFracionariaETextoDecimal.'),
('CRÍTICO','Pagamentos concorrentes perdiam recebimentos',
 'Duas transações liam o mesmo saldo antes de gravar, sem serializar mutações do empréstimo.',
 'Dois pagamentos de 700 eram aceitos para parcela de 1250,50 e um recebimento era sobrescrito.',
 'Lock pessimista do empréstimo, com escopo do Perfil, antes de pagamento, estorno, edição, exclusão, quitação e refinanciamento.',
 'AuditoriaConcorrenciaTests.pagamentosConcorrentesNaoPerdemRecebimentoNemUltrapassamSaldo, reproduzido e aprovado em PostgreSQL real.'),
('ALTO','CRUD de observações deixava coleção inversa inconsistente na transação',
 'Criar/remover observação não atualizava a coleção da parcela já gerenciada.',
 'Excluir o empréstimo nessa mesma transação podia falhar com referência transitória/FK.',
 'Sincronizar os dois lados da relação no service; manter cascade/orphanRemoval exclusivos já existentes.',
 'AuditoriaRegressaoTests.observacoesTemCrudIsolamentoEExclusaoJuntoComEmprestimo.'),
('ALTO','Configuração padrão do LibreOffice só funcionava em Windows',
 'application.properties fixava caminho C:/Program Files/... também para o container Linux.',
 'Conversão falhava no Linux, embora soffice estivesse no PATH.',
 'Padrão soffice, override CONTRATO_PDF_EXECUTAVEL e descoberta do caminho instalado no Windows.',
 'DocxPdfConversorTests; conversão real Windows sem override; inspeção da configuração Linux. Execução real Linux ainda pendente.'),
('ALTO','Relatório de inadimplentes dependia de outra página atualizar status',
 'Consulta excluía PENDENTE com vencimento passado.',
 'Cliente com dívida vencida desaparecia do relatório.',
 'Incluir PENDENTE na consulta por data de vencimento, junto de ATRASADO/PARCIAL.',
 'AuditoriaRegressaoTests.inadimplentesIncluiPendenteVencidaSemDependerDeOutraTela.'),
('MÉDIO','Download de contrato excluído retornava 400',
 'Busca documental sem resultado lançava a mesma exceção das regras de negócio.',
 'Quebrava o contrato HTTP 404 exigido para recursos removidos.',
 'DocumentoNaoEncontradoException e handler 404; somente buscas de documento são alteradas. Header ausente continua 400.',
 'AuditoriaRegressaoTests.downloadsDeContratoExcluidoRetornam404ComCors; ContratoExclusaoHttpTests; AssinaturasPdfIntegrationTests; navegador.'),
('CRÍTICO','Consulta de parcelas atrasadas sobrescrevia pagamento concorrente',
 'Consulta carregava entidades antes do pagamento e saveAll regravava as colunas financeiras antigas.',
 'O teste em PostgreSQL confirmou valorPago 1250,50 virar 0,00 e data/status inconsistentes.',
 'UPDATE atômico somente de status, com predicados de saldo e status; flush e limpeza do contexto para evitar entidades antigas.',
 'AuditoriaStatusConcorrenciaTests.consultaDeAtrasadasNaoSobrescrevePagamentoConcorrente; antes falha comprovada, depois aprovado em PostgreSQL e H2.'),
('MÉDIO','Arquivo que não era Excel retornava 500',
 'ImportController convertia todas as exceções em erro interno, inclusive formato inválido conhecido.',
 'Entrada inválida aparecia como indisponibilidade do servidor.',
 'Entrada incompatível retorna 400 claro; erros inesperados são propagados ao handler em vez de ocultados.',
 'AuditoriaRegressaoTests.arquivoQueNaoEExcelRetorna400ComCors; falhou com 500 antes da correção.'),
('ALTO','Página Backup anunciava sucesso com zero linhas importadas',
 'Frontend descartava ImportacaoResultado e exibía mensagem fixa para qualquer resposta 201.',
 'Usuário podia acreditar que um backup rejeitado havia sido restaurado.',
 'Mostrar quantidades importadas/ignoradas/duplicadas e até três motivos de rejeição; preservar mensagem de erro do backend.',
 'tools/auditoria/importacao-ui.mjs com arquivo real e API/PostgreSQL reais: 0 importadas, 1 ignorada, motivo exibido; build frontend.'),
('MÉDIO','Lint preexistente impede qualidade estática totalmente verde',
 'Seis erros de Fast Refresh/set-state-in-effect e três avisos já estavam no baseline.',
 'npm run lint termina com código 1; possíveis renderizações adicionais e dívida de manutenção.',
 'PENDENTE. Nenhuma regra desabilitada, nenhum diagnóstico ocultado. Correção estrutural desses hooks/componentes foi separada das falhas reproduzidas de negócio.',
 'frontend-lint-baseline.log e frontend-lint-final.log: mesmos 6 erros e 3 avisos; zero erro novo na página Backup.')
]

lines = ['# Resumo executivo','',
 'Auditoria de 07/10/2026. Foram mapeadas **55 interfaces funcionais: 46 endpoints e 9 rotas frontend**. Os 75 itens da solicitação foram revisados; a cobertura e suas limitações constam abaixo.',
 '**22 grupos de bugs encontrados: 21 corrigidos, 1 pendente (lint, com 6 erros e 3 avisos preexistentes). Oito grupos são críticos e foram corrigidos.** Agrupamentos e critérios de contagem são explícitos; não representam promessa de ausência de outros bugs.',
 'Nenhum teste destrutivo foi direcionado ao PostgreSQL real do usuário. Não foi utilizada a base controle_clientes_back. Nenhuma cláusula jurídica, dado fixo de Edson/Ivani ou assinatura original foi redesenhado.',
 '', '# Escopo','',
 'Inventário antes das mudanças, reprodução, correções pontuais, testes completos H2, bateria crítica em PostgreSQL real, builds, lint, navegador real, arquivos renderizados, restart, metadata, FKs, deletes, LOBs e privacidade.',
 'O inventário inicial e os hashes foram preservados; mudanças que já existiam no checkout antes desta auditoria não foram atribuídas a esta tarefa. Nenhum reset/clean de Git ou migração destrutiva foi feito.',
 '', '# Ambiente utilizado','',
 '| Componente | Ambiente isolado |','|---|---|',
 '| Backend | Java 17, Spring Boot 4.0.2, Maven 3.9.12, Windows |',
 '| PostgreSQL | 18.3 nativo, cluster `.auditoria/postgres-data`, loopback 127.0.0.1, porta 15432, usuário auditor |',
 '| Bancos | controle_clientes_auditoria para API/E2E/restart; controle_clientes_auditoria_regressao para testes create-drop |',
 '| API / frontend | API loopback 18082; Vite preview 5180; desenvolvimento StrictMode 5181, quando indicado no resultado E2E |',
 '| Navegador | Playwright + Microsoft Edge headless; chamadas encaminhadas à API real, sem simular respostas de negócio |',
 '| Documentos | LibreOffice real Windows; Poppler/PDFium, imagens renderizadas e comparação de pixels |',
 'A preferência por Docker/Testcontainers foi substituída por PostgreSQL nativo separado porque Docker Desktop não estava disponível de forma estável. O banco continua sendo PostgreSQL real, com isolamento de cluster/porta/base. Trust foi limitado ao ambiente local de auditoria; não é configuração para produção.',
 'A URL de teste foi sempre explicitamente sobrescrita. Usuário/senha de produção não foram usados. Servidores auxiliares foram iniciados ocultos e apenas os processos de auditoria são encerrados na limpeza.',
 '', '# Matriz de funcionalidades','',
 'Cada endpoint foi inspecionado. A coluna de testes identifica a família que cobre os cenários executados; não significa que todas as combinações possíveis de parâmetros tenham sido exercitadas. PATCH antigos de nome/endereço são apontados como inspeção quando não há evidência específica.',
 '| Funcionalidade | Endpoint | Service | Tabelas | Frontend | Testes / evidência | Risco |','|---|---|---|---|---|---|---|']

areas = {
 'BackupController':('perfil, clientes, emprestimos, parcelas','BackupPage','AuditoriaRegressaoTests; XLSX zero/um/vários; navegador','CRÍTICO'),
 'ImportController':('perfil, clientes, emprestimos, parcelas','BackupPage','AuditoriaRegressaoTests; ImportacaoNumericaAuditoriaTests; importacao-ui','CRÍTICO'),
 'ClientController':('clientes, perfil; emprestimos em DELETE','Clients / CadastroClienteDialog / EditClienteDialog','ClienteRgHttpTests; PadronizacaoCadastroHttpTests; CredorDevedorFiadorHttpTests; E2E','ALTO'),
 'ContratoController':('perfil, clientes, emprestimos, parcelas','DadosContratoDialog / Documentos','ContratoIntegrationTests; CredorDevedorFiadorIntegrationTests; E2E','CRÍTICO'),
 'ContratoGeradoController':('contratos_gerados, emprestimos, perfil, clientes','DadosContratoDialog / DocumentosHistorico','ContratoGeracaoIntegrationTests; ContratoExclusaoHttpTests; AssinaturasPdfIntegrationTests; E2E','CRÍTICO'),
 'EmprestimoController':('emprestimos, parcelas, clientes, perfil, contratos_gerados, parcela_observacoes','Emprestimo / NovoEmprestimoDialog / EditEmprestimoDialog','EmprestimoExclusaoContratosTests; AuditoriaRegressaoTests; AuditoriaConcorrenciaTests; E2E','CRÍTICO'),
 'ParcelaController':('parcelas, emprestimos, clientes, perfil','Parcelas / PagarParcelaDialog / AlterarParcelaDialog','AuditoriaRegressaoTests; AuditoriaStatusConcorrenciaTests; E2E','CRÍTICO'),
 'ParcelaObservacaoController':('parcela_observacoes, parcelas, perfil','ParcelaObservacoes','AuditoriaRegressaoTests.observacoesTemCrudIsolamentoEExclusaoJuntoComEmprestimo','ALTO'),
 'PerfilController':('perfil','Configuracoes / ProfileContext','CredorDevedorFiadorHttpTests; smoke: criar/listar/trocar/recarregar','CRÍTICO'),
 'RelatorioController':('clientes, emprestimos, parcelas, perfil','Home / RelatorioParcelas / histórico','AuditoriaRegressaoTests; CredorDevedorFiadorIntegrationTests; smoke HTTP','CRÍTICO'),
 'OpcoesDocumentoController':('nenhuma; enums','cadastro/edição cliente e fiador','PadronizacaoCadastroHttpTests; E2E','MÉDIO'),
 'HomeController':('nenhuma','API health / carregamento','startup e /ping real; raiz inspecionada','BAIXO')
}
for r in inv['endpoints']:
    tables,front,tests,risk=areas[r['controller']]
    if r['rota'].endswith('/name') or r['rota'].endswith('/endereco'): tests='Inspeção de escopo/implementação; teste HTTP específico não executado nesta auditoria'
    lines.append(f"| {r['controller']} | {r['metodo']} `{r['rota']}` | {', '.join(r['service']) or 'Resposta direta'} | {tables} | {front} | {tests} | {risk} |")
lines += ['', '## Rotas frontend','', '| Rota | Fluxo / evidência |','|---|---|']
for route,desc in [('/', 'Dashboard e gráficos'),('/clientes','Cadastro/edição/consulta'),('/emprestimo','Criação, fiador, dados/documentos e delete'),('/parcelas','Consulta/pagamento'),('/documentos','Geração, histórico, downloads e exclusão'),('/simulacao','Carregamento do simulador; fórmulas não comparadas exaustivamente ao service'),('/relatorio-parcelas','Relatório visual e chamadas reais'),('/backup','Export; importação rejeitada com contagens/motivo'),('/configuracoes','Seleção/troca/persistência de Perfil')]:
    lines.append(f'| `{route}` | {desc}; smoke em carteira vazia e populada quando consta em smoke/resultado.json |')

lines += ['', '# Bugs encontrados','', 'Cada número conta uma causa/grupo funcional, não cada assertion. BUG-022 reúne os seis erros de lint existentes. Credor/devedor iguais permitidos pelo backend são uma regra preexistente explícita em testes: ela foi preservada, não contabilizada como correção. O seletor frontend continua excluindo o devedor.']
for i,(priority,title,cause,impact,fix,test) in enumerate(bugs,1):
    lines += ['',f'## BUG-{i:03d} — {title}','',f'**Prioridade:** {priority}. **Estado:** '+('Pendente.' if i==22 else 'Corrigido.'),
              f'**Sintoma e causa raiz:** {cause}',f'**Impacto:** {impact}',f'**Correção:** {fix}',f'**Teste de regressão/evidência:** {test}']

lines += ['', '# Foreign Keys auditadas','',
 'Consulta executada em pg_constraint do PostgreSQL real: **10 FKs, todas ON DELETE NO ACTION**. A exclusão é controlada pelo service e pelas relações exclusivas do ORM; não houve adição de CascadeType.REMOVE para Cliente, Fiador ou Perfil.',
 '| Constraint | Tabela filha → pai | Comportamento definido |','|---|---|---|',
 '| fk49u7po31kjmlv7wd6tb9ittnv | clientes.perfil_id → perfil.id | Não existe DELETE público de Perfil |',
 '| fkm1hace7t3qn2x9skem6ta3xd5 | contratos_gerados.perfil_id → perfil.id | Documento não elimina Perfil |',
 '| fkss25ocki8gsdwvjg5d1ru5xp6 | contratos_gerados.emprestimo_id → emprestimos.id | Delete explícito dos contratos antes do empréstimo |',
 '| fk3m8mbjea6db14m6k4qtgxvtef | emprestimos.cliente_id → clientes.id | Cliente com empréstimo é bloqueado com mensagem de negócio |',
 '| fkfkj1tf49uuimyfa24aqnopwqj | emprestimos.fiador_id → clientes.id | Cliente utilizado como fiador é bloqueado |',
 '| fktndcwdjjq9fyiqlw3p6a5xhti | emprestimos.perfil_id → perfil.id | Delete do empréstimo preserva Perfil |',
 '| fkgi1knt5n9m344pbx10v5y9ceq | parcela_observacoes.perfil_id → perfil.id | Delete da observação preserva Perfil |',
 '| fktrl47g2begfotk37md8nb7s4a | parcela_observacoes.parcela_id → parcelas.id | Observações removidas antes da parcela via cascade exclusivo |',
 '| fkilncg1pracqhgvlkudokkb5pn | parcelas.emprestimo_id → emprestimos.id | Parcelas removidas antes do empréstimo |',
 '| fkioe35mphslpfs80xtwcvdju7v | parcelas.perfil_id → perfil.id | Delete da parcela preserva Perfil |',
 f'Evidência completa de tipos, constraints, índices e órfãos: {link(OUT/"postgres-metadata-final.txt")}. Foram identificadas 6 tabelas, 9 índices, checks de enums, UNIQUE perfil.nome e UNIQUE (emprestimo_id,numero_parcela).',
 '', '# Operações DELETE auditadas','',
 '| Entidade | Operação e ordem | Resultado / limite |','|---|---|---|',
 '| Cliente | Validar Perfil/cliente; bloquear empréstimos próprios ou referência como fiador; excluir somente cliente livre | Mensagem clara, sem SQL cru; testes de isolamento existentes |',
 '| Empréstimo | Validar Perfil/cliente/empréstimo; obter lock; bloquear qualquer valorPago positivo; apagar contratos; observações das parcelas; parcelas; empréstimo; commit | Testes sem documento, com documento/assinado, múltiplos documentos, rollback, outros empréstimos e entidades preservadas |',
 '| ContratoGerado | Buscar com Perfil+Cliente+Empréstimo+ID; apagar apenas linha selecionada e seus bytea/textos | Delete intermediário de três contratos; restantes intactos; downloads apagados 404 |',
 '| Observação | Buscar por Perfil e ID; atualizar coleção inversa; apagar observação | CRUD, isolamento e delete do pai na mesma transação |',
 '| Perfil / parcela isolada | Não há endpoint DELETE dessas entidades | Não aplicável; não foi inventada nova operação |',
 'Testes e metadata confirmam ausência de órfãos em contratos, parcelas, empréstimos sem cliente e observações. Os bytes documentais estão em BYTEA na própria contratos_gerados; pg_largeobject_metadata não possui objetos separados no ambiente de auditoria. Apagar a linha elimina DOCX/PDF/assinado/snapshots/hashes/metadados desse registro.',
 '', '# Clientes','',
 'Cadastro e edição completos por HTTP e navegador; leitura de legado incompleto é tolerada e geração documental exige regularização. CPF/CEP são String e preservam zeros. RG tem cobertura de 7–13 caracteres e X final; documentos usam o valor cadastrado/normalizado pelas regras já existentes. Sexo, órgão/UF e estado civil possuem testes específicos. O teste de navegador editou e reabriu nome, rua e profissão, verificando também CPF/RG; não editou isoladamente cada combinação de todos os campos.',
 '', '# Empréstimos','',
 'Criação/consulta/fiador, quatro frequências, PIX/DINHEIRO e FISICO/DIGITAL cobertos pelas suítes existentes e novas. Fiador obrigatório na criação atual, ausência legada tolerada na leitura e bloqueada na geração. Frontend: busca/ordem alfabética, devedor excluído, cadastro aninhado e scroll com wheel comprovados.',
 'Refinanciamento no projeto finaliza o empréstimo como REFINANCIADO; não cria automaticamente um empréstimo destino. Testou-se a regra implementada, sem introduzir uma relação origem/destino inexistente.',
 '', '# Parcelas','',
 'Número/ordem/valores/vencimentos/status auditados; reprogramação em 31/01, 29/02, 28/02 e 31/12, frequências diária/semanal/quinzenal/mensal. Atualização de atrasos mantém o comportamento existente de derivar status em GET, agora com UPDATE somente de status; o GET não apaga nem regrava dados de pagamento. Repetição é idempotente quanto ao resultado.',
 '', '# Pagamentos','',
 'Parcial, integral, completar saldo, excedente bloqueado, estorno, quitação e refinanciamento. BigDecimal preservado; R$0,01 aceito quando parcela positiva. Duas transações concorrentes de pagamento testadas no PostgreSQL; consulta de atrasos concorrente testada com sincronização determinística e leitura real JPA. Os locks não adicionam idempotency key à API: duas chamadas sequenciais válidas de geração ainda criam dois documentos, como esperado.',
 'O arredondamento existente usa valor uniforme de parcela. A política de eventual resíduo (ex.: 100/3) não foi redefinida para ajustar a última parcela sem decisão de negócio; deve ser revisada com casos de uso reais.',
 '', '# Contratos','',
 'Geração, prévia, validações de dados incompletos, três históricos, delete do intermediário, ordem e downloads independentes. Data contratual deriva de dataGeracao no instante de geração, convertida para America/Sao_Paulo e persistida no snapshot. Downloads retornam bytes persistidos e não recalculam data ou snapshot. Profissão totalmente minúscula, acentos, CPF/CEP formatados, RG preservado, números femininos 2/21 e dados fixos Edson/Ivani cobertos por testes documentais.',
 'Edson Antonio Vieira e Ivani Elias Machado Vieira, CPF 215.637.868-18, RG 30.623.886-X permanecem fixos. Templates de origem, assinatura e promissória oficiais foram verificados por hash; somente o DOCX de runtime foi reconstruído a partir do original para remover a regressão estrutural. Nenhuma alteração do cadastro é aplicada retroativamente aos bytes antigos.',
 '', '# Promissória','',
 'Emitente=devedor, avalista=fiador, credor fixo e valor/data/vencimento auditados. Regra existente dataContrato.plusMonths(1).plusDays(5) preservada. Comparação renderizada a 216 dpi: **zero pixels alterados fora dos campos variáveis** no caso oficial; última página do merge pixel a pixel idêntica à promissória gerada. Nomes/endereço longos e avalista vertical têm testes específicos; inspeção visual não foi substituída por texto extraído.',
 f'Imagens/arquivos: {link(OUT/"visuais/promissoria-comparacao-lado-a-lado.png")}, {link(OUT/"visuais/promissoria-overlay-diferencas.png")}, {link(OUT/"visuais/promissoria-original-render.png")}, {link(OUT/"visuais/promissoria-gerada-render.png")}, {link(OUT/"visuais/promissoria-teste.pdf")}, {link(OUT/"visuais/contrato-completo-teste.pdf")}.',
 '', '# PDFs','',
 'Conversão LibreOffice real em Windows, exit code/PDF não vazio, merge contrato → promissória, páginas sem vazios inesperados e verificações SHA-256 independentes. Três páginas DOCX renderizadas e inspecionadas, inclusive comparação de estrutura com o Word original. Foram produzidos exemplos reais pelos testes e pelo navegador.',
 f'{link(OUT/"e2e/contrato-completo.pdf")}; {link(OUT/"e2e/contrato.docx")}; páginas em {link(OUT/"docx-render")}.',
 '', '# Assinaturas','',
 'PDF normal sem imagens de assinatura; PDF assinado altera somente as regiões de credor/testemunha, mantendo devedor/fiador em branco. Casos nomes curtos, nomes longos e assinatura em quarta página foram renderizados/comparados; labels e promissória permanecem intactos. Assets privados sem endpoint público; GET /assinaturas/edson.png devolveu 404.',
 f'{link(OUT/"assinaturas/nomes-curtos-comparacao.png")}; {link(OUT/"e2e/contrato-assinado.pdf")}; {link(OUT/"assinaturas-visual.log")}.',
 '', '# Frontend','',
 'Única alteração de fonte nesta auditoria: BackupPage/index.tsx, para refletir o resultado real da importação. Nenhuma mudança estética ampla, arquitetura, seletor de fiador, cláusula ou snapshot. Componentes de geração/exclusão possuem disabled/locks durante loading; testes de duplo clique e StrictMode são identificados nas evidências, sem extrapolar para todas as telas.',
 f'Fluxos completos e requisições: {link(OUT/"e2e/resultado.json")}. Smoke das nove rotas e troca de Perfil: {link(OUT/"smoke/resultado.json")}. Importação rejeitada: {link(OUT/"importacao-ui/resultado.json")}.',
 'Fallbacks de contrato/download/exclusão de empréstimo preservam mensagens úteis. apiClient tem fallback genérico para outros endpoints; padronização completa de todas as mensagens e acessibilidade de todos os modais ainda exige revisão manual.',
 '', '# CORS','',
 'Preflight GET/POST/PUT/PATCH/DELETE com X-Perfil-Id, Origin localhost:5173 e cabeçalhos de erro 400/404/409/500 executados por HTTP/MockMvc. Navegador em localhost:5180/5181 encaminha à API real 18082 para isolamento; não foi tomada a porta 8080 do usuário. A configuração foi testada com Origin solicitado e servidor em porta alternativa. Origem 127.0.0.1 não é automaticamente equivalente a localhost; o allowlist foi preservado.',
 '', '# Exception handling','',
 '404 recurso/documento inexistente, 400 entrada/parâmetro/formato conhecidos, 409 integridade e 500 inesperado com mensagem segura e CORS. O teste de 500 é um endpoint presente somente no contexto de teste, nunca publicado na API de produção. Regras legadas de recurso fora do Perfil em cliente/empréstimo podem continuar usando 400; não foram convertidas indiscriminadamente.',
 'SQL não é devolvido ao usuário. Stacktraces continuam possíveis no log do servidor para falhas técnicas; políticas de retenção/acesso e sanitização abrangente de todos os erros de banco ainda precisam validação operacional. Nenhum conteúdo PDF/bytea foi impresso nas operações documentais executadas.',
 '', '# Dados legados','',
 'Leitura/cadastro incompleto, RG antigos, formatação UF/órgão, contrato histórico sem PDF/assinado e backup antigo de 18 colunas possuem testes. Backup antigo com pagamento parcial sem distribuição individual é rejeitado antes de criar dados. Deve-se reexportar da base de origem quando possível; não existe informação suficiente para recuperar a distribuição a partir do resumo antigo.',
 'XLSX de backup é uma exportação financeira, não uma cópia integral do PostgreSQL: não preserva todos os campos pessoais/fiador, documentos/snapshots/observações. Não deve ser usado como única estratégia de recuperação integral.',
 '', '# PostgreSQL','',
 resultado('postgres-final-completo.log'),
 'A bateria de 121 testes abrangeu dez classes: contratos, assinaturas, geração, merge/promissória, escopo, transações, delete, bytea, metadados e duas regressões de concorrência. O complemento de 40 execuções validou a correção final da importação e repetiu regressões/CORS. São 161 execuções PostgreSQL com sobreposição, não 161 testes distintos; 122 casos distintos foram executados entre as duas rodadas.',
 f'Complemento: {resultado("postgres-importacao-final.log")}. Restart com hashes: {link(OUT/"smoke/resultado.json")}.',
 '', '# Resultados dos testes','',
 '| Execução | Resultado |','|---|---|',
 f'| Baseline H2 antes das correções | {resultado("h2-baseline.log")} |',
 f'| mvn test final | {resultado("mvn-test-final.log")} |',
 f'| PostgreSQL crítico final | {resultado("postgres-final-completo.log")} |',
 f'| mvn clean package | {resultado("mvn-clean-package.log")} |',
 'O baseline possuía 256 testes, 10 falhas e 1 teste ignorado. Uma falha era a estrutura DOCX e nove eram fixtures antigas com RG inválido para a regra 7–13 já vigente. Fixtures foram atualizadas para RG válido; validação não foi removida. Expectativa de metadados passou a considerar pdfAssinadoDisponivel já existente. Expectations HTTP documentais foram ajustadas ao 404 exigido, mantendo todas as verificações de isolamento e preservação.',
 'O teste ignorado é exclusivo de Linux: DocxPdfConversorTests.timeoutMataProcessoELimpaTemporarios. Não foi contado como executado no Windows. Foram acrescentadas 47 execuções de teste em relação ao baseline: regressões de negócio, HTTP/CORS, números importados, concorrência e configuração.',
 'Evidências anteriores às correções: regressoes-antes.log, backup-parcial-antes.log, backup-legado-parcial-antes.log, concorrencia-antes.log, status-concorrencia-antes.log, moeda-textual-antes.log, importacao-numerica-antes.log, inadimplentes-antes.log, configuracao-antes.log, contrato-excluido-status-antes.log e importacao-http-antes.log. Tentativas intermediárias que falharam não constituem aprovação; logs finais e artefatos aprovados são os referenciados nesta seção.',
 '', '# Build backend','',resultado('mvn-clean-package.log'),
 'Package executa novamente toda a suíte, sem skipTests. O jar final é usado para reinício e E2E; target é descartável e as evidências ficaram em .auditoria/evidencias.',
 '', '# Build frontend','',
 f'**APROVADO: npm run build (TypeScript + Vite), código de saída 0.** {link(OUT/"frontend-build.log")}. Bundle principal 999,67 kB (300,20 kB gzip); desempenho em grandes carteiras ainda precisa medição.',
 '', '# Lint','',
 f'**REPROVADO: 6 erros e 3 avisos preexistentes; zero novos.** {link(OUT/"frontend-lint-baseline.log")}; {link(OUT/"frontend-lint-final.log")}.',
 '| Arquivo | Diagnóstico existente |','|---|---|',
 '| components/ui/badge.tsx:35 | Fast Refresh: exportação não componente |',
 '| contexts/ProfileContext.tsx:166 | Fast Refresh: exportação não componente |',
 '| hooks/useParcelasPage.ts:44,85 | Dois set-state-in-effect |',
 '| pages/Emprestimo/components/EditEmprestimoDialog.tsx:73 | set-state-in-effect |',
 '| pages/Home/components/FinancialChart.tsx:76 | set-state-in-effect |',
 '| AlterarParcelaDialog:94,98; Emprestimo/index:43 | Três avisos de deps/disable não utilizado |',
 '', '# Pendências','',
 '- BUG-022: resolver os seis erros e três avisos do lint sem desabilitar regras nem mudar as regras de negócio.',
 '- Executar build/container e conversor real em Linux; teste de timeout Linux está ignorado neste Windows.',
 '- Validar manualmente edição de todos os campos legados em todas as combinações; mobile, foco/teclado, leitores de tela e falhas de rede durante cada modal.',
 '- Medir N+1/volume e definir limites operacionais; auditoria não realizou benchmark de carga.',
 '- Definir backup integral do banco/documentos, gestão de credenciais e acesso/retensão de logs antes de produção.',
 '', '# Riscos restantes','',
 'Nenhum sistema foi certificado como livre de bugs. Isolamento por X-Perfil-Id foi testado como regra de escopo, mas header selecionável não equivale a autenticação/autorização de usuários. Não foi implantado um sistema de login novo.',
 'Listagens de contratos utilizam projeção de metadados e não retornam PDF/DOCX/snapshot. Isso foi verificado no código e em testes de payload. Home e relatórios fazem múltiplas consultas/listagens e não possuem paginação universal; N+1 sob volume grande não foi medido. Falta de índices individuais adicionais para consultas de grandes carteiras é uma hipótese de desempenho, não falha comprovada por benchmark.',
 'Datas contratuais/históricas e saldos têm testes; alterações simultâneas de cadastro durante uma geração não receberam teste específico de carga. Duplo clique foi avaliado em fluxos indicados, sem idempotência global de todas as operações.',
 'GET de parcelas atualiza status derivado; agora preserva o dinheiro, mas permanece uma escrita interna. Uma futura separação de projeção e atualização exigiria decisão arquitetural fora desta correção.',
 'Na importação, números percentuais menores que 1 sem formato explícito preservam a convenção legada de fração; uma célula numérica 0,5 pode representar 50%, enquanto texto 0,5 representa 0,5%. Use células percentuais explícitas e confira a taxa ao importar planilhas externas. Não foi redefinida essa convenção de arquivos legados.',
 'O handler legado ainda classifica RuntimeException genérica como 400 e usa sua mensagem, pois os services a utilizam para regras de negócio antigas. Não se certifica que todo erro de programação inesperado seja convertido em 500 seguro; separar essas classes exige revisão e reprodução específicas. Os 500 e erros conhecidos comprovados nesta auditoria estão identificados nos testes.',
 'application.properties mantém defaults locais de conexão; credenciais devem vir de configuração segura de ambiente. O relatório omite seus valores. Não houve alteração na base de produção ou deployment.',
 '', '# Checklist pré-produção','',
 'Checklist manual para o usuário; caixas abertas não são falhas automáticas nem verificações fictícias:',
 '- [ ] API final inicia com a URL/Perfil corretos e GET /ping responde.',
 '- [ ] Frontend final inicia e utiliza a API configurada.',
 '- [ ] PostgreSQL de uso está correto; backup integral recente foi verificado.',
 '- [ ] LibreOffice instalado e descoberto em Windows; validar também no Linux de destino.',
 '- [ ] Templates e assets de assinatura estão no jar.',
 '- [ ] Cliente abre; edição salva/reabre todos os campos reais.',
 '- [ ] Seletor de fiador busca, rola e preserva os campos do empréstimo.',
 '- [ ] Empréstimo cria; pagamento, saldo e estorno conferem.',
 '- [ ] Contrato gera; data é a de geração; texto jurídico e nomes fixos conferem.',
 '- [ ] DOCX/PDF/assinado baixam; histórico antigo permanece idêntico.',
 '- [ ] Promissória tem mesmo formulário; assinaturas só em Edson/Ivani.',
 '- [ ] Delete de contrato e de empréstimo não apaga Cliente/Fiador.',
 '- [ ] Importação mostra quantidade e motivos; comparar o saldo restaurado.',
 '- [ ] Concluir correções de lint e validar ambiente Linux antes de declarar todos os checks verdes.',
 '', '## Health / preflight','',
 'GET /ping já existente foi reutilizado como verificação de API UP, sem adicionar Actuator. Ele não é diagnóstico de saúde do banco/documentos. Inicialização sem banco falhou com erro de conexão explícito; sem LibreOffice/template/assinatura a API subiu, mas geração falhou com mensagem controlada e sem histórico parcial. O preflight global com estado degradado não foi adicionado: validação continua no fluxo de geração. Evidência: '+link(OUT/'startup-resultados.json'),
 '', '# Cobertura dos 75 itens solicitados','',
 'Legenda: **T** = teste executado/evidência; **I** = inspeção; **M** = depende de validação manual ou ambiente não executado; **N/A** = recurso inexistente. T não certifica todas as combinações possíveis.',
 '| Item | Cobertura | Evidência / limite |','|---|---|---|']

coverage = {
1:('I','Inventário anterior às alterações, 46 endpoints, 9 rotas e hashes'),2:('I','Correções pontuais após reprodução; arquitetura preservada'),3:('T','PostgreSQL nativo isolado 15432; dois bancos de auditoria'),4:('T','121 testes críticos PostgreSQL e complemento HTTP'),5:('T','Startup disponível/indisponível e quatro falhas documentais controladas'),6:('T/I/M','Windows real; Linux padrão PATH inspecionado, execução Linux pendente'),7:('T','CORS Origin localhost:5173; servidor de teste em porta alternativa'),8:('T','HTTP de escopo + UI seleção/troca/reload'),9:('T','Cliente completo HTTP/E2E'),10:('T/M','HTTP completo; UI editou subconjunto e reabriu; matriz de todos os campos manual'),11:('T/M','Legado incompleto no backend; todas as combinações de modal legado não executadas'),12:('T','RgUtilTests e ClienteRgHttpTests: 7–13 e X'),13:('T','CPF/CEP String, máscaras e zeros em testes e UI'),14:('T','Fiador busca/ordem/exclusão devedor/cadastro/scroll, testes de troca'),15:('T','Quatro frequências, formas/tipos, principal/juros/fiador'),16:('T','API real + E2E'),17:('T','EmprestimoExclusaoContratosTests + PG + UI'),18:('T','Cliente/Fiador/Perfil e empréstimos alheios preservados'),19:('T','pg_constraint + testes delete documentos/parcelas'),20:('T','Duas FKs para clientes + bloqueios de negócio'),21:('T','Parcelas/ordem/status/datas-limite'),22:('T','Pagamento parcial/integral/saldo e PG'),23:('T','Estorno limpa data e financeiro'),24:('T','Parcial/completar/excedente bloqueado'),25:('T','Quitação financeira'),26:('T','Reprogramação em quatro extremos'),27:('T/N/A','Finalização REFINANCIADO testada; empréstimo destino não existe nessa função'),28:('T','CRUD observações, escopo e delete do pai'),29:('T/M','HTTP real e página; filtros exaustivos/datas do gráfico não executados'),30:('T','Import/export round-trip, legado, duplicados e rejeição'),31:('T','XLSX real zero/um/três e abertura por leitor'),32:('T','Dados completos/incompletos, sem fiador e credor fixo'),33:('T','DOCX e suíte formatter/template; três páginas renderizadas'),34:('T','Alterações cliente/fiador posteriores; snapshot/bytes iguais'),35:('T','Três contratos, ordem e downloads independentes'),36:('T','Delete intermediário, preservação e downloads 404'),37:('T/M','LO Windows real e indisponibilidade; Linux não executado'),38:('T','PromissoriaPdfIntegrationTests e datas'),39:('T','Imagens comparadas: zero pixels fixos alterados'),40:('T','Merge e última página pixel-idêntica'),41:('T','Assinaturas normais/longas/quarta página, regiões exclusivas'),42:('T','Bytes históricos após edição/pagamento/restart'),43:('T','SHA-256 independente dos três formatos'),44:('T','E2E página Documentos completo'),45:('T','E2E modal completo e sincronização'),46:('T','E2E fiador/submit/scroll/cadastro aninhado'),47:('I/T/M','apiClient inspecionado; erros documentais e import testados; matriz inteira de rede pendente'),48:('T/I','200/201/204/400/404/409/500; regras legadas 400 preservadas'),49:('T/I','Respostas sem SQL; logs técnicos sob gestão operacional'),50:('T','Todas 10 FKs extraídas e deletes definidos'),51:('T','SQL de órfãos e pg_largeobject_metadata'),52:('T/I','@Transactional; rollback de geração/delete e concorrência'),53:('T','Navegador duplo clique geração/pagar/delete; corrida financeira PG'),54:('T/I','E2E em desenvolvimento StrictMode quando log PASS; loading/locks e GET status atômico'),55:('I/T','Busca de null-unsafe e suíte de campos legados; pagamento null corrigido'),56:('T','Formatter/cadastro/documentos, português/acento/sexo/UF'),57:('T/M','BigDecimal, centavos, saldo; política de resíduo por parcela não redefinida'),58:('T','31/01, 28/02, 29/02, 31/12 e datas documentais'),59:('T','Restart preserva cliente/empréstimo/contratos e hashes'),60:('T','Somente bancos de auditoria/15432, nenhuma base real'),61:('T','Regressões antes/depois preservadas'),62:('I/T','Sem remoção de validações/tests; fixtures ajustadas a regras vigentes'),63:('T','mvn test e mvn clean package finais'),64:('T','Build OK; lint reprova 6 antigos/3 avisos, zero novos'),65:('T','14 verificações E2E principais e smoke/backup adicionais'),66:('I','Checklist manual presente neste relatório'),67:('T/I','/ping reutilizado; não diagnostica banco/templates'),68:('T/I','Falha controlada na geração; health documental startup não implementado'),69:('I','Este relatório com inventário, bugs, matriz e evidências'),70:('I','Cobertura e pendências explicitadas; nenhuma promessa zero bugs'),71:('I','22 grupos classificados, oito críticos'),72:('I/M','Payload sem blobs; bundle pesado e N+1/carga não medidos'),73:('T/I/M','Asset 404, payload sem snapshot/blob e logs inspecionados; retenção/sanitização global manual'),74:('T/I/M','Entregáveis concluídos com pendências declaradas'),75:('I','Resposta final com números e evidências')}
for i in range(1,76):
    status,desc=coverage[i];lines.append(f'| {i} | {status} | {desc} |')

lines += ['', '# Inventário completo anterior às correções','',
 'Listagem capturada antes de qualquer alteração da auditoria. Novo handler documental e novas classes de teste constam nos bugs/resultados; a contagem inicial não foi regravada.']
for section,files in inv['backend'].items():
    lines+=['',f'## Backend — {section} ({len(files)})','']+[f'- {link(p)}' for p in files]
for section,files in inv['frontend'].items():
    lines+=['',f'## Frontend — {section} ({len(files)})','']+[f'- {link(FRONT/p)}' for p in files]
lines += ['', '## Classes de testes existentes no baseline','']+['- '+t for t in inv['testes']]

baseline=json.loads((OUT/'fontes-baseline-sha256.json').read_text(encoding='utf-8'))
changed=[]
for name,sha in baseline.items():
    p=Path(name)
    if not p.exists() or hashlib.sha256(p.read_bytes()).hexdigest()!=sha:changed.append(name)
new_tests=[p for p in (ROOT/'src/test/java').rglob('*Tests.java') if p.stem not in inv['testes']]
lines+=['','# Arquivos alterados nesta auditoria','',
        'Comparação SHA-256 com baseline desta tarefa, sem confundir alterações prévias do Git.']
lines += ['- '+link(p) for p in changed]
lines += ['','Novas classes de teste:','']+['- '+link(p) for p in new_tests]
sources=ROOT/'src/main/resources'
fixed=[p for p in sources.rglob('*') if p.is_file() and ('source' in p.parts or 'assinaturas' in p.parts)]
unchanged=[p for p in fixed if str(p) in baseline and hashlib.sha256(p.read_bytes()).hexdigest()==baseline[str(p)]]
lines += ['',f'Fontes oficiais/assets preservados por SHA-256: {len(unchanged)} de {len(fixed)} arquivos selecionados.']
lines += ['- '+link(p) for p in unchanged]
e2e=OUT/'e2e/resultado.json';smoke=OUT/'smoke/resultado.json'
for title,p in [('E2E principal — build de produção',e2e),('E2E desenvolvimento — StrictMode',OUT/'e2e-strictmode/resultado.json'),('Smoke/restart',smoke)]:
    lines+=['',f'## {title}','']
    if p.exists():
        r=json.loads(p.read_text(encoding='utf-8'));lines+=['Estado: '+r.get('status','desconhecido')]+['- '+x for x in r.get('checks',[])]
    else:lines+=['NÃO EXECUTADO / aguardando evidência.']
summary={'interfacesMapeadas':55,'endpoints':46,'rotasFrontend':9,'bugs':22,'criticosCorrigidos':8,'corrigidos':21,'pendentes':1,'lintErrosAntigos':6,'lintAvisosAntigos':3,'lintErrosNovos':0,'arquivosBaselineAlterados':changed}
for name in ['mvn-test-final.log','mvn-clean-package.log','postgres-final-completo.log','postgres-importacao-final.log']:
    data=(OUT/name).read_text(encoding='utf-8',errors='replace')
    counts=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',data)
    summary[name]={'status':'PASS' if 'BUILD SUCCESS' in data else 'FAIL','total':int(counts[-1][0]),'falhas':int(counts[-1][1]),'erros':int(counts[-1][2]),'ignorados':int(counts[-1][3])}
lines += ['','## Evidências operacionais finais','',
          '- Arquivos XLSX abertos e conferidos: '+link(OUT/'smoke/xlsx-abertura.json'),
          '- Busca de marcadores CPF/RG/PDF nos logs executados: '+link(OUT/'privacidade-logs.json')+'; resultado limitado aos marcadores sintéticos exercitados, não uma prova global sobre todos os erros possíveis.',
          '- Servidores temporários: '+link(OUT/'encerramento.json')+'. Cluster/data/evidências preservados; nenhum serviço do usuário encerrado.']
(OUT/'resumo-auditoria.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
(ROOT/'AUDITORIA_COMPLETA_SISTEMA.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
