import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path.cwd()
out = root / '.auditoria/fechamento'
def read(name):
    return json.loads((out / name).read_text(encoding='utf-8-sig'))
def evidence(name, title=None):
    return f'[{title or name}](<{(out / name).as_posix()}>)'
def totals(name):
    rows = read(name)
    return {k: sum(int(r[k]) for r in rows) for k in ('tests', 'failures', 'errors', 'skipped')}

pg_classes = set('AssinaturasPdfIntegrationTests,AuditoriaConcorrenciaTests,AuditoriaCorsHttpTests,AuditoriaRegressaoTests,AuditoriaStatusConcorrenciaTests,ContratoGeracaoIntegrationTests,ContratoIntegrationTests,CredorDevedorFiadorIntegrationTests,EmprestimoExclusaoContratosTests,PromissoriaPdfIntegrationTests,ImportacaoNumericaAuditoriaTests,HealthTests,FechamentoCaracterizacaoTests'.split(','))
rows = []
for p in (root / 'target/surefire-reports').glob('TEST-*.xml'):
    r = ET.parse(p).getroot()
    if r.get('name').rsplit('.', 1)[-1] in pg_classes:
        rows.append({k: r.get(k) for k in ('name', 'tests', 'failures', 'errors', 'skipped', 'time')})
assert len(rows) == len(pg_classes)
(out / 'postgresql-final.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
test, package, pg = (totals(n) for n in ('mvn-test-final.json', 'mvn-package-final.json', 'postgresql-final.json'))
assert test == package == dict(tests=313, failures=0, errors=0, skipped=1)
assert pg == dict(tests=135, failures=0, errors=0, skipped=0)
for name in ('mvn-test-final.log', 'mvn-package-final.log', 'postgresql-final.log'):
    assert 'BUILD SUCCESS' in (out / name).read_text(encoding='utf-8')
assert 'error' not in (out / 'npm-lint-final.log').read_text(encoding='utf-8').lower()
assert 'built in' in (out / 'npm-build-final.log').read_text(encoding='utf-8')
ui, net, e2e, smoke, backup, restore, health = (read(n) for n in (
    'frontend/resultado.json', 'rede/resultado.json', 'e2e-final/resultado.json',
    'smoke-final/resultado.json', 'backup-ui/resultado.json', 'restore/resultado.json', 'health/resultado.json'))
for r in (ui, net, e2e, smoke, backup, restore, health):
    assert r['status'] == 'PASS'
assert restore['fixture']['scope'] == ui['scope'], 'Restore final ainda não concluído.'
assert read('health/banco-indisponivel.json')['http'] == 503
assert read('health/banco-recuperado.json')['status'] == 'UP'
inventory = read('alteracoes-final.json')
assert inventory['protegidosInalterados']
volume = read('volume-final-pg.json')
before, after = read('volume-antes-pg.json'), read('volume-depois-pg.json')
client_before, client_after = read('volume-ui-antes.json'), read('volume-ui-depois.json')
home_before, home_after = read('volume-home-antes.json'), read('volume-home-depois.json')
assert home_before['text'] == home_after['text'], 'Totais/texto da Home diferiram.'
sql_table = '\n'.join(f"| `{m['endpoint']}` | {m['queries']} | {m['ms']} | {m['bytes']} |" for m in volume['measurements'])
counts = ', '.join(f'**{v} {k}**' for k, v in restore['counts'].items())
matrix = [
    ('Criar Perfil', 'backup-ui/resultado.json'), ('Trocar Perfil', 'backup-ui/resultado.json'),
    ('Criar Cliente', 'e2e-final/resultado.json'), ('Editar os 17 campos', 'frontend/resultado.json'),
    ('Criar Fiador', 'e2e-final/resultado.json'), ('Criar Empréstimo', 'e2e-final/resultado.json'),
    ('Buscar fiador / teclado / scroll', 'frontend/resultado.json'),
    ('Pagar parcial', 'frontend/resultado.json'), ('Completar pagamento e estornar', 'frontend/resultado.json'),
    ('Gerar contrato', 'e2e-final/resultado.json'), ('Baixar DOCX', 'e2e-final/resultado.json'),
    ('Baixar PDF', 'e2e-final/resultado.json'), ('Baixar PDF assinado', 'e2e-final/resultado.json'),
    ('Excluir contrato', 'e2e-final/resultado.json'), ('Gerar novo contrato', 'e2e-final/resultado.json'),
    ('Excluir empréstimo sem pagamento; preservar Cliente/Fiador', 'e2e-final/resultado.json'),
    ('Exportar', 'backup-ui/resultado.json'), ('Importar cenário válido', 'backup-ui/resultado.json'),
    ('Abrir relatórios', 'smoke-final/resultado.json'), ('Reiniciar API / persistência / hashes', 'smoke-final/resultado.json')]
(out / 'e2e-matriz-final.json').write_text(json.dumps([
    dict(etapa=i, fluxo=n, estado='APROVADO', evidencia=e) for i, (n, e) in enumerate(matrix, 1)
], ensure_ascii=False, indent=2), encoding='utf-8')
matrix_md = '\n'.join(f'| {i} | {n} | APROVADO | {evidence(e)} |' for i, (n, e) in enumerate(matrix, 1))
changed_md = '\n'.join(f'- `{Path(n).as_posix()}`' for n in inventory['alterados'])
added_md = '\n'.join(f'- `{Path(n).as_posix()}`' for n in inventory['novos'])
report = f'''# Resumo

Fechamento técnico de 07/10/2026, no Windows, com PostgreSQL real em cluster exclusivo da auditoria (`127.0.0.1:15432`). Não houve deployment nem alteração da base de uso `controle_clientes_back` na porta 5432.

**APROVADO nos fluxos cobertos:** lint 0 erros/0 avisos, build frontend, `mvn test`, `mvn clean package`, bateria PostgreSQL, regressões frontend, backup/restore real e health. **Linux/LibreOffice: NÃO EXECUTADO**, pois Docker Engine está indisponível e não há distribuição WSL em execução. Isso continua sendo uma pendência de validação do ambiente de destino, não uma execução aprovada fictícia.

**Os fluxos cobertos foram testados e aprovados; isso não significa ausência absoluta de bugs futuros.** A aprovação é limitada aos cenários e ambientes descritos. Este relatório não autoriza implantação nem introduz autenticação nova.

# Pendências anteriores

| Pendência da auditoria | Fechamento |
|---|---|
| BUG-022: 6 erros e 3 avisos ESLint | CORRIGIDO, saída 0, nenhuma regra desabilitada |
| Legados / edição / foco / mobile / falhas de rede | APROVADO nos cenários abaixo |
| Health global | IMPLEMENTADO e testado com dependências faltantes e banco desligado |
| Backup integral / restore | APROVADO em banco novo com API restaurada e hashes iguais |
| Volume / N+1 | MEDIDO: 500 clientes, 1.000 empréstimos, 6.000 parcelas por Perfil |
| RuntimeException / senha fixa / logs | Corrigidos de forma localizada e revalidados |
| Linux real e timeout de conversão | NÃO EXECUTADO; ambiente indisponível |

O relatório anterior foi atualizado sem apagar os resultados históricos da primeira auditoria. O baseline desta rodada está em {evidence('auditoria-anterior.md')} e {evidence('baseline-sha256.json')}.

# Lint corrigido

**Antes: 6 erros/3 avisos, código 1. Depois: 0 erros/0 avisos, código 0.** {evidence('npm-lint-final.log')}. Configuração ESLint não foi afrouxada e diagnósticos não foram ocultados.

Os seis erros eram dois exports incompatíveis com Fast Refresh (Badge e ProfileContext), dois efeitos com setState em useParcelasPage, um em EditEmprestimoDialog e um em FinancialChart. Variants, estado/contexto e hook foram separados de componentes; imports públicos de Perfil continuam disponíveis no barrel. Parcelas usa página derivada e remonta por Perfil; modal de edição inicializa ao abrir; gráfico mede no ref e observa resize.

Os três avisos eram dependências de carregarObservacoes, disable não utilizado e dependência instável do efeito duplicado de hidratação da página Empréstimo. O callback agora é estável, o disable foi removido e a hidratação continua no hook que já a executava. Não se mudou cálculo financeiro para satisfazer lint.

# Alterações frontend

Erros de API usam mensagem de negócio segura quando disponível e fallback por operação. HTTP não estruturado/SQL/Hibernate/stacktrace não aparece ao usuário. Fetch é abortado após 30 s (150 s para geração contratual); o teste acelerou somente o relógio para 120 ms, exercitando AbortController real com request pendente. Esse limite cobre obtenção dos headers; streaming tardio do corpo não recebeu cenário próprio.

Clientes, Empréstimo e Parcelas mostram falha de listagem em vez de parecer carteira vazia. Confirmação de delete e edição de empréstimo capturam rejeição e exibem erro. Exportação XLSX usa o mesmo cliente HTTP e mantém validação de MIME. Cabeçalhos responsivos e labels foram ajustados onde houve falha reproduzida.

O N+1 na interface foi removido com contagem agrupada por cliente e leitura da carteira por Perfil. Não houve paginação geral nova, CascadeType.REMOVE em Cliente/Fiador/Perfil, mudança de arquitetura nem alteração das regras de pagamento.

# Clientes legados

**APROVADO:** cliente de teste com nulls nos dados pessoais novos, endereço parcial e RG antigo de oito dígitos abriu sem HTTP 500. A tela recebeu edição dos **17 campos editáveis**, salvou, fechou e reabriu; os valores exibidos foram comparados ao DTO persistido. CPF e CEP com zeros à esquerda, sexo, estado civil, nacionalidade OUTRA, órgão emissor e UF fazem parte da fixture. RG legado existente não foi reinventado por estes ajustes.

Nome, indicador, sexo, estado civil, nacionalidade e sua descrição, profissão, CPF, RG, órgão/UF emissora, CEP, rua, número, complemento, bairro, cidade e UF foram exercitados. O cliente e fiador são fixtures do banco isolado, não cadastros reais do usuário. {evidence('frontend/resultado.json')}.

# Mobile

**APROVADO:** 375×812, 390×844 e 768×1024. Clientes/edição, Novo empréstimo/fiador, Documentos e Parcelas foram abertos; bounds dos dialogs/lista, altura/posição, scroll via wheel com 35 opções, busca e seleção foram verificados. O campo de busca de Parcelas passou a caber no viewport; tabelas largas mantêm scroll horizontal existente.

Screenshots finais inspecionados/renderizados: {evidence('frontend/mobile-375.png')}, {evidence('frontend/mobile-390.png')}, {evidence('frontend/mobile-768.png')}. O teste é Edge headless com viewport mobile; não equivale a teste em todos os celulares físicos.

# Acessibilidade

**APROVADO nos checks básicos:** título acessível, Tab, Escape e retorno de foco ao botão Editar depois da animação Radix; labels de Novo empréstimo, edição e Configurações ligados aos controles; ícones de edição/exclusão e calendário com nome; teclado na busca de fiador (ArrowDown/Enter) e preservação dos campos do modal. Não foi realizada certificação WCAG nem validação completa com leitores de tela; essa validação manual permanece pendente.

# Falhas de rede

**APROVADO:** quatro listagens indisponíveis (Clientes, Empréstimo, Parcelas e Documentos) e **10 cenários de 500/timeout** para salvar cliente, criar empréstimo, pagar parcela, gerar documento e excluir empréstimo. Mensagem DELETE: “Não foi possível excluir o empréstimo.”. Nenhuma mensagem SQL técnica simulada apareceu e houve zero pageerror nos runners aprovados. {evidence('rede/resultado.json')} e {evidence('frontend/resultado.json')}.

500 e timeout dessas operações foram simulados no transporte do navegador; CRUD/documentos e pagamentos de sucesso usaram API/PostgreSQL reais. O CORS e o 500 inesperado também foram testados no backend.

# Health / preflight

`GET /health` implementado sem Actuator. Retorna apenas `api`, `database`, `libreOffice`, `contratoTemplate`, `promissoriaTemplate`, `assinaturaEdson`, `assinaturaIvani` e `status`. **200 UP** quando todas as sondas passam; **200 DEGRADED** se dependência documental está ausente; **503 DOWN** se banco indisponível. Não exige X-Perfil-Id e não expõe senha, connection string, paths de instalação ou bytes.

Sonda de banco tem deadline 1.200 ms, connection validation 1 s, um único worker sem fila e fechamento de conexão. Driver travado e chamadas sucessivas foram testados sem crescimento de threads/fila. Sonda LibreOffice executa --version com limite 2 s, descarta saída e encerra processo ao vencer o prazo. Ausência e probes normais foram exercitados. Templates/PNG são verificados por presença/conteúdo não vazio, não por análise jurídica, decodificação completa ou prova de conversão.

**6 cenários reais aprovados:** normal + jar de ensaio sem cada um dos quatro assets + executável inexistente. Jars clonados ficaram somente na pasta de evidências; os recursos originais não foram editados. **Banco real desligado: HTTP 503 em 1.457 ms, depois UP ao reiniciar o cluster isolado.** Há ainda **6 testes HealthTests** (incluindo worker travado e LO real). {evidence('health/resultado.json')}, {evidence('health/banco-indisponivel.json')}, {evidence('health/banco-recuperado.json')}.

No Windows, o probe usa soffice.com adjacente a soffice.exe para versão console, quando disponível. O comando de conversão documental existente foi preservado. A distinção de launcher é documentada no [manual oficial LibreOffice](https://help.libreoffice.org/latest/en-GB/text/shared/guide/start_parameters.html?DbPAR=WRITER&System=WIN).

# Linux / LibreOffice

**NÃO EXECUTADO:** conversão DOCX→PDF→merge real no Linux e `timeoutMataProcessoELimpaTemporarios`. `docker info` saiu com 1 (pipe dockerDesktopLinuxEngine inexistente); WSL tem somente docker-desktop parado e nenhuma distribuição rodando. {evidence('linux-disponibilidade.json')}.

No Windows houve conversão real, merge e assinatura nos testes de integração/E2E. O único skip nas duas suítes completas é explicitamente o timeout específico Linux. Não se alterou Docker Desktop, WSL, PATH global ou instalação de sistema para ocultar a indisponibilidade.

# Backup integral

Documentado em [BACKUP_E_RESTAURACAO.md](<{(root / 'BACKUP_E_RESTAURACAO.md').as_posix()}>): pg_dump custom datado, pg_restore em banco novo, configuração por ambiente, senhas fora de script, verificação de dados/bytes e limites do XLSX. A tela também explica a diferença entre exportação operacional XLSX e backup PostgreSQL completo.

**APROVADO:** exportar XLSX real pela UI e importar em Perfil novo, preservando um empréstimo, três parcelas e recebimento parcial R$ 10. Troca de Perfil, reload e relatório passaram. {evidence('backup-ui/resultado.json')}. Esse ensaio operacional usa fixture própria; não é o teste de restore integral.

# Restore testado

**APROVADO com o jar final:** pg_dump/pg_restore reais, banco novo `{restore['database']}`, API restaurada na porta 18083 com DDL validate. Origem/destino: {counts}. Todas as seis contagens iguais; todos os registros contratuais tiveram snapshot, snapshot da promissória, hashes e bytes comparados no SQL. Cliente, empréstimos, parcelas, pagamento parcial R$10 e histórico foram comparados via API restaurada. SHA-256 dos downloads DOCX/PDF/assinado ficou idêntico; health restaurado UP.

Dump: `{restore['dump']}`. Evidência: {evidence('restore/resultado.json')}. Roles/servidor PostgreSQL e arquivos externos de implantação não foram restaurados neste teste; a documentação orienta cópia/configuração separada. Bancos/dumps preservados. APIs auxiliares foram encerradas ao terminar.

# Performance

**APROVADO no ensaio funcional de volume:** Perfil com 500 clientes, 1.000 empréstimos e 6.000 parcelas no PostgreSQL real. Para acentuar a falha de relatório, os 1.000 empréstimos pertencem ao primeiro cliente e têm fiador compartilhado. Outra fixture idêntica serviu para a comparação de UI. Dados sintéticos pertencem somente ao banco separado de volume.

| Endpoint (fixture final) | SQL/request | Tempo ms | Payload bytes |
|---|---:|---:|---:|
{sql_table}

Medição pontual com Hibernate statistics/StatementInspector e MockMvc; não é throughput, p95 ou teste multiusuário. Diferenças de aquecimento impedem prometer redução geral de latência. Máximo **3 SQL/request** nos nove endpoints medidos após ajuste. {evidence('volume-final-pg.json')}.

# N+1

| Fluxo | Antes | Depois | Correção |
|---|---|---|---|
| Relatório de histórico (1.000 empréstimos) | 1.002 SQL | 2 SQL | Reutiliza parcelas já carregadas pelo EntityGraph |
| Clientes: empréstimos por cliente | 500 chamadas; 502 requests totais | 0 chamadas individuais; 2 totais | Contagem agrupada incluída no DTO |
| Home: carteira por cliente | 500 chamadas; 507 requests totais | 1 leitura de carteira; 7 totais | GET /emprestimos/detalhes por Perfil |

Clientes: uma execução de UI {client_before['ms']}→{client_after['ms']} ms. Home: {home_before['ms']}→{home_after['ms']} ms; as chamadas caíram, mas o tempo total desta execução aumentou. Totais e texto renderizado da Home foram iguais na comparação automática; cálculo do gráfico e saldos permanece o mesmo.

O endpoint de carteira usa DTO existente, escopo validado e EntityGraph específico (cliente/fiador/parcelas); não adiciona EAGER global. `quantidadeEmprestimos` no DTO cliente é aditivo e os construtores anteriores continuam compatíveis. Foram acrescentados somente `/health` e essa leitura de carteira; endpoints anteriores permanecem. Total de interfaces inventariadas passa de 46 endpoints/9 rotas para 48 endpoints/9 rotas, sem rotas frontend novas.

Metadata contratual: **193 bytes, 3 SQL, nenhum SELECT de DOCX/PDF/snapshot**, mesmo com blobs e snapshots grandes na fixture. Esse payload não retorna LOBs. {evidence('volume-ui-antes.json')}, {evidence('volume-ui-depois.json')}, {evidence('volume-home-antes.json')}, {evidence('volume-home-depois.json')}, {evidence('volume-antes-pg.json')}.

# Segurança de configuração

`application.properties`: DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD, sem senha fixa. Defaults não secretos são apenas locais; produção deve definir valores explicitamente. Nenhuma credencial real é copiada a relatório/script. X-Perfil-Id continua sendo **isolamento funcional, NÃO autenticação forte**; modelo de login/usuários não foi alterado.

RuntimeException inesperada agora resulta em **500 seguro + CORS**. Os 43 throws explícitos de regra de negócio revisados, em services de cliente/empréstimo/parcelas/perfil, passaram para RegraNegocioException; texto e status de validações conhecidos foram preservados. Exceções Quitado/Paga derivam dessa classe de domínio. DataIntegrity mantém 409. Teste NPE→500+CORS e bateria de negócio reexecutados. Não se migrou catch genérico por suposição nem alterou fórmula financeira.

# Logs

Handler HTTP registra somente classe da falha, sem mensagem crua/stacktrace com valores. Erro inesperado de importação registra linha e classe; conversão de célula não imprime valor bruto. SQL/bind em produção continuam OFF. Logs de contrato usam IDs/templateVersion, sem snapshot, CPF, RG, PDF/DOCX ou imagem de assinatura. Testes de privacidade/HTTP foram reexecutados. Avisos legados de importação ainda têm nome/datas/valores operacionais; acesso e retenção de logs precisam de política de produção. Isso não constitui auditoria de todos os componentes externos/níveis de debug.

# Testes PostgreSQL

**APROVADO: 135 testes, 0 falhas, 0 erros, 0 skips**, 13 classes, Maven com URL/driver/dialect PostgreSQL explícitos na base `controle_clientes_auditoria_regressao` (DDL create-drop exclusivo de testes). Inclui pagamento concorrente, consulta/status concorrentes, delete com contratos e LOBs, importação financeira, documentos, assinaturas, hashes, regras de Perfil e handler/CORS. A bateria também contém testes unitários de health/importação/caracterização; não se afirma que cada teste faz acesso SQL. {evidence('postgresql-final.log')}, {evidence('postgresql-final.json')}.

Separadamente, o teste de volume PostgreSQL passou após correções. As rodadas intermediárias e seus números não são somados como testes distintos à bateria final.

Caracterização financeira preservada: R$100/3 gera **33,33 + 33,33 + 33,33 = 99,99**, resíduo **0,01**, sem redistribuir centavos. Importação mantém numeric 0,5→50%, texto “0,5”→0,5%, numeric 0,005 com formato percentual→0,5%. A tela/documentação recomenda percentual explícito. Nenhuma convenção ou decisão financeira nova foi implementada.

# E2E final

**APROVADO:** 14 verificações do runner principal, 14 do runner estendido (incluindo quatro listagens offline), 10 cenários de erro, 5 de backup operacional e smoke de **18 páginas** (9 rotas × Perfil vazio/populado), com zero pageerror nos resultados aprovados. Os números representam verificações de runners e têm cobertura sobreposta; não são somados como testes independentes.

As **20 etapas solicitadas** foram cobertas por runners complementares, com fixtures isoladas diferentes; não se afirma um único teste linear de 20 etapas. Matriz verificável:

| Etapa | Fluxo | Estado | Evidência |
|---:|---|---|---|
{matrix_md}

No reinício com jar final, clientes/empréstimos/histórico sobreviveram e SHA-256 dos três formatos da fixture histórica da auditoria anterior permaneceu igual. Runners corrigidos durante o trabalho (seletores ambíguos após criar segundo empréstimo, ordem não garantida de parcelas e espera da animação de foco) só foram aprovados depois de repetir e passar. Arquivos `falha.json` intermediários são registros diagnósticos anteriores, não resultados aprovados.

# Backend final

| Comando | Resultado |
|---|---|
| mvn test | APROVADO, 313 testes: 312 executados, 1 Linux ignorado; 0 falhas/0 erros |
| mvn clean package | APROVADO, repetiu 313 testes, 0 falhas/0 erros, 1 Linux ignorado; jar gerado |
| Bateria crítica PostgreSQL | APROVADO, 135 testes, 0 falhas/0 erros/0 skips |

{evidence('mvn-test-final.log')}, {evidence('mvn-package-final.log')}. O jar deste clean package foi usado no restart e restore finais. Não foi usado skipTests.

# Frontend final

**APROVADO:** `npm run lint` código 0, 0 erros/0 avisos; `npm run build` código 0 (TypeScript/Vite). Bundle principal **1.002,86 kB / 300,84 kB gzip**; Vite emite aviso de chunk >1.000 kB. É aviso de build/tamanho, não aviso ESLint; não foi silenciado aumentando limite. {evidence('npm-lint-final.log')}, {evidence('npm-build-final.log')}.

Inventário relativo ao início desta rodada: **42 arquivos fonte alterados, 11 novos e 1 substituído/removido** (`ProfileContext.tsx` virou barrel `.ts` + Provider/state/hook). Relatórios e helpers são adicionais e ficam fora desses números. {evidence('alteracoes-final.json')}. O diff Git inclui trabalhos anteriores, portanto não foi usado para atribuir mudanças exclusivamente a este fechamento.

**Preservados por SHA-256: 6 arquivos de templates/assinaturas.** Não se mudou texto jurídico, layout documental, snapshot/hash, datas contratuais/promissória ou Edson/Ivani; downloads históricos também permaneceram idênticos no restart/restore. O conversor recebeu apenas probe de disponibilidade. A calculadora recebeu somente tipagem da exceção de validação, sem alteração dos cálculos.

# Pendências restantes

1. **NÃO EXECUTADO:** Linux real/LibreOffice/merge e timeout com encerramento de processo/limpeza de temporários. Requer Docker/WSL/Linux disponível.
2. **VALIDAÇÃO MANUAL PENDENTE:** leitores de tela e combinações adicionais de cadastros/aparelhos físicos; os três viewports e 17 campos da fixture passaram.
3. **VALIDAÇÃO MANUAL PENDENTE no destino:** credenciais, acesso, retenção/armazenamento externo dos backups e logs, fontes/LibreOffice e segurança da implantação. Backup/restauração desta base isolada foram aprovados; não foi restaurado o banco real do usuário.
4. **Risco documentado:** X-Perfil-Id não autentica pessoas; sem alteração do modelo nesta tarefa.
5. **Limites de desempenho:** bundle >1.000 kB, listagens ainda grandes, sem benchmark concorrente/p95. Não se introduziu paginação geral nem EAGER global. Resíduo de centavos e convenção percentual continuam como regras atuais documentadas.

Não há declaração de liberação irrestrita do ambiente Linux nem promessa de zero bugs.

# Checklist de liberação

- [x] npm run lint = 0 erros / 0 warnings
- [x] npm run build = OK
- [x] mvn test = OK (1 skip Linux explicitado)
- [x] mvn clean package = OK (1 skip Linux explicitado)
- [x] PostgreSQL crítico = OK
- [x] Perfil = OK
- [x] Cliente = OK
- [x] Cliente legado = OK na fixture
- [x] Fiador = OK
- [x] Empréstimo = OK
- [x] Parcelas = OK
- [x] Pagamentos = OK
- [x] Delete empréstimo = OK; Cliente/Fiador permanecem
- [x] Contratos = OK
- [x] PDF = OK no Windows
- [x] PDF assinado = OK no Windows
- [x] Promissória = OK no Windows
- [x] Backup pg_dump = OK
- [x] Restore = OK em banco novo
- [x] Health = OK
- [x] Mobile = OK nos três viewports
- [x] Falha de rede = OK nos cenários descritos
- [ ] Linux / LibreOffice / timeout real = NÃO EXECUTADO
- [ ] Validar ambiente de produção, controles de acesso, proteção/retenção externa de backup e logs
- [ ] Leitores de tela / dispositivos físicos adicionais = VALIDAÇÃO MANUAL PENDENTE

## Arquivos fonte alterados nesta rodada

{changed_md}

## Arquivos fonte novos nesta rodada

{added_md}

Substituído: `{inventory['removidos'][0]}`. Helpers reprodutíveis: `tools/fechamento/`; evidências: `.auditoria/fechamento/`. As evidências e dumps contêm fixtures de dados pessoais, devem permanecer privados e não são arquivos para publicação.
'''
(root / 'FECHAMENTO_PRE_PRODUCAO.md').write_text(report, encoding='utf-8')
print('Relatório criado; todos os resultados exigidos foram verificados.')
