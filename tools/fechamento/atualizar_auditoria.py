from pathlib import Path
import re

root = Path.cwd()
out = root / '.auditoria/fechamento'
s = (out / 'auditoria-anterior.md').read_text(encoding='utf-8-sig')
def replace(old, new):
    global s
    assert old in s, old[:80]
    s = s.replace(old, new)
def section(start, end, text):
    global s
    a, b = s.index(start), s.index(end, s.index(start) + len(start))
    s = s[:a] + text.rstrip() + '\n\n' + s[b:]

replace('**22 grupos de bugs encontrados: 21 corrigidos, 1 pendente (lint, com 6 erros e 3 avisos preexistentes). Oito grupos são críticos e foram corrigidos.**',
        '**22 grupos de bugs encontrados na auditoria original: 22 corrigidos, incluindo BUG-022 no fechamento (lint 0 erros/0 avisos). Oito grupos são críticos e foram corrigidos.**')
replace('# Escopo', '''# Fechamento técnico de 07/10/2026

**APROVADO nos cenários cobertos:** `mvn test` e `mvn clean package` com 313 testes, 0 falhas, 0 erros e 1 skip Linux; bateria crítica PostgreSQL com 135 testes, 0 falhas/0 erros/0 skips; lint 0/0; build frontend; E2E; restore integral e health. **Linux/LibreOffice/timeout: NÃO EXECUTADO**, pois Docker/WSL não estão disponíveis. Os fluxos cobertos foram testados e aprovados; isso não significa ausência absoluta de bugs futuros.

O inventário de 46 endpoints/9 rotas acima é o da auditoria original. O fechamento acrescentou `/health` e a leitura `/emprestimos/detalhes` por Perfil para remover 500 chamadas na Home: agora 48 endpoints/9 rotas, sem remover endpoints anteriores. Medição: 500 clientes, 1.000 empréstimos, 6.000 parcelas; relatório de histórico 1.002→2 SQL; Clientes 502→2 requests; Home 507→7 requests. Backup/restore real, mobile e clientes legados aprovados nas fixtures descritas.

Resultados, arquivos e limites atuais: [FECHAMENTO_PRE_PRODUCAO.md](C:/Users/caioe/Desktop/projects/controle-clientes/backend/FECHAMENTO_PRE_PRODUCAO.md). Operação de backup: [BACKUP_E_RESTAURACAO.md](C:/Users/caioe/Desktop/projects/controle-clientes/backend/BACKUP_E_RESTAURACAO.md). As tabelas de execução anteriores permanecem como histórico, identificadas abaixo; evidências finais estão em `.auditoria/fechamento/`.

# Escopo''')
section('## BUG-022 —', '# Foreign Keys auditadas', '''## BUG-022 — Lint preexistente impede qualidade estática totalmente verde

**Prioridade:** MÉDIO. **Estado:** CORRIGIDO no fechamento.
**Sintoma e causa raiz:** Seis erros de Fast Refresh/set-state-in-effect e três avisos estavam no baseline.
**Impacto anterior:** npm run lint terminava com código 1.
**Correção:** Exports separados de componentes; paginação derivada/remount por Perfil; modal inicializado ao abrir; resize do gráfico via ref/observer; callbacks/dependências corrigidos; hidratação duplicada removida. Nenhuma regra desabilitada ou diagnóstico ocultado; regras de negócio preservadas.
**Teste de regressão/evidência:** baseline antigo 6 erros/3 avisos; resultado final **0 erros/0 avisos, saída 0**, [npm-lint-final.log](C:/Users/caioe/Desktop/projects/controle-clientes/backend/.auditoria/fechamento/npm-lint-final.log). Build e regressões UI/Perfil/edição/gráfico/mobile/E2E aprovados; detalhe no relatório de fechamento.''')
replace('| mvn test final | APROVADO: 303 testes', '| mvn test da auditoria anterior | APROVADO: 303 testes')
replace('| mvn clean package |', '| mvn clean package da auditoria anterior |')
replace('| PostgreSQL crítico final |', '| PostgreSQL crítico da auditoria anterior |')
replace('# Build backend\n\nAPROVADO: 303 testes, 0 falhas, 0 erros, 1 ignorados.',
        '# Build backend\n\n**APROVADO no fechamento: mvn clean package, 313 testes, 0 falhas, 0 erros e 1 skip Linux.** [mvn-package-final.log](C:/Users/caioe/Desktop/projects/controle-clientes/backend/.auditoria/fechamento/mvn-package-final.log). Resultado histórico da auditoria anterior: 303 testes, 0 falhas, 0 erros, 1 ignorados.')
replace('Package executa novamente toda a suíte, sem skipTests.',
        'No fechamento: mvn test e mvn clean package repetiram 313 testes, 0 falhas, 0 erros e 1 skip Linux; PostgreSQL final teve 135 testes sem falhas/erros/skips. Logs finais em `.auditoria/fechamento/`. Package executa novamente toda a suíte, sem skipTests.')
section('# Build frontend', '# Lint', '''# Build frontend

**APROVADO no fechamento: npm run build (TypeScript + Vite), código 0.** [npm-build-final.log](C:/Users/caioe/Desktop/projects/controle-clientes/backend/.auditoria/fechamento/npm-build-final.log). Bundle principal 1.002,86 kB (300,84 kB gzip); Vite emite aviso de chunk >1.000 kB. O aviso não foi silenciado e é risco distinto de lint. Volume/N+1 foi medido em PostgreSQL e interface; não equivale a benchmark de throughput/concorrência.''')
replace('**REPROVADO: 6 erros e 3 avisos preexistentes; zero novos.**',
        '**APROVADO no fechamento: 0 erros/0 avisos, saída 0.** [npm-lint-final.log](C:/Users/caioe/Desktop/projects/controle-clientes/backend/.auditoria/fechamento/npm-lint-final.log). O baseline histórico abaixo teve 6 erros/3 avisos, todos corrigidos:')
replace('| Arquivo | Diagnóstico existente |', '| Arquivo | Diagnóstico de baseline (corrigido) |')
section('# Pendências', '# Riscos restantes', '''# Pendências

- **NÃO EXECUTADO:** Linux real/LibreOffice/merge e teste de timeout Linux; Docker Engine indisponível e nenhuma distro WSL executando.
- **VALIDAÇÃO MANUAL PENDENTE:** leitores de tela, aparelhos físicos e combinações adicionais. A fixture legada e seus 17 campos, foco/teclado, três viewports e falhas de rede passaram.
- Validar ambiente de produção e política de proteção/retensão externa de backups/logs. O backup pg_dump/restore em banco novo e downloads/hashes com jar final passaram; documentação operacional criada.
- Definir limites operacionais/p95 multiusuário conforme uso. N+1 comprovado foi corrigido; não houve benchmark de throughput nem paginação geral nova.

BUG-022 deixou de ser pendência; health/preflight foi implementado e testado. Detalhes e checklist de liberação em FECHAMENTO_PRE_PRODUCAO.md.''')
replace('Home e relatórios fazem múltiplas consultas/listagens e não possuem paginação universal; N+1 sob volume grande não foi medido.',
        'Home e relatórios continuam sem paginação universal, mas volume foi medido: 500 clientes/1.000 empréstimos/6.000 parcelas. N+1 do relatório caiu de 1.002 para 2 SQL; Home caiu de 507 para 7 requests; Clientes caiu de 502 para 2. Tempo da Home nesta execução aumentou; não se promete latência melhor em todos os casos.')
replace('O handler legado ainda classifica RuntimeException genérica como 400 e usa sua mensagem, pois os services a utilizam para regras de negócio antigas. Não se certifica que todo erro de programação inesperado seja convertido em 500 seguro; separar essas classes exige revisão e reprodução específicas. Os 500 e erros conhecidos comprovados nesta auditoria estão identificados nos testes.',
        'No fechamento, throws conhecidos de validação foram revisados e migrados para RegraNegocioException com mensagens/status preservados. RuntimeException inesperada é 500 seguro + CORS; NPE foi testada. Handler registra apenas classe da falha, sem valores/mensagem crua. Importação também omite mensagem crua de erros inesperados; retenção/acesso a logs externos precisa de política de produção.')
replace('application.properties mantém defaults locais de conexão; credenciais devem vir de configuração segura de ambiente. O relatório omite seus valores.',
        'application.properties usa DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD, com defaults locais não secretos e sem senha fixa. Produção deve definir esses valores explicitamente; o relatório omite credenciais.')
section('# Checklist pré-produção', '## Health / preflight', '''# Checklist pré-produção

Checklist dos ensaios isolados; liberação do ambiente de destino ainda exige validação operacional:

- [x] Lint 0 erros/0 avisos e build frontend aprovado.
- [x] mvn test / mvn clean package: 313 testes, 0 falhas/0 erros, 1 skip Linux.
- [x] PostgreSQL crítico: 135 testes, 0 falhas/0 erros/0 skips.
- [x] API final reiniciada; health UP, persistência e três hashes históricos iguais.
- [x] Perfil cria/troca/reload; cliente legado e 17 campos editados/persistidos/reabertos.
- [x] Mobile 375×812, 390×844 e 768×1024; fiador busca/teclado/wheel/scroll.
- [x] Empréstimo cria/edita; pagamento parcial/completar/estornar; saldo preservado.
- [x] Contrato/DOCX/PDF/assinado/promissória geram e baixam no Windows.
- [x] Delete contrato/empréstimo preserva Cliente/Fiador; hashes históricos mantidos.
- [x] Seis assets de templates/assinaturas inalterados por SHA-256.
- [x] Falhas de listagem e 500/timeout exibem mensagem segura e contextual.
- [x] pg_dump/pg_restore integral em banco novo + API validate + snapshots/bytes/hashes.
- [x] Volume/N+1 medido e corrigido onde comprovado, sem EAGER global.
- [ ] LibreOffice/merge/timeout real no Linux = NÃO EXECUTADO.
- [ ] Ambiente de produção, proteção/retensão externa de backup/logs, acesso e leitores de tela = VALIDAÇÃO MANUAL PENDENTE.''')
section('## Health / preflight', '# Cobertura dos 75 itens solicitados', '''## Health / preflight

GET `/ping` permanece. Novo GET `/health`: API, banco, LibreOffice, templates de contrato/promissória e assinaturas Edson/Ivani; 200 UP, 200 DEGRADED se dependência documental ausente, 503 DOWN se banco indisponível, sem paths/segredos. Banco com deadline 1.200 ms/worker único sem fila; LO --version 2 s. Seis cenários reais normais/ausência aprovados, seis testes unitários/real-LO; PostgreSQL isolado desligado respondeu 503 em 1.457 ms e recuperou UP. Conversão não foi alterada; no Windows só o probe usa launcher console soffice.com quando adjacente.

Evidências: [health/resultado.json](C:/Users/caioe/Desktop/projects/controle-clientes/backend/.auditoria/fechamento/health/resultado.json), [banco-indisponivel.json](C:/Users/caioe/Desktop/projects/controle-clientes/backend/.auditoria/fechamento/health/banco-indisponivel.json). Health verifica presença/sondas, não certifica layout jurídico ou conversão real; esta foi exercitada separadamente no Windows.''')
replace('| 64 | T | Build OK; lint reprova 6 antigos/3 avisos, zero novos |',
        '| 64 | T | Fechamento: build OK; lint 0 erros/0 avisos, todos os anteriores corrigidos |')
replace('| 72 | I/M | Payload sem blobs; bundle pesado e N+1/carga não medidos |',
        '| 72 | T/M | Volume medido; N+1 corrigido; metadata 193 bytes sem blobs; bundle pesado, throughput multiusuário pendente |')
replace('- [ProfileContext.tsx](<C:/Users/caioe/Desktop/projects/controle-clientes/frontend/src/contexts/ProfileContext.tsx>)',
        '- [ProfileContext.ts — barrel atual, substitui o .tsx](<C:/Users/caioe/Desktop/projects/controle-clientes/frontend/src/contexts/ProfileContext.ts>)')
(root / 'AUDITORIA_COMPLETA_SISTEMA.md').write_text(s, encoding='utf-8')
print('Auditoria anterior atualizada, preservando histórico das execuções.')
