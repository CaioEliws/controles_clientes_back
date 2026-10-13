# Contexto Completo da API Java

Análise estática realizada em 01/10/2026. Raiz do repositório analisado: `C:/Users/caioe/Desktop/projects/controle-clientes/backend`. Todos os caminhos `src/...` abaixo são relativos a essa raiz; caminhos `../...` identificam arquivos complementares na pasta da aplicação. O relatório descreve o código disponível, sem implementar ou corrigir funcionalidades.

## 1. Resumo executivo

O backend controla carteiras de clientes e empréstimos separadas por **perfil**. Um perfil possui clientes; os clientes possuem empréstimos; cada empréstimo possui parcelas; cada parcela pode ter observações. A API permite registrar contratos, consultar vencimentos, receber pagamentos totais ou parciais, desfazer recebimentos, alterar parcelas, reprogramar contratos, quitar/refinanciar e produzir relatórios e planilhas.

Foram lidas **68 fontes Java: 67 de produção e 1 de teste**. Há **9 controllers, 32 operações HTTP explicitamente declaradas, 11 classes `@Service`, 5 interfaces de repository e 5 entidades JPA**. Existem também 5 componentes `@Component` (4 auxiliares da importação e `Principal`), 5 enums, 21 classes/records de DTO (18 no pacote `dto` e 3 na importação), 4 classes no pacote de exceptions, 1 configuração e 1 classe de inicialização. Não há classes explicitamente chamadas UseCase.

Características que outra IA precisa preservar ao interpretar o sistema:

- `X-Perfil-Id` é um identificador fornecido pelo cliente, não um token de autenticação. Os filtros por perfil restringem consultas, mas não comprovam a identidade de quem envia o cabeçalho.
- Pagamentos são registros internos no PostgreSQL. `PIX` e `DINHEIRO` são classificações do contrato; não foi identificado gateway de pagamento.
- Refinanciar marca parcelas como pagas e o contrato como `REFINANCIADO`; não cria outro empréstimo, não transfere saldo e não publica evento.
- Os três GETs de listagem global de parcelas atualizam atrasos no banco antes de consultar. Não existe scheduler de atualização de atrasos identificado.
- O histórico financeiro ignora parcelas `PARCIAL` nos valores e contagens por situação. O relatório de inadimplentes filtra `ATRASADO`/`PARCIAL` vencidos, deixando de fora `PENDENTE` vencido ainda não atualizado.
- A geração normal, a importação e a reprogramação têm regras de datas diferentes. Só a geração de parcelas da importação pula domingos para frequência diária.
- A exportação tem 18 colunas e a importação espera posições até a coluna 19, incluindo tipo de contrato. Não são layouts compatíveis para restauração direta.
- Há retornos HTTP de entidades JPA e de DTOs. A coleção de observações de `Parcelas` e sua referência inversa não têm anotações Jackson para interromper o ciclo.

**Natureza das evidências:** assinaturas, branches, mappings e constraints são fatos observados nas fontes. Consequências de concorrência, serialização e rollback são indicadas como inferências quando não executadas. Não foram iniciados a aplicação, banco, containers, builds ou testes: a solicitação restringe a escrita ao relatório e não exige validação dinâmica. Nenhum resultado de execução é presumido.

Escopo complementar: `../docker-compose.yml`, estrutura DDL de `../backup.sql` e arquivos do frontend relacionados ao consumo da API foram inspecionados. Os registros de negócio do dump não são reproduzidos. O frontend não integra o repositório Git do backend e não recebeu auditoria integral de interface; sua análise serve para explicar o contrato e a infraestrutura.

## 2. Stack tecnológica

| Item | Evidência e descrição |
|---|---|
| Java | Versão 17 declarada em `pom.xml`; imagens Java 17 em `Dockerfile`. Versão instalada/efetivamente usada não verificada. |
| Spring Boot | Parent `org.springframework.boot:spring-boot-starter-parent:4.0.2` em `pom.xml`. |
| Spring Framework | Gerenciado pelo parent; sem versão explícita própria no projeto. Não foi possível determinar com segurança pelo código analisado a versão resolvida do framework. |
| Build | Maven; artifact `com.caio:controle_clientes:0.0.1-SNAPSHOT`, sem `<modules>`. Wrapper only-script 3.3.4 aponta para Maven 3.9.12 em `.mvn/wrapper/maven-wrapper.properties`. |
| HTTP | `spring-boot-starter-web`, controllers anotados e Spring MVC; payloads JSON e upload multipart. |
| Persistência | `spring-boot-starter-data-jpa`, Jakarta Persistence e configuração Hibernate; driver PostgreSQL runtime. |
| Validação | `spring-boot-starter-validation`; `@Valid` em criação de perfil e de cliente. |
| Planilhas | Apache POI `poi-ooxml:5.2.3`; `WorkbookFactory`, `XSSFWorkbook`, `FormulaEvaluator`, `DataFormatter`. |
| Boilerplate | Lombok optional: getters, setters, `@Data`, builders, construtores e logger SLF4J. |
| Testes | `spring-boot-starter-test`; 1 `@SpringBootTest` com JUnit Jupiter. |
| Banco | PostgreSQL, datasource em `src/main/resources/application.properties`; PostgreSQL 16 Alpine no compose complementar. |
| ORM/schema | `spring.jpa.hibernate.ddl-auto=update`. Sem Flyway/Liquibase/migrations versionadas identificados. |
| Segurança | Sem Spring Security, JWT, OAuth, Basic Auth, API key, roles ou filtros autenticadores encontrados. CORS customizado. |
| Cache | Sem Redis, cache Spring ou cache distribuído identificado. `HashMap` local de clientes durante uma importação. |
| Mensageria/jobs | Sem broker, eventos Spring de negócio, jobs, scheduler ou async encontrados. |
| Integrações/storage | PostgreSQL e planilhas de entrada/saída; sem clientes HTTP externos, cloud, email ou storage persistente de arquivos identificado. |
| Observabilidade | Logger da importação; stack traces no advice; SQL/bind logs desativados. Sem Actuator, métricas ou tracing explicitamente declarados. |
| Documentação HTTP | Sem OpenAPI/Swagger/Springdoc ou especificação de API identificada. |
| Containers | Backend Maven/Temurin em multi-stage; compose complementar com PostgreSQL, backend e frontend Nginx. |

Versões transitivas de Hibernate, Jackson, Lombok, Spring Data e JUnit não são fixadas diretamente: não inferir uma árvore de dependências resolvida a partir apenas da declaração dos starters.

## 3. Arquitetura

Arquitetura observada: aplicação única Spring Boot, organizada em camadas de controllers, services, models, repositories e DTOs. A expressão “monólito em camadas” é uma classificação inferida dessa organização, não uma declaração arquitetural do projeto. Não há evidência de hexagonal/DDD completo, módulos Maven separados, interfaces de negócio com implementações alternativas, facades, DAOs manuais ou gateways.

`src/main/java/com/caio/controle_clientes/ControleClientesApplication.java` contém `main` e chama `SpringApplication.run`. `@SpringBootApplication` está no pacote raiz que contém as demais classes. Há imports de `CommandLineRunner`, `Autowired` e serviços sem utilização ativa; a classe não implementa runner.

Fluxo dominante:

```text
HTTP -> Spring MVC / binding / eventual @Valid
     -> Controller -> Service -> Spring Data JpaRepository
                             -> entidades JPA -> PostgreSQL
     <- ResponseEntity / DTO / entidade / bytes XLSX
Erros não interceptados localmente -> GlobalExceptionHandler
```

Exceções à divisão de camadas: `ParcelaController` consulta `ParcelaRepositorio` diretamente para as três listagens globais, após chamar `ParcelaService.atualizarParcelasAtrasadas`. A importação possui orquestração própria que grava entidades diretamente, sem reutilizar as validações de `ClienteService`/`EmprestimoService`.

DI efetiva: construtores explícitos ou gerados por `@RequiredArgsConstructor`. Os 5 repositories são interfaces `JpaRepository<Entidade, Long>` implementadas por proxies Spring Data; não há classe concreta própria. `CorsConfig` fornece um `WebMvcConfigurer` anônimo por `@Bean`. As demais dependências de negócio são classes concretas únicas.

Não há sessão de usuário ou resolução central do perfil. O controller recebe o header e o propaga; `PerfilHeaderService` existe, porém não foram encontradas chamadas ativas. Algumas operações validam a existência do perfil; outras apenas consultam por seu ID. Listas podem retornar vazias para um perfil inexistente.

Transações abrangem alterações financeiras nos services, mas não existe transação única que envolva todos os controllers. Não há `@Version`, locks explícitos, isolamento customizado nem controle de idempotência identificado. **Inferência:** pagamentos simultâneos ou cadastros simultâneos podem enfrentar concorrência; não foi feita reprodução.

## 4. Estrutura de packages

Prefixo real: `com.caio.controle_clientes`; diretório base `src/main/java/com/caio/controle_clientes/`.

| Package / diretório | Responsabilidade e elementos |
|---|---|
| raiz | `ControleClientesApplication`, inicialização Spring. |
| `config` | `CorsConfig`, única configuração customizada encontrada. |
| `controllers` | 9 adaptadores HTTP: Backup, Client, Emprestimo, Home, Import, Parcela, ParcelaObservacao, Perfil e Relatorio. |
| `services` | `ClienteService`, `RelatorioService`, `ImportService`, `ExportService`. |
| `services.emprestimo` | `EmprestimoService`, orquestração financeira; `CalculadoraEmprestimoService`, fórmula e calendário. |
| `services.parcela` | `ParcelaService`, mudanças/atrasos/estorno; `ParcelaObservacaoService`, CRUD de notas. |
| `services.perfil` | `PerfilService` e `PerfilHeaderService`. |
| `services.importService` | `ImportacaoPlanilhaService` e componentes `ImportacaoCellReader`, `ImportacaoLinhaMapper`, `ImportacaoParcelaFactory`, `ImportacaoValidator`. A capitalização `importService` é real. |
| `services.importService.dto` | `LinhaImportacaoDTO`, `ImportacaoResultado`, `ErroImportacao`. |
| `repository` | 5 interfaces Spring Data; JPQL e consultas derivadas. |
| `models` | 5 entidades e 5 enums; callbacks JPA em observações. |
| `dto.cliente` | 5 DTOs de criação/alteração/relatórios. |
| `dto.emprestimo` | 2 records: criação e detalhes. |
| `dto.parcela` | 9 records de operações e respostas. |
| `dto.Perfil` | `CriarPerfilRequest` e `PerfilResponse`. A inicial maiúscula do package é real. |
| `exceptions` | 3 exceptions de negócio e `GlobalExceptionHandler`. |
| `principal` | Bean `Principal`, mensagem simples e grande menu antigo comentado. |

Não foram identificados converters JPA, projections, specifications, aspects, interceptors, filtros, clients, listeners de negócio, consumers, producers ou classes de jobs. `@PrePersist`/`@PreUpdate` são callbacks da entidade e estão detalhados nas seções 10 e 16.

## 5. Funcionalidades existentes

| Capacidade | Descrição / endpoints | Componentes e dados | Regras centrais |
|---|---|---|---|
| Perfis | Listar/criar em `/perfis` | PerfilController -> PerfilService -> PerfilRepositorio -> Perfil | Nome obrigatório, trim e consulta duplicada sem diferenciar caixa; não existe login. |
| Clientes | Criar/listar/excluir/alterar nome/endereço em `/clientes` | ClientController -> ClienteService -> ClienteRepositorio, EmprestimoRepositorio, PerfilService -> Cliente/Perfil | Criação não permite nome já consultável no perfil; exclusão proibida enquanto houver qualquer empréstimo. |
| Contratos | Criar/listar/detalhar/excluir em `/clientes/{idCliente}/emprestimos` | EmprestimoController -> ClienteService/EmprestimoService/CalculadoraEmprestimoService -> repos de empréstimo/parcela -> Cliente/Emprestimo/Parcelas/Perfil | Prestação por fórmula financeira; parcelas geradas; associação ao perfil e cliente verificada. |
| Recebimentos | `/parcelas/pagar`, `/pagar-parcial`, `/desfazer-pagamento` | ParcelaController -> EmprestimoService ou ParcelaService -> repos de parcela/empréstimo | Acumula pagamentos sem exceder saldo; estorno zera o acumulado inteiro. |
| Calendário/cobrança | `/parcelas`, `/vencendo-hoje`, `/mes-atual`, `/alterar` e reprogramação | ParcelaController/EmprestimoController -> ParcelaService/EmprestimoService -> Parcelas/Emprestimo | Consultas atualizam vencidos; alteração individual restringe datas; reprogramação modifica só não pagas. |
| Encerramento | `/{idEmprestimo}/quitar` e `/refinanciar` | EmprestimoController -> EmprestimoService -> Emprestimo/Parcelas | Completa os valores pagos contabilmente; aplica status final; não cria contrato substituto. |
| Observações | CRUD em `/parcelas/observacoes` | ParcelaObservacaoController -> ParcelaObservacaoService -> repos de parcela/observação -> ParcelaObservacao | Texto não vazio, trim, vínculo ao perfil; timestamps por callbacks. |
| Relatórios | `/relatorios/clientes/{idCliente}` e `/inadimplentes` | RelatorioController -> ClienteService/RelatorioService -> repos de empréstimo/parcela | Histórico por status; inadimplentes agrupados por cliente e saldo restante. |
| Importação | `POST /import` multipart | ImportController -> ImportService -> ImportacaoPlanilhaService + auxiliares -> Cliente/Emprestimo/Parcelas | Layout posicional, validação por linha, duplicidade composta e resumo de erros. |
| Exportação | `GET /export` | BackupController -> ExportService -> EmprestimoRepositorio | Uma linha por contrato, recálculo de recebido/saldo a partir das parcelas; sem exportar notas e endereços. |
| Disponibilidade simples | `/` e `/ping` | HomeController | Strings estáticas; não verificam banco ou dependências. |

Todas essas capacidades usam processamento síncrono. Nenhuma publica evento/mensagem ou chama API externa identificada. Banco, quando usado, é o único datasource PostgreSQL.

## 6. Endpoints

### 6.1 Convenções comuns e limites do inventário

As **32 operações** abaixo contam pares método/rota explícitos dos controllers, sem somar `/error` do framework, preflight OPTIONS ou eventual tratamento automático de HEAD. Não há context path configurado no backend. O Nginx complementar expõe `/api/...` e remove `/api/` ao encaminhar para o backend.

**Autenticação/roles para cada operação:** nenhuma exigência identificada no código. Não há `Authorization`, cookie de sessão ou authorities verificados. Exceto `/`, `/ping` e os dois `/perfis`, **todas as outras 28 operações exigem `X-Perfil-Id` convertível para Long** por `@RequestHeader`, obrigatório por padrão. Isso define escopo, não autenticação. Cada ficha abaixo herda essa declaração.

**Entrada/saída:** objetos de body são JSON, datas `LocalDate` e valores `BigDecimal` conforme DTOs da seção 11; enums usam nomes declarados na seção 10. Não há parâmetros opcionais ou defaults declarados. Não há paginação. Onde a ficha informa “nenhum”, não existe parâmetro/body correspondente na assinatura.

**Erros comuns aplicáveis às operações:** header ausente -> advice 400 com `{error,message}`; `@Valid` inválido -> 400 com `{error,message,fields}`; `IllegalArgumentException`/`RuntimeException` propagada -> 400; violação de integridade propagada -> 409 com `{error,message,details}`; outra `Exception` -> 500 com `{error,message}`. Para binding/conversão, media type ou rota inválida, não há handler específico próprio: o status e corpo completos dependem da resolução Spring MVC, não foram comprovados em runtime. Um 404 por entidade inexistente **não é declarado**: serviços lançam RuntimeException e o advice responde 400. As fichas listam sucesso e erros associados ao fluxo, não todos os possíveis erros do servidor/framework.

**Banco/integrações/eventos por ficha:** os nomes de entidades/tabelas indicados usam PostgreSQL. Não há integração externa nem evento/mensagem produzido em qualquer ficha; planilhas são processamento local Apache POI. Essas propriedades comuns se aplicam individualmente a todos os endpoints.

Arquivos dos controllers: `src/main/java/com/caio/controle_clientes/controllers/<Classe>.java`; a classe/método da ficha identifica integralmente o arquivo de origem, listado novamente na seção 7.

### 6.2 Disponibilidade e perfis

#### E01 — GET `/`

- Controller/método: `HomeController.home`; arquivo `src/main/java/com/caio/controle_clientes/controllers/HomeController.java`.
- Header de perfil: não; path/query/body/DTO de entrada: nenhum.
- Saída: String `API RODANDO!`; sucesso 200; falhas genéricas de infraestrutura não tratadas localmente.
- Fluxo: 1. MVC resolve rota; 2. executa método; 3. retorna string. Sem service, validação de negócio ou banco.

#### E02 — GET `/ping`

- Controller/método: `HomeController.ping`, mesmo arquivo E01.
- Header de perfil: não; path/query/body: nenhum; saída String `ok`; sucesso 200.
- Fluxo: 1. recebe GET; 2. retorna constante. Sem consulta, validação ou service.

#### E03 — GET `/perfis`

- Controller/método: `PerfilController.listar`.
- Header de perfil: não; path/query/body: nenhum.
- Saída: `List<PerfilResponse>` (`id`, `nome`), 200; erros comuns 400/409/500 conforme propagação.
- Fluxo: 1. chama `PerfilService.listarTodos`; 2. `PerfilRepositorio.findAll`; 3. `PerfilResponse.fromEntity`; 4. retorna lista sem ordenação explícita. Banco: `perfil`; não verifica usuário nem perfil ativo.

#### E04 — POST `/perfis`

- Controller/método: `PerfilController.criar`; header de perfil: não; path/query: nenhum.
- Body/entrada: `CriarPerfilRequest {nome:String}` com `@Valid`, `@NotBlank`.
- Saída: `PerfilResponse`; 201 por `@ResponseStatus`; 400 nome inválido/duplicado; 409 constraint; 500 erro genérico.
- Fluxo: 1. valida body; 2. `PerfilService.criar` faz trim; 3. busca nome ignore-case e rejeita existente; 4. salva `Perfil` e mapeia resposta. Banco: `perfil`. Não retorna `Location`.

### 6.3 Clientes

#### E05 — POST `/clientes`

- Controller/método: `ClientController.criarCliente`; path/query: nenhum; header de perfil obrigatório.
- Body: `ClienteCreateDTO` (nome, nomeIndicador, enderecoRua, enderecoBairro, enderecoNumero); `@Valid`. Nome/rua/bairro `@NotBlank`, número `@NotNull`; indicador opcional.
- Saída: vazia, 201; 400 validação/cliente existente/perfil não encontrado; 409 integridade; 500 genérico.
- Fluxo: 1. bind/valida; 2. verifica perfilId nulo no método (header ausente normalmente falha antes); 3. `ClienteService.criarCliente` consulta nome no perfil e busca perfil; 4. monta e salva Cliente. Banco: `clientes`, `perfil`. Não retorna ID nem Location.

#### E06 — DELETE `/clientes/{id}`

- Controller/método: `ClientController.deletarCliente`; path `id:Long`; query/body: nenhum.
- Saída vazia 204; 400 cliente inexistente ou com empréstimos; 409 integridade; 500 genérico.
- Fluxo: 1. `ClienteService.deletarCliente(perfilId,id)`; 2. encontra cliente no perfil; 3. consulta `existsByClienteIdAndPerfilId`; 4. rejeita se true, senão remove. Banco: `clientes`, `emprestimos`. Bloqueio vale inclusive para contratos quitados/refinanciados.

#### E07 — PATCH `/clientes/{id}/name`

- Controller/método: `ClientController.updateName`; path `id:Long`; query nenhum.
- Body: `UpdateNomeClienteDTO {nome:String}`, sem `@Valid`.
- Saída vazia 204; 400 cliente não encontrado; demais erros comuns.
- Fluxo: 1. obtém dto.getNome; 2. `ClienteService.updateNameCliente`; 3. busca ID/perfil; 4. troca nome e salva `clientes`. Não aplica trim, não valida vazio e não verifica duplicidade.

#### E08 — PATCH `/clientes/{id}/endereco`

- Controller/método: `ClientController.updateEndereco`; path `id:Long`; query nenhum.
- Body: `UpdateEnderecoDTO {rua:String,bairro:String,numero:Integer}`, sem `@Valid`.
- Saída vazia 204; 400 cliente não encontrado; demais erros comuns.
- Fluxo: 1. extrai os três campos; 2. `ClienteService.updateEnderecoCliente`; 3. encontra cliente no perfil; 4. substitui todos os campos e salva. Campos ausentes podem virar null; não é atualização apenas dos campos presentes. Banco: `clientes`.

#### E09 — GET `/clientes`

- Controller/método: `ClientController.listarClientes`; path/query/body nenhum.
- Saída `List<Cliente>`, 200; demais erros comuns.
- Fluxo: 1. `ClienteService.listarTodos`; 2. `ClienteRepositorio.findAllByPerfilId`; 3. retorna entidades. Banco: `clientes` e possível carregamento de `perfil` na serialização. `emprestimos` é `@JsonIgnore`. Não valida previamente existência de perfil nem ordena/pagina explicitamente.

### 6.4 Empréstimos

Base literal: `/clientes/{idCliente}/emprestimos`. `idCliente:Long` está em todas as rotas; nas rotas com `{idEmprestimo}`, esse path também é Long. Query parameters: nenhum em todas estas oito operações.

#### E10 — POST `/clientes/{idCliente}/emprestimos`

- Controller/método: `EmprestimoController.criarEmprestimo`.
- Body: `EmprestimoCreateDTO {valorEmprestado:BigDecimal,quantidadeParcelas:int,jurosCobrado:BigDecimal,formaPagamento:FormaPagamento,frequenciaPagamento:FrequenciaPagamento,tipoContrato:TipoContrato}`; sem `@Valid`/constraints.
- Saída vazia 201; 400 cliente/perfil inexistente, frequência/tipo ausente ou erro de cálculo runtime; 409 integridade; 500 genérico.
- Fluxo: 1. `ClienteService.getClienteById`; 2. `EmprestimoService.criarEmprestimo` valida frequência/tipo e vínculo; 3. calcula prestação/total e salva contrato; 4. gera/salva parcelas, atualiza início/fim e salva contrato. Banco: `clientes`, `perfil`, `emprestimos`, `parcelas`. Não há validação explícita de principal/taxa/quantidade positivos.

#### E11 — GET `/clientes/{idCliente}/emprestimos`

- Controller/método: `EmprestimoController.listar`; body nenhum.
- Saída `List<Emprestimo>`, 200; 400 cliente ausente/perfil incompatível; demais erros comuns.
- Fluxo: 1. busca Cliente no perfil; 2. `EmprestimoService.listarEmprestimosCliente` valida vínculo; 3. `findAllByClienteAndPerfilId` com EntityGraph cliente/parcelas; 4. retorna entidades. Banco: clientes/emprestimos/parcelas; parcelas ignoradas na serialização de Emprestimo.

#### E12 — GET `/clientes/{idCliente}/emprestimos/{idEmprestimo}/parcelas`

- Controller/método: `EmprestimoController.listarParcelas`; body nenhum.
- Saída `List<Parcelas>` (entidades, não `ParcelaResponseDTO`), 200; 400 contrato não encontrado ou cliente incompatível; demais erros comuns.
- Fluxo: 1. `EmprestimoService.listarParcelasDoCliente`; 2. JPQL `buscarComParcelas(idEmprestimo,perfilId)`; 3. compara idCliente com o cliente do contrato; 4. retorna coleção. Não atualiza atrasos e não ordena explicitamente. Banco: emprestimos/parcelas e relacionamentos serializados; risco inferido de ciclo com observações, seção 11.

#### E13 — DELETE `/clientes/{idCliente}/emprestimos/{idEmprestimo}`

- Controller/método: `EmprestimoController.deletar`; body nenhum.
- Saída vazia 204; 400 contrato/cliente incompatível ou parcela `PAGO`/`PARCIAL`; 409 integridade; 500 genérico.
- Fluxo: 1. `EmprestimoService.deletarEmprestimo`; 2. busca com parcelas e valida cliente; 3. bloqueia se algum status é PAGO/PARCIAL; 4. remove parcelas e contrato. Banco: emprestimos/parcelas; observações removíveis via cascade de parcelas. `ATRASADO` com valorPago positivo não está no predicado de bloqueio.

#### E14 — GET `/clientes/{idCliente}/emprestimos/detalhes`

- Controller/método: `EmprestimoController.listarDetalhado`; body nenhum.
- Saída `List<EmprestimoDetalhadoDTO>`, 200; 400 cliente/perfil inválido; demais erros comuns, inclusive nulls no mapper.
- Fluxo: 1. busca Cliente; 2. `EmprestimoService.listarDetalhado`; 3. consulta com EntityGraph; 4. converte enum via `.name()` e conta situações. `PARCIAL` conta como pendente por `isPendente`. Não atualiza atrasos. Banco: clientes/emprestimos/parcelas.

#### E15 — POST `/clientes/{idCliente}/emprestimos/{idEmprestimo}/refinanciar`

- Controller/método: `EmprestimoController.refinanciar`; body nenhum.
- Saída vazia 200; 400 contrato/cliente inválido, contrato QUITADO ou já REFINANCIADO; demais erros comuns.
- Fluxo: 1. `EmprestimoService.refinanciarEmprestimo`; 2. helper `finalizarEmprestimo(...,REFINANCIADO,true)` busca/valida; 3. completa saldo das não pagas e marca PAGO com data atual; 4. soma recebido, calcula saldo e aplica REFINANCIADO. Banco: emprestimos/parcelas. Parcelas já PAGO preservam data. Não gera novo empréstimo.

#### E16 — POST `/clientes/{idCliente}/emprestimos/{idEmprestimo}/quitar`

- Controller/método: `EmprestimoController.quitar`; body nenhum.
- Saída vazia 200; 400 contrato/cliente inválido ou já QUITADO; demais erros comuns.
- Fluxo: 1. `EmprestimoService.quitarEmprestimo`; 2. helper `finalizarEmprestimo(...,QUITADO,false)`; 3. completa saldo de todas as parcelas e define data atual inclusive nas já PAGO; 4. atualiza totais/status. Banco: emprestimos/parcelas. Contrato REFINANCIADO não é bloqueado por esse helper quando a operação é quitar.

#### E17 — PATCH `/clientes/{idCliente}/emprestimos/{idEmprestimo}/parcelas/reprogramar`

- Controller/método: `EmprestimoController.reprogramarParcelas`.
- Body: `ReprogramarParcelasDTO {novaDataInicial:LocalDate,novoValorParcela:BigDecimal}`, sem `@Valid`.
- Saída vazia 204; 400 data ausente, valor nulo/não positivo, contrato/cliente inválido, sem parcelas/sem abertas ou valor inferior ao já pago; demais erros comuns.
- Fluxo: 1. `EmprestimoService.reprogramarParcelas` valida entrada; 2. busca contrato e seleciona não PAGO em ordem de número; 3. atribui datas pelo índice/frequência e novo valor igual a todas; 4. atualiza status, salva, recalcula totais e período. Aceita data inicial passada; não pula domingos. Banco: emprestimos/parcelas.

### 6.5 Parcelas

Todas as operações abaixo têm path parameters: nenhum. Apenas E21 tem query declarada; as outras não têm query.

#### E18 — POST `/parcelas/pagar`

- Controller/método: `ParcelaController.pagarParcela`; body `PagarParcelaDTO {idEmprestimo:Long,numeroParcela:Integer}`, sem `@Valid`.
- Saída vazia 204; 400 parcela inexistente, contrato QUITADO, parcela já paga, saldo não positivo; demais erros comuns.
- Fluxo: 1. `EmprestimoService.pagarParcela`; 2. busca pela tripla contrato/perfil/número; 3. calcula saldo como valorParcela-valorPago; 4. delega internamente a `pagarParcelaParcial` com esse saldo e atualiza parcela/contrato. Banco: parcelas/emprestimos.

#### E19 — POST `/parcelas/pagar-parcial`

- Controller/método: `ParcelaController.pagarParcelaParcial`; body `PagarParcelaParcialDTO {idEmprestimo,numeroParcela,valorPago:BigDecimal}`, sem `@Valid`.
- Saída vazia 204; 400 parcela inexistente, contrato QUITADO, `ParcelaPagaException`, valor <= 0, valor > saldo; `valorPago=null` pode causar NullPointerException -> handler runtime 400. Demais erros comuns.
- Fluxo: 1. `EmprestimoService.pagarParcelaParcial`; 2. busca e valida; 3. soma valorPago e define PAGO se igualdade exata, senão PARCIAL, com data atual; 4. salva e incrementa recebido do contrato, calculando saldo e QUITADO se todas PAGO. Banco: parcelas/emprestimos. Valor do body é incremento, não novo acumulado.

#### E20 — POST `/parcelas/desfazer-pagamento`

- Controller/método: `ParcelaController.desfazerPagamentoParcela`; body `DesfazerPagamentoParcelaDTO {idEmprestimo,numeroParcela}`, sem `@Valid`.
- Saída vazia 204; 400 parcela ausente ou sem valorPago positivo; demais erros comuns.
- Fluxo: 1. `ParcelaService.desfazerPagamentoParcela`; 2. busca parcela; 3. zera todo valorPago e define ATRASADO/PENDENTE pela data; 4. salva e recalcula totais/status do contrato. Não limpa `dataPagamento`. Banco: parcelas/emprestimos.

#### E21 — GET `/parcelas?status={status}`

- Controller/método: `ParcelaController.listarPorStatus`; query obrigatória `status:ParcelaStatus`; body nenhum.
- Saída `List<ParcelaResponseDTO>`, 200; demais erros comuns; enum inválido/query ausente depende do binding MVC.
- Fluxo: 1. `ParcelaService.atualizarParcelasAtrasadas(perfilId)` grava atrasos; 2. controller chama `findParcelasComEmprestimoEClientePorStatus`; 3. JPQL faz fetch de contrato/cliente e ordena vencimento ASC; 4. `toDTO` e resposta. Banco: parcelas/emprestimos/clientes. GET tem efeito de escrita.

#### E22 — GET `/parcelas/vencendo-hoje`

- Controller/método: `ParcelaController.vencendoHoje`; query/body nenhum.
- Saída `List<ParcelaResponseDTO>`, 200; demais erros comuns.
- Fluxo: 1. atualiza atrasos; 2. query `findParcelasVencendoHoje(LocalDate.now(),[PENDENTE,PARCIAL],perfilId)`; 3. ordena numeroParcela ASC; 4. mapeia DTO. Banco: parcelas/emprestimos/clientes. Vencimento hoje não é classificado atrasado pela atualização.

#### E23 — GET `/parcelas/mes-atual`

- Controller/método: `ParcelaController.parcelasDoMes`; query/body nenhum.
- Saída `List<ParcelaResponseDTO>`, 200; demais erros comuns.
- Fluxo: 1. atualiza atrasos; 2. calcula primeiro/último dia do mês atual; 3. `findParcelasDoMes` com BETWEEN inclusivo e todas as situações; 4. ordena ATRASADO, PARCIAL, PENDENTE, PAGO e vencimento, mapeia DTO. Banco: parcelas/emprestimos/clientes.

#### E24 — PATCH `/parcelas/alterar`

- Controller/método: `ParcelaController.alterarParcela`; body `AlterarParcelaDTO {idEmprestimo,numeroParcela,novaData:LocalDate,novoValor:BigDecimal}`, sem `@Valid`.
- Saída vazia 204; 400 parcela ausente, data/valor nulos, valor <=0/menor que já pago, data passada para parcela atualmente não paga; demais erros comuns.
- Fluxo: 1. `ParcelaService.alterarParcela`; 2. busca e valida inclusive status antigo; 3. altera data/valor e recalcula status; 4. salva, soma totais de todas as parcelas e atualiza status do empréstimo. Não atualiza período nem valorParcela agregado do contrato. Banco: parcelas/emprestimos.

### 6.6 Observações

#### E25 — POST `/parcelas/observacoes`

- Controller/método: `ParcelaObservacaoController.criarObservacao`; path/query nenhum.
- Body `CriarObservacaoParcelaDTO {idEmprestimo,numeroParcela,observacao:String}`, sem `@Valid`.
- Saída vazia 204; 400 texto vazio/null ou parcela inexistente; 409 integridade/limite de coluna; demais erros comuns.
- Fluxo: 1. `ParcelaObservacaoService.criarObservacao`; 2. valida texto e busca parcela com perfil; 3. trim e copia perfil da parcela; 4. salva, callback prePersist define timestamps. Banco: parcelas/parcela_observacoes.

#### E26 — GET `/parcelas/observacoes`

- Controller/método: `ParcelaObservacaoController.listarObservacoes`; path/body nenhum.
- Queries obrigatórias `idEmprestimo:Long`, `numeroParcela:Integer`.
- Saída `List<ParcelaObservacaoResponseDTO>`, 200; 400 parcela inexistente; demais erros comuns/binding.
- Fluxo: 1. `ParcelaObservacaoService.listarObservacoes`; 2. resolve parcela pela tripla; 3. `findByParcelaIdAndPerfilIdOrderByDataCriacaoDesc`; 4. mapeia id/texto/datas. Banco: parcelas/parcela_observacoes.

#### E27 — PATCH `/parcelas/observacoes`

- Controller/método: `ParcelaObservacaoController.editarObservacao`; path/query nenhum.
- Body `EditarObservacaoParcelaDTO {idObservacao:Long,observacao:String}`, sem `@Valid`.
- Saída vazia 204; 400 texto ausente/vazio ou nota não encontrada no perfil; 409 integridade; demais erros comuns.
- Fluxo: 1. `ParcelaObservacaoService.editarObservacao`; 2. valida e busca ID/perfil; 3. trim e save; 4. callback preUpdate atualiza timestamp quando houver update JPA. Banco: parcela_observacoes.

#### E28 — DELETE `/parcelas/observacoes/{idObservacao}`

- Controller/método: `ParcelaObservacaoController.removerObservacao`; path `idObservacao:Long`; query/body nenhum.
- Saída vazia 204; 400 nota não encontrada no perfil; demais erros comuns.
- Fluxo: 1. `ParcelaObservacaoService.removerObservacao`; 2. busca ID/perfil; 3. delete. Banco: parcela_observacoes; não muda os valores financeiros.

### 6.7 Relatórios e arquivos

#### E29 — GET `/relatorios/clientes/{idCliente}`

- Controller/método: `RelatorioController.historicoCliente`; path `idCliente:Long`; query/body nenhum.
- Saída real `HistoricoClienteDTO` em `ResponseEntity<?>`, 200; 400 cliente ausente; demais erros comuns.
- Fluxo: 1. `ClienteService.getClienteById`; 2. `RelatorioService.gerarHistoricoCliente`; 3. lista contratos e consulta parcelas de cada contrato; 4. agrega principal e valores/contagens conforme status. Banco: clientes/emprestimos/parcelas. Não atualiza atraso nem contabiliza PARCIAL em totalPago/totalAberto.

#### E30 — GET `/relatorios/inadimplentes`

- Controller/método: `RelatorioController.inadimplentes`; path/query/body nenhum.
- Saída `List<InadimplenteDTO>`, 200; demais erros comuns.
- Fluxo: 1. `RelatorioService.gerarRelatorioInadimplentes`; 2. busca dataVencimento < hoje e status ATRASADO/PARCIAL com fetch cliente; 3. agrupa por Cliente, soma valorParcela-valorPago e toma máximo vencimento; 4. retorna DTOs sem ordem explícita de clientes. Banco: parcelas/emprestimos/clientes. Não inclui PENDENTE vencido nem chama atualizador.

#### E31 — POST `/import`

- Controller/método: `ImportController.importar`; path nenhum; campo multipart obrigatório `file:MultipartFile` por `@RequestParam`; sem body JSON.
- Consumes `multipart/form-data`; saída `ImportacaoResultado`.
- Status: 400 arquivo null/vazio com resumo zerado; 201 ao retornar do service, inclusive com linhas ignoradas/duplicadas; 500 com resumo zerado se a chamada lança Exception; header ausente falha antes com 400 do advice. Campo multipart ausente/binding depende do MVC.
- Fluxo: 1. verifica arquivo; 2. `ImportService.importarPlanilha` -> `ImportacaoPlanilhaService.importar`; 3. lê primeira aba, valida/mapeia cada linha, resolve cliente, verifica duplicidade e grava contrato/parcelas; 4. retorna contadores/erros. Banco: clientes/emprestimos/parcelas e FKs de perfil. POI local; sem validação explícita de extensão/MIME ou perfil via PerfilService. Tratamento local mascara erros globais com resposta zerada.

#### E32 — GET `/export`

- Controller/método: `BackupController.exportar`; path/query/body nenhum.
- Saída `byte[]`, 200; MIME `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; headers `Content-Disposition: attachment; filename="backup-perfil-<perfilId>-<data>.xlsx"`, `Cache-Control: no-store`.
- Erros comuns 400/409/500 por advice; não há catch local.
- Fluxo: 1. `ExportService.exportarExcel`; 2. busca contratos por perfil com EntityGraph cliente/parcelas; 3. monta Workbook Backup ordenado por data do empréstimo; 4. serializa bytes e controller entrega download. Banco: emprestimos/clientes/parcelas. Não salva arquivo em storage e não valida perfil existente previamente.

## 7. Controllers

| Classe | Arquivo | Dependências injetadas | Métodos Java / operações |
|---|---|---|---|
| BackupController | `src/main/java/com/caio/controle_clientes/controllers/BackupController.java` | ExportService | exportar (E32) |
| ClientController | `src/main/java/com/caio/controle_clientes/controllers/ClientController.java` | ClienteService | criarCliente, deletarCliente, updateName, updateEndereco, listarClientes (E05–E09) |
| EmprestimoController | `src/main/java/com/caio/controle_clientes/controllers/EmprestimoController.java` | EmprestimoService, ClienteService | criarEmprestimo, listar, listarParcelas, deletar, listarDetalhado, refinanciar, quitar, reprogramarParcelas (E10–E17) |
| HomeController | `src/main/java/com/caio/controle_clientes/controllers/HomeController.java` | Nenhuma | home, ping (E01–E02) |
| ImportController | `src/main/java/com/caio/controle_clientes/controllers/ImportController.java` | ImportService | importar (E31), catch local de Exception |
| ParcelaController | `src/main/java/com/caio/controle_clientes/controllers/ParcelaController.java` | EmprestimoService, ParcelaRepositorio, ParcelaService | pagarParcela, pagarParcelaParcial, desfazerPagamentoParcela, listarPorStatus, vencendoHoje, parcelasDoMes, alterarParcela (E18–E24) |
| ParcelaObservacaoController | `src/main/java/com/caio/controle_clientes/controllers/ParcelaObservacaoController.java` | ParcelaObservacaoService | criarObservacao, listarObservacoes, editarObservacao, removerObservacao (E25–E28) |
| PerfilController | `src/main/java/com/caio/controle_clientes/controllers/PerfilController.java` | PerfilService | listar, criar (E03–E04) |
| RelatorioController | `src/main/java/com/caio/controle_clientes/controllers/RelatorioController.java` | RelatorioService, ClienteService | historicoCliente, inadimplentes (E29–E30) |

Nenhum controller possui autorização por método, transação explícita, integração externa ou publicação de evento. A responsabilidade predominante é binding, delegação e construção do status/resposta; as listagens de parcela incluem consulta ao repository diretamente.

## 8. Services e regras de negócio

Convenções das tabelas: `Tx` = `@Transactional` Spring de escrita; `RO` = `@Transactional(readOnly=true)`; `JTA` = `jakarta.transaction.Transactional`; `—` = sem anotação transacional própria no método/classe. Chamadas de repository ainda recebem o comportamento transacional de Spring Data quando aplicável. Não há chamadas a APIs externas nem eventos em nenhum método abaixo. As exceptions não capturadas seguem a seção 13. Construtores de DI são infraestrutura, não casos de uso.

### 8.1 ClienteService

Arquivo: `src/main/java/com/caio/controle_clientes/services/ClienteService.java`. Gerencia o cadastro e o escopo de clientes. Dependências: `ClienteRepositorio`, `EmprestimoRepositorio`, `PerfilService`.

| Método público / parâmetros -> retorno | Chamado por | Chamadas e regra | Tx / efeitos / falhas |
|---|---|---|---|
| `buscarPorNome(Long perfilId,String nome) -> Cliente` | Sem chamada ativa encontrada; menu comentado tem assinatura antiga | `findByNomeIgnoreCaseAndPerfilId`; retorna único cliente ou lança RuntimeException | —; leitura; “Cliente não encontrado!”; duplicidades de nome podem tornar resultado Optional ambíguo (inferência). |
| `criarCliente(Long perfilId,String nome,String indicador,String enderecoRua,String enderecoBairro,Integer enderecoNumero) -> void` | ClientController.criarCliente | Consulta nome/perfil, rejeita se presente; busca Perfil pelo service; monta entidade e `save` | —; insert; cliente já existe/perfil ausente; não faz trim nem validação de endereço internamente. |
| `updateNameCliente(Long perfilId,Long id,String nome) -> void` | ClientController.updateName | `getClienteById`, setter e save | —; update; cliente ausente; não reexecuta unicidade/NotBlank. |
| `updateEnderecoCliente(Long perfilId,Long id,String rua,String bairro,Integer numero) -> void` | ClientController.updateEndereco | `getClienteById`, substitui três campos e save | —; update; cliente ausente; não valida campos. |
| `deletarCliente(Long perfilId,Long clienteId) -> void` | ClientController.deletarCliente | `findByIdAndPerfilId`, `existsByClienteIdAndPerfilId`, delete apenas sem contratos | JTA; delete; cliente ausente ou “Não é possível deletar o cliente pois ele possui empréstimos cadastrados.” |
| `getClienteById(Long perfilId,Long id) -> Cliente` | ClientController via updates; EmprestimoController criar/listar/detalhes; RelatorioController.historicoCliente | Busca ID e perfil conjuntamente | —; leitura; “Cliente não encontrado”. |
| `listarTodos(Long perfilId) -> List<Cliente>` | ClientController.listarClientes | `findAllByPerfilId`; não consulta Perfil | —; leitura; pode retornar lista vazia. |

Duplicidade de nome de cliente é uma consulta da criação, não uma constraint declarada na entidade. A importação pode criar clientes sem endereço porque não chama este service e a entidade permite nulls nessas colunas.

### 8.2 PerfilService e PerfilHeaderService

Arquivos: `src/main/java/com/caio/controle_clientes/services/perfil/PerfilService.java` e `src/main/java/com/caio/controle_clientes/services/perfil/PerfilHeaderService.java`.

`PerfilService` depende de `PerfilRepositorio`; nenhum método tem transação explícita própria.

- `listarTodos() -> List<PerfilResponse>`: chamado pelo PerfilController; `findAll` e mapeamento `PerfilResponse.fromEntity`, sem ordenar.
- `criar(CriarPerfilRequest request) -> PerfilResponse`: chamado pelo PerfilController; trim do nome, `findByNomeIgnoreCase`, RuntimeException se já houver nome; cria/salva Perfil e mapeia. `@NotBlank` é aplicado pelo controller, não pelo service. `request`/nome nulos em chamada direta não recebem proteção própria. Constraint única do banco também pode falhar em concorrência.
- `buscarPorId(Long perfilId) -> Perfil`: chamado por ClienteService.criarCliente, EmprestimoService.criarEmprestimo e PerfilHeaderService; `findById`, RuntimeException “Perfil não encontrado”.

`PerfilHeaderService` depende de PerfilService. `resolverPerfil(Long perfilId) -> Perfil` rejeita null com “Cabeçalho X-Perfil-Id não enviado.” e delega a buscarPorId. Não foi encontrada utilização ativa; não é filter/interceptor e não executa automaticamente em cada requisição apenas por ser `@Service`.

### 8.3 CalculadoraEmprestimoService

Arquivo: `src/main/java/com/caio/controle_clientes/services/emprestimo/CalculadoraEmprestimoService.java`. Sem dependências injetadas ou transações; não persiste dados. Chamado por EmprestimoService na criação.

`calcularParcela(BigDecimal valorEmprestado,BigDecimal taxaJuros,int quantidadeParcelas) -> BigDecimal`:

1. Converte percentual em taxa `i=taxaJuros/100` com escala 10, HALF_UP.
2. Para `i=0`, retorna `principal/n` com duas casas e HALF_UP.
3. Caso contrário, calcula `principal*i / (1 - 1/(1+i)^n)`, com potência BigDecimal e inverso em escala 10; prestação com duas casas.

A identificação como fórmula de prestação constante/Price é uma interpretação matemática dessa expressão. Não há conversão de taxa por frequência: a mesma taxa de entrada é usada para diário/semanal/quinzenal/mensal. Não há validação explícita de n>0, taxa>=0 ou principal>0; nulls/divisão inválida/potência podem produzir exceptions runtime. Não há ajuste final para diferenças de centavos.

`gerarParcelas(Emprestimo emprestimo) -> List<Parcelas>`:

1. Rejeita frequência nula; calcula valor constante e taxa.
2. Primeiro vencimento é calculado a partir de `LocalDate.now()` (não da data do contrato): +1 dia, +1 semana, +15 dias ou +1 mês.
3. Para números 1..n, monta parcela com mesmo valor, PENDENTE, valorPago=0, empréstimo e perfil.
4. Datas subsequentes usam índice desde o primeiro vencimento; mensal preserva o dia daquele primeiro vencimento, limitado ao último dia do mês destino. Se o primeiro vencimento já perdeu o dia por ser um mês curto, é esse dia reduzido que vira base.

O loop calcula juros, amortização e saldo devedor em variáveis locais, porém esses números não são persistidos nem retornados por parcela. Todos os dias, incluindo domingo, são considerados na frequência diária. Helpers privados: validarFrequencia, criarParcela, calcularPrimeiroVencimento, calcularVencimentoPorIndice, calcularVencimentoMensal.

### 8.4 EmprestimoService

Arquivo: `src/main/java/com/caio/controle_clientes/services/emprestimo/EmprestimoService.java`. Dependências: `PerfilService`, `EmprestimoRepositorio`, `ParcelaRepositorio`, `CalculadoraEmprestimoService`. Concentra criação, recebimento, encerramento e reprogramação.

| Método público / parâmetros -> retorno | Chamado por | Fluxo principal / repositories | Tx / falhas e efeitos |
|---|---|---|---|
| `criarEmprestimo(Long perfilId,Cliente cliente,BigDecimal valorEmprestado,int quantidadeParcelas,BigDecimal jurosCobrado,FormaPagamento formaPagamento,FrequenciaPagamento frequenciaPagamento,TipoContrato tipoContrato) -> Emprestimo` | EmprestimoController.criarEmprestimo | Valida frequência/tipo; busca Perfil; verifica perfil do Cliente; helper montarEmprestimo calcula prestação/total; `save` contrato; calculadora gera; `saveAll` parcelas; define período; `save` contrato | Tx; inserts/update; runtime de entrada, vínculo, cálculo ou integridade. |
| `pagarParcela(Long perfilId,Long idEmprestimo,Integer numeroParcela) -> void` | ParcelaController.pagarParcela | `buscarParcela` -> saldo restante -> chama `pagarParcelaParcial` | Tx; quita saldo dessa parcela; mesmas falhas do parcial. |
| `pagarParcelaParcial(Long perfilId,Long idEmprestimo,Integer numeroParcela,BigDecimal valorPago) -> void` | ParcelaController.pagarParcelaParcial; chamada interna pagarParcela | Busca parcela; valida; acumula valorPago; decide PAGO/PARCIAL; data=hoje; salva; helper atualizarFinanceiroEmprestimo | Tx; update parcela/contrato; contrato QUITADO, parcela PAGO, valor<=0/excesso/null e ausência da parcela. |
| `listarEmprestimosCliente(Long perfilId,Cliente cliente) -> List<Emprestimo>` | EmprestimoController.listar | Valida perfil do cliente; `findAllByClienteAndPerfilId` | RO; entidades; RuntimeException se vínculo incompatível. |
| `listarParcelas(Long perfilId,Long idEmprestimo) -> List<Parcelas>` | Nenhuma chamada ativa encontrada; referência antiga comentada | `buscarComParcelas` e getParcelas | RO; contrato ausente; não valida idCliente separado. |
| `listarParcelasDoCliente(Long perfilId,Long idCliente,Long idEmprestimo) -> List<Parcelas>` | EmprestimoController.listarParcelas | `buscarComParcelas`; valida cliente do contrato | RO; contrato ausente/cliente incompatível. |
| `deletarEmprestimo(Long perfilId,Long idCliente,Long idEmprestimo) -> void` | EmprestimoController.deletar | Busca com parcelas; verifica cliente; rejeita qualquer PAGO/PARCIAL; `deleteAll` parcelas e `delete` contrato | Tx; deletes e cascades; ausência/vínculo/status. |
| `listarDetalhado(Long perfilId,Cliente cliente) -> List<EmprestimoDetalhadoDTO>` | EmprestimoController.listarDetalhado | Valida perfil; `findAllByClienteAndPerfilId`; helper toDetalhadoDTO | RO; leitura; enums nulos na `.name()` podem falhar. |
| `refinanciarEmprestimo(Long perfilId,Long idCliente,Long idEmprestimo) -> void` | EmprestimoController.refinanciar | `finalizarEmprestimo` com REFINANCIADO e validarRefinanciado=true | Tx; updates; bloqueia QUITADO/REFINANCIADO. |
| `quitarEmprestimo(Long perfilId,Long idCliente,Long idEmprestimo) -> void` | EmprestimoController.quitar | `finalizarEmprestimo` com QUITADO e validarRefinanciado=false | Tx; updates; bloqueia apenas QUITADO além de ausência/vínculo. |
| `reprogramarParcelas(Long perfilId,Long idCliente,Long idEmprestimo,LocalDate novaDataInicial,BigDecimal novoValorParcela) -> void` | EmprestimoController.reprogramarParcelas | Busca com parcelas; filtra não PAGO; datas/valor/status; `saveAll`; recalcula totais/período e `save` contrato | Tx; updates; validações de data presente, valor positivo, abertas existentes e valor>=pago. |

**Criação e estado inicial:** `montarEmprestimo` grava dataDoEmprestimo=hoje, EM_ABERTO, prestação calculada, valorTotal=prestação*n em escala 2/HALF_UP, recebido=0 e aReceber=total. FormaPagamento não é explicitamente validada. `atualizarPeriodoPagamento` ordena por numeroParcela e usa a data da primeira/última (não mínimo/máximo cronológico).

**Validação de recebimento:** `validarPagamentoParcela` bloqueia status de contrato exatamente QUITADO; não compara REFINANCIADO/QUITADO_TERCEIRO. Se parcela PAGO, lança `ParcelaPagaException`. Usa `valorPago.compareTo(0)` sem null guard e exige incremento <= valorParcela-valorPagoAtual. Valores antigos nulos são tratados como zero por `safe`. Pagamento parcial em parcela atrasada redefine status para PARCIAL mesmo com vencimento passado; um GET posterior de listagem pode voltar a ATRASADO.

**Atualização financeira incremental:** `atualizarFinanceiroEmprestimo` soma incremento a `emprestimo.valorRecebido` existente e calcula aReceber pelo total armazenado. Não reconstrói recebido a partir de todas as parcelas neste caminho. Se todas estão PAGO, define QUITADO; não força EM_ABERTO nos demais casos. **Inferência:** totais importados inconsistentes podem permanecer inconsistentes ao receber um incremento.

**Encerramento compartilhado:** `finalizarEmprestimo` busca contrato com parcelas e valida cliente. Para cada parcela aplicável, `falta=max(valorParcela-valorPago,0)`; soma falta ao valorPago, marca PAGO e dataPagamento=hoje. No refinanciamento pula parcelas já PAGO; na quitação também reatribui suas datas. Soma valorPago de todas, aReceber=total armazenado-recebido, grava statusFinal. Não recalcula valorTotal, não aplica desconto, não solicita transferência e não guarda identificação de terceiro. Um contrato com status QUITADO_TERCEIRO vindo da importação não recebe bloqueio específico.

**Reprogramação:** seleciona todas com status diferente de PAGO (incluindo ATRASADO/PARCIAL), ordena número e usa índice sequencial de abertas a partir da novaDataInicial. Não gera parcelas extras e não altera quantidadeParcelas ou principal/taxa. Valor é igual em todas as abertas e não pode ficar abaixo do acumulado pago em cada uma. Datas podem ser anteriores a hoje. Mensal ancora no dia da novaDataInicial; diário não pula domingo. Status é PENDENTE/ATRASADO se pago=0; PAGO se pago>=novoValor; senão PARCIAL/ATRASADO pela data. Não altera dataPagamento nesses branches.

`recalcularTotaisEPeriodoEmprestimo`: total=sum(valorParcela), recebido=sum(valorPago), aReceber=total-recebido. Atualiza o valorParcela do contrato para o primeiro valor não nulo de parcela não paga na ordem da coleção, ou mantém o antigo se não houver. Todas pagas -> QUITADO; se havia QUITADO mas deixou de estar tudo pago -> EM_ABERTO; outros status ficam preservados. Atualiza início/fim por número. Essa recomposição difere do caminho individual em ParcelaService.

**DTO detalhado:** conta PAGO; pendentes por `isPendente()` incluem PENDENTE/PARCIAL; atrasadas por `isAtrasada()` incluem ATRASADO. `.name()` exige formaPagamento/frequencia/tipo não nulos. Não recalcula atrasos previamente.

**Resolução de dados:** `buscarParcela` usa ID do contrato + ID do perfil da parcela + número. `buscarEmprestimoComParcelas` usa ID/perfil do contrato e JPQL com inner fetch. Validação de cliente compara IDs. `buscarEmprestimoPorId` existe, mas não foram encontradas chamadas ativas. Nenhum helper tem propagação transacional própria. A chamada interna pagarParcela -> pagarParcelaParcial não atravessa outro proxy; a transação do método público pagarParcela já abrange o fluxo.

### 8.5 ParcelaService

Arquivo: `src/main/java/com/caio/controle_clientes/services/parcela/ParcelaService.java`. Dependências: `ParcelaRepositorio`, `EmprestimoRepositorio`.

| Método público / parâmetros -> retorno | Chamado por | Regra / chamadas | Tx e efeitos |
|---|---|---|---|
| `atualizarParcelasAtrasadas(Long perfilId) -> void` | ParcelaController.listarPorStatus, vencendoHoje, parcelasDoMes | Duas `findByStatusAndPerfilIdAndDataVencimentoBefore`, PENDENTE e PARCIAL; muda ambas para ATRASADO; `saveAll` se houver | Tx; escreve status; não muda pago/dataPagamento/totais do contrato. |
| `desfazerPagamentoParcela(Long perfilId,Long idEmprestimo,Integer numeroParcela) -> void` | ParcelaController.desfazerPagamentoParcela | Busca tripla, exige valorPago>0; zera; status pela data; save; recalcula contrato | Tx; desfaz acumulado inteiro, não só última entrada; não limpa dataPagamento. |
| `alterarParcela(Long perfilId,Long idEmprestimo,Integer numeroParcela,LocalDate novaData,BigDecimal novoValor) -> void` | ParcelaController.alterarParcela | Busca; exige data/valor, valor>0 e >=pago; restringe data pela situação anterior; muda data/valor/status; save/recalcula | Tx; altera parcela e totais, sem recalcular período/prestação agregada. |
| `alterarDataVencimento(Long perfilId,Long idEmprestimo,Integer numeroParcela,LocalDate novaData) -> void` | Sem chamada ativa encontrada | Busca; data obrigatória e regra da alteração; atualiza data/status; save/recalcula contrato | Tx; não há endpoint próprio que o exponha. |
| `toDTO(Parcelas parcela) -> ParcelaResponseDTO` | Três listagens de ParcelaController | Copia ID do empréstimo, nome do cliente, número, valor/pago/status/vencimento | —; apenas leitura; referências nulas podem causar runtime. |

Todos os mutadores usam `buscarParcela` com `findByEmprestimoIdAndPerfilIdAndNumeroParcela`; ausência -> RuntimeException “Parcela não encontrada”. `safe` trata BigDecimal nulo como zero. Demais RuntimeExceptions: parcela sem pagamento para desfazer, data/valor obrigatórios, valor positivo/limite do já pago e data inválida para parcela não paga.

`validarDataAlteracao` observa o status **antes** da mudança: se não é PAGO, proíbe novaData antes de hoje. Se é PAGO, permite data passada, inclusive quando aumentar novoValor fará a parcela deixar de estar paga. `atualizarStatusParcelaAposAlteracao`: pago=0 -> ATRASADO/PENDENTE; pago>=valor -> PAGO; restante -> ATRASADO/PARCIAL. Não faz ajuste de dataPagamento.

`recalcularTotaisEmprestimo` soma todos os valores/pagos, recalcula total/recebido/aReceber; todas PAGO -> QUITADO; status antigo QUITADO e alguma aberta -> EM_ABERTO. Não atualiza início/fim, quantidadeParcelas, taxa, valorEmprestado ou valorParcela do contrato. Se contrato REFINANCIADO e estorno abre parcela, permanece REFINANCIADO; se depois todas ficarem PAGO pela recomposição, pode passar a QUITADO. Não há proibição explícita de estorno/alteração por status do contrato.

### 8.6 ParcelaObservacaoService

Arquivo: `src/main/java/com/caio/controle_clientes/services/parcela/ParcelaObservacaoService.java`. Dependências: `ParcelaObservacaoRepositorio`, `ParcelaRepositorio`. Chamado exclusivamente pelos quatro métodos homônimos de ParcelaObservacaoController.

| Método público / parâmetros -> retorno | Chamadas / regra | Tx / falhas |
|---|---|---|
| `criarObservacao(Long perfilId,Long idEmprestimo,Integer numeroParcela,String textoObservacao) -> void` | Texto null/blank rejeitado; resolve parcela por tripla; trim; associa parcela e perfil dela; save | Tx; RuntimeException texto obrigatório/parcela ausente; integridade (texto > coluna 1000 não tem limite Java). |
| `listarObservacoes(Long perfilId,Long idEmprestimo,Integer numeroParcela) -> List<ParcelaObservacaoResponseDTO>` | Resolve parcela; busca por parcelaID/perfil ordenada dataCriacao DESC; mapper privado toDTO | RO; parcela ausente; nenhuma alteração financeira. |
| `editarObservacao(Long perfilId,Long idObservacao,String novoTexto) -> void` | Texto validado/trim; busca observação por ID/perfil; save | Tx; texto obrigatório/observação ausente/integridade; timestamp via callback. |
| `removerObservacao(Long perfilId,Long idObservacao) -> void` | Busca ID/perfil; delete | Tx; observação ausente; remove somente nota. |

Nenhuma verificação de autor, senha, status de parcela ou status do contrato. A proteção observada é a consulta com perfil. Mapper não devolve entidade, parcela ou perfil, evitando ciclos nesse endpoint de notas.

### 8.7 RelatorioService

Arquivo: `src/main/java/com/caio/controle_clientes/services/RelatorioService.java`. Dependências: `EmprestimoRepositorio`, `ParcelaRepositorio`. Métodos sem transação explícita; chamados por RelatorioController.

`gerarHistoricoCliente(Long perfilId,Cliente cliente) -> HistoricoClienteDTO`: consulta contratos por Cliente/perfil (EntityGraph carrega parcelas), depois consulta novamente `findByEmprestimoAndPerfilId` para cada contrato. Soma **valorEmprestado** dos contratos. Para PAGO soma valorParcela em totalPago e conta; para PENDENTE soma valorParcela em totalAberto e conta parcelasAVencer; para ATRASADO soma valorParcela em totalAberto e conta atrasadas. PARCIAL não participa de nenhum desses três branches, mas participa de totalParcelas. Não usa valorPago para compor totalPago e não subtrai valorPago das atrasadas. Não atualiza atrasos e não verifica datas para classificar PENDENTE. Há consulta por contrato adicional, mesmo com parcelas previamente carregadas. **Inferência:** o número de consultas cresce com o número de contratos; não medido.

`gerarRelatorioInadimplentes(Long perfilId) -> List<InadimplenteDTO>`: consulta vencimento < hoje + status em ATRASADO/PARCIAL; sem resultados retorna `List.of()`. Agrupa por entidade Cliente, soma `valorParcela - (valorPago ou 0)`, conta registros e escolhe maior dataVencimento como ultimoVencimento. Não limita saldo a zero (diferentemente do exportador), não retorna ID do cliente, não ordena clientes explicitamente e não usa atualização de atrasos. PENDENTE vencido só será incluído depois de virar ATRASADO por outro fluxo. Nenhuma validação de Perfil prévia; nulls de campos necessários podem gerar runtime.

### 8.8 ImportService e ImportacaoPlanilhaService

Arquivos: `src/main/java/com/caio/controle_clientes/services/ImportService.java` e `src/main/java/com/caio/controle_clientes/services/importService/ImportacaoPlanilhaService.java`.

`ImportService` injeta somente ImportacaoPlanilhaService. `importarPlanilha(Long perfilId,InputStream file) -> ImportacaoResultado throws Exception` é chamado por ImportController e delega diretamente a `importar`, sem transação própria.

`ImportacaoPlanilhaService` injeta ClienteRepositorio (`clienteRepository`), EmprestimoRepositorio (`emprestimoRepository`), ParcelaRepositorio (`parcelaRepository`), ImportacaoLinhaMapper, ImportacaoValidator e ImportacaoParcelaFactory.

Único método público de negócio: `importar(Long perfilId,InputStream file) -> ImportacaoResultado throws Exception`, `@Transactional` Spring de escrita. Fluxo completo:

1. Cria lista de erros e cache HashMap de clientes por chave normalizada; contadores zerados.
2. `WorkbookFactory.create(file)` em try-with-resources; acessa **somente primeira aba**; cria evaluator/formatter.
3. Pula a primeira Row iterada como cabeçalho, sem reconhecer títulos ou exigir número de linha zero. Linhas vazias entre as posições 0..18 são ignoradas sem entrar em totalLinhas.
4. Cada linha não vazia incrementa totalLinhas; mapeia DTO e valida. Primeiro erro de validação -> linhasIgnoradas++, erro com número físico `row.getRowNum()+1`, log warn e continue.
5. `obterOuCriarCliente`: chave NFD sem acentos + trim + uppercase; cache local consultado primeiro. No banco usa nome ignore-case e perfil; se não existe, instancia `Perfil` com apenas ID, cria Cliente com nome/indicador e save. Não busca perfil por PerfilService e não cria perfil no banco. Cliente existente não tem indicador/endereço atualizado.
6. `emprestimoJaExiste`: se cliente sem ID, false; senão chama exists com **clienteId, perfilId, dataDoEmprestimo, valorEmprestado, inicioPagamento, quantidadeParcelas**. Se true, linhasDuplicadas++, adiciona motivo em erros e continua. Taxa, frequência, tipo, total, parcelasPagas e fim não integram essa chave. Não há constraint composta desse contrato na entidade/dump.
7. `criarEmprestimo` instancia outro Perfil apenas com ID, copia todos os campos financeiros/datas/status do DTO, salva contrato. Não calcula prestação pela CalculadoraEmprestimoService.
8. Factory gera parcelas; orquestrador preenche perfil e empréstimo de todas; `saveAll`; linhasImportadas++.
9. Exception dentro do processamento da linha -> linhasIgnoradas++, motivo `Erro inesperado: <mensagem>`, log error e continua.
10. Ao terminar, fecha Workbook, monta contadores/erros, registra resumo no logger e retorna.

**Efeitos e limites transacionais:** não há transação independente por linha, savepoint ou `REQUIRES_NEW`. Capturar exceptions não garante commit das linhas anteriores nem rollback apenas daquela linha. **Inferência baseada na estrutura:** falha de persistência pode marcar a transação como rollback-only, erro pode aparecer no flush/commit após o loop, e incrementos do resumo não são prova de persistência. `@Transactional` não declara `rollbackFor` para checked exceptions; não se deve afirmar atomicidade integral para todo tipo de falha. O controller pega a Exception da chamada/proxy e entrega resumo zerado em 500. Linhas com validação ruim, sem falha de persistência, podem coexistir com 201 e erros no resumo.

**Normalização:** o cache remove acentos, mas a query de nome no banco só usa ignore-case. Assim, nomes que diferem por acento podem cair no mesmo cliente no cache de uma importação embora a busca do banco tenha outra equivalência. O mapper remove conteúdo após o primeiro `(` do nome. Essas regras não existem no cadastro HTTP normal.

**Coerência financeira:** recebido/aReceber/total do contrato são copiados da planilha e não reconstituídos após gerar parcelas. Parcelas pagas são inferidas pela contagem: as primeiras N são PAGO com valorPago=valorParcela, restantes PENDENTE/0. Não existe representação de pagamento parcial por linha de contrato. Status global do contrato não força as parcelas a PAGO se a contagem da planilha disser diferente; datas passadas restantes também ficam inicialmente PENDENTE. fimPgto da planilha é armazenado, não recalculado da última parcela gerada. Não são importadas observações ou endereços.

### 8.9 Auxiliares de importação

Todos são `@Component`, sem transação própria. Arquivos em `src/main/java/com/caio/controle_clientes/services/importService/`.

#### ImportacaoCellReader.java

Sem dependências injetadas. Chamado por ImportacaoLinhaMapper. Métodos públicos recebem `Cell`, `FormulaEvaluator`, `DataFormatter`:

| Método -> retorno | Conversão e comportamento |
|---|---|
| `getMoneyValue -> BigDecimal` | Null -> 0. Se evaluator retorna numeric, BigDecimal.valueOf(double). Senão remove R$, espaços e todos os pontos; troca vírgula por ponto e parseia. Exception -> debug e 0. Texto com ponto decimal fora do padrão brasileiro pode ter interpretação diferente. |
| `getPercentValue -> BigDecimal` | Null -> 0. Numeric ou texto sem %, vírgula->ponto; se 0<valor<1, multiplica por 100; retorna Math.round como BigDecimal inteiro. Exception -> debug/0. Perde casas decimais; valor exatamente 1 não recebe multiplicação. |
| `getString -> String` | Null -> string vazia; formatter com evaluator e trim. Não há catch local. |
| `getIntegerValue -> Integer` | Null -> 0; número arredondado Math.round/cast int; texto remove pontos, troca vírgula e parseia/arredonda. Exception -> debug/0. |
| `getLocalDate -> LocalDate` | Null -> null. Numeric formatado como data: Date -> Instant -> ZoneId.systemDefault -> LocalDate. Fórmula numeric: DateUtil.getLocalDateTime. Texto com / usa dd/MM/yyyy, demais usa LocalDate.parse. Exception -> debug/null. |

Essas conversões frequentemente transformam célula inválida em zero/null. O validator posterior rejeita alguns, mas zero é aceito para taxa e campos financeiros agregados. Não há enumeração de erros por célula; a validação informa somente primeiro problema da linha.

#### ImportacaoLinhaMapper.java

Injeta ImportacaoCellReader. `mapear(Row row,FormulaEvaluator evaluator,DataFormatter formatter) -> LinhaImportacaoDTO` é chamado pelo importador; usa posições fixas descritas abaixo. `isRowEmpty(Row row,FormulaEvaluator evaluator,DataFormatter formatter) -> boolean` examina células de 0 a 18 e retorna true se nenhum texto formatado não vazio; chamado antes do try por linha.

| Coluna Excel (índice Java) | Conteúdo mapeado |
|---|---|
| A (0) | dataEmprestimo |
| B (1) | nomeClienteOriginal; nomeCliente = parte anterior ao primeiro `(`, trim |
| C (2) | nomeIndicador |
| D (3) | formaPagamento |
| E (4) | tipoContrato |
| F (5) | frequenciaPagamento |
| G (6) | valorEmprestado |
| H (7) | totalParcelas |
| I (8) | parcelasPagas |
| J (9) | Não lida |
| K (10) | valorAReceber |
| L (11) | valorRecebido |
| M (12) | Não lida |
| N (13) | valorTotalEmprestimos |
| O (14) | valorParcela |
| P (15) | taxaJurosPercentual |
| Q (16) | inicioPgto |
| R (17) | fimPgto |
| S (18) | situacao |

Helpers privados: frequência remove acentos/trim/uppercase e reconhece prefixos DIA, SEM, QUI, MEN/MES, fallback MENSAL; contrato reconhece DIG/FIS, fallback FISICO; forma usa enum.valueOf de trim/uppercase, fallback DINHEIRO; situação reconhece QUITADO, REFINANCIADO, QUITADO TERCEIRO/QUITADO_TERCEIRO, fallback EM_ABERTO. Valores desconhecidos não são rejeitados, tornam-se defaults. Não valida nomes/títulos do cabeçalho.

#### ImportacaoValidator.java

Sem DI. `validar(LinhaImportacaoDTO dto) -> String`: chamado pelo importador; retorna **primeiro** erro ou null. Ordem: dataEmprestimo presente; nome original não blank; nome limpo não blank; valorEmprestado>0; totalParcelas>0; parcelasPagas>=0 e <=total; valorParcela>0; inicioPgto presente; fimPgto se presente não pode ser anterior ao início; taxa se presente >=0; valorRecebido>=0; valorAReceber>=0; valorTotalEmprestimos>=0. Não exige fimPgto nem valores financeiros totais positivos; não cruza soma de parcelas com total/recebido; não valida perfil, endereço, limite de taxa/quantidade ou consistência de status. Não lança exception para falha de regra normal; produz mensagem que pode incluir valores de negócio.

#### ImportacaoParcelaFactory.java

Sem DI. `gerar(Emprestimo emp,Integer pagas) -> List<Parcelas>`: chamado pelo importador; começa em inicioPagamento e usa frequência do contrato ou MENSAL se null. Para DIARIO, ajusta domingo inicial para segunda e pula domingos subsequentes. Nos demais incrementa semana/15 dias/mês sequencialmente. Mensal usa a data **da parcela anterior**; dia pode permanecer reduzido após mês curto, diferentemente da calculadora e da reprogramação. Primeiras `pagas` recebem PAGO, valorPago=valorParcela, dataPagamento=dataVencimento; as restantes PENDENTE e zero. Não verifica se vencimento passou. Factory preenche empréstimo, não perfil: o orquestrador o completa. Não persiste. Nulls/quantidades inválidas em uso direto podem gerar runtime; a importação passa por validator antes.

### 8.10 ExportService

Arquivo: `src/main/java/com/caio/controle_clientes/services/ExportService.java`. Injeta EmprestimoRepositorio. `exportarExcel(Long perfilId) -> byte[] throws Exception`, chamado por BackupController; sem transação explícita. Usa `findAllByPerfilId` com EntityGraph cliente/parcelas.

Ordena contratos por dataDoEmprestimo crescente, null por último. Cria XSSFWorkbook e ByteArrayOutputStream em try-with-resources, aba `Backup`, cabeçalho em negrito/cinza/borda, moeda R$, percentuais com duas casas e datas dd/mm/yyyy, autosize. Uma linha por contrato. Helpers privados tratam BigDecimal null como 0, int null como 0, string null como vazia. Dinheiro é escrito como double; taxa como taxaDeJuros/100 nas duas colunas percentuais.

| Índice/coluna | Título / origem |
|---|---|
| 0/A | Data empréstimo / dataDoEmprestimo |
| 1/B | Cliente / cliente.nome |
| 2/C | Indicação / cliente.nomeIndicador |
| 3/D | Forma Pgto / formaPagamento.name |
| 4/E | Pagamento / frequência como Diário, Semanal, Quinzenal, Mensal; fallback Mensal |
| 5/F | Valor empréstimo / valorEmprestado |
| 6/G | Qtde parcelas / quantidadeParcelas |
| 7/H | Qtde parcelas pagas / count status PAGO |
| 8/I | Qtde parcelas a vencer / max(0,quantidadeParcelas-countPAGO), inclui atrasadas/parciais |
| 9/J | Valor a receber / soma max(valorParcela-valorPago,0); fallback campo do contrato somente se coleção null |
| 10/K | Valor recebido / soma valorPago; coleção null ->0 |
| 11/L | Juros cobrado / taxaDeJuros/100, não valor monetário de juros |
| 12/M | Valor total empréstimos / campo valorTotalEmprestimo |
| 13/N | Valor parcela / campo valorParcela do contrato |
| 14/O | Tx juros / mesma taxaDeJuros/100 da coluna L |
| 15/P | Inicio pgto / inicioPagamento |
| 16/Q | Final pgto / finalPagamento |
| 17/R | Situação do empréstimo / enum.name |

Não exporta tipoContrato, IDs, perfil completo, endereços, criadoEm, detalhes individuais de parcelas, notas ou datas reais de cada recebimento. O termo backup utilizado na UI/controller não implica cópia integral do banco. **Fato do layout:** a importação espera E=tipoContrato, F=frequência e S=situação, enquanto exportação grava E=frequência, F=principal e termina em R. **Inferência direta:** exportar e reimportar sem transformar colunas não constitui round-trip válido. Não houve ensaio com arquivo.

### 8.11 Principal

Arquivo: `src/main/java/com/caio/controle_clientes/principal/Principal.java`. Bean `@Component`, sem DI ativa. Único método público ativo `showMenu() -> void` imprime `API RODANDO!` no stdout; não foi encontrada chamada ativa. Centenas de linhas de menu Scanner estão comentadas e usam assinaturas antigas sem perfil; não fazem parte do fluxo executável. `@Component` faz a classe ser registrada, mas não invoca showMenu sozinho.

## 9. Repositories

Todos estão em `src/main/java/com/caio/controle_clientes/repository/`, são interfaces Spring Data `JpaRepository<Entidade,Long>` e recebem operações herdadas como findAll/findById/save/saveAll/delete/deleteAll. Sem implementation Java própria, SQL nativo, procedure, Specification, projection ou DAO manual identificado. A geração das consultas derivadas é responsabilidade do framework.

### 9.1 ClienteRepositorio.java

| Método | Consulta / utilização |
|---|---|
| findByNomeIgnoreCaseAndPerfilId | Nome sem diferenciar caixa + perfil.id; Optional. ClienteService.buscarPorNome/criarCliente e importação. Não remove acento nem espaços. |
| findByIdAndPerfilId | ID + perfil.id; Optional. ClienteService.getClienteById/deletarCliente. |
| findAllByPerfilId | Lista pelo perfil; ClienteService.listarTodos. Sem ORDER BY. |
| buscarClienteComEmprestimos | JPQL `SELECT c FROM Cliente c LEFT JOIN FETCH c.emprestimos WHERE c.id=:id AND c.perfil.id=:perfilId`; mantém clientes sem contratos. Não foram encontradas chamadas ativas. |

### 9.2 EmprestimoRepositorio.java

| Método | Consulta / utilização |
|---|---|
| findAllByPerfilId | Lista por perfil; EntityGraph `cliente`, `parcelas`; ExportService. |
| findAllByClienteAndPerfilId | Cliente + perfil; EntityGraph `cliente`, `parcelas`; EmprestimoService.listarEmprestimosCliente/listarDetalhado e RelatorioService. |
| findByClienteAndPerfilId | Mesmo filtro derivado, sem EntityGraph; sem chamadas ativas encontradas. |
| findByIdAndPerfilId | ID + perfil, Optional; helper buscarEmprestimoPorId sem chamadas ativas. |
| existsByClienteIdAndPerfilId | Existência de qualquer contrato; ClienteService.deletarCliente. |
| existsByClienteIdAndPerfilIdAndDataDoEmprestimoAndValorEmprestadoAndInicioPagamentoAndQuantidadeParcelas | Todos os seis predicados com AND; importador evita duplicidade por essa chave; parâmetros: Long clienteId/perfilId, LocalDate dataDoEmprestimo/inicioPagamento, BigDecimal valorEmprestado, Integer quantidadeParcelas. |
| buscarComParcelas | JPQL `SELECT e FROM Emprestimo e JOIN FETCH e.parcelas WHERE e.id=:id AND e.perfil.id=:perfilId`; helper principal de consultas/mutações de contrato. |

O fetch de `buscarComParcelas` é **inner join**, não left join. **Inferência do JPQL:** contrato sem parcela não produz resultado, podendo gerar “Empréstimo não encontrado” antes do branch que verifica coleção vazia. Nenhuma consulta aqui garante ordem de parcelas. Não há `@Lock` ou `@Version`.

### 9.3 ParcelaRepositorio.java

| Método / tipo | Predicados, fetch, ordem e chamadores |
|---|---|
| findByIdAndPerfilId / derivada Optional | ID e perfil; sem chamadas ativas encontradas. |
| findByEmprestimoIdAndPerfilIdAndNumeroParcela / derivada Optional | empréstimo.id, perfil.id da parcela, número; EmprestimoService, ParcelaService e ParcelaObservacaoService. |
| findParcelasComEmprestimoEClientePorStatus / JPQL | `p JOIN FETCH p.emprestimo e JOIN FETCH e.cliente c`; p.status=:status AND p.perfil.id=:perfilId; ORDER BY p.dataVencimento ASC; ParcelaController.listarPorStatus. |
| findByStatusAndPerfilIdAndDataVencimentoBefore / derivada List | status, perfil, dataVencimento < data; duas consultas de ParcelaService.atualizarParcelasAtrasadas. |
| findParcelasEmAbertoVencidas / JPQL | mesmos fetches; dataVencimento<:data AND status IN :statuses AND perfil; ORDER BY vencimento ASC; não foram encontradas chamadas ativas. |
| findParcelasVencendoHoje / JPQL | mesmos fetches; dataVencimento=:data AND status IN :statuses AND perfil; ORDER BY numeroParcela ASC; ParcelaController.vencendoHoje, com PENDENTE/PARCIAL. |
| findParcelasDoMes / JPQL | mesmos fetches; vencimento BETWEEN :inicio AND :fim AND perfil; ORDER BY CASE status 'ATRASADO'=1, 'PARCIAL'=2, 'PENDENTE'=3, 'PAGO'=4, depois vencimento ASC; ParcelaController.parcelasDoMes. |
| findByEmprestimoAndPerfilId / derivada List | entidade Emprestimo e perfil da parcela; RelatorioService.gerarHistoricoCliente. |
| findParcelasAtrasadasComCliente / JPQL | mesmos fetches; vencimento<:hoje AND status IN :statuses AND perfil; ORDER BY vencimento ASC; RelatorioService.gerarRelatorioInadimplentes com ATRASADO/PARCIAL. |

Queries por parcela filtram `p.perfil.id`, não repetem a igualdade ao perfil do empréstimo/cliente. Os caminhos normais preenchem os mesmos perfis, mas FKs independentes não garantem essa igualdade em dados inseridos fora desses caminhos. Os inner joins de cliente excluem linhas sem cliente vinculado. O CASE usa literais textuais dos nomes do enum; validação efetiva dessa JPQL pelo provedor não foi executada.

### 9.4 ParcelaObservacaoRepositorio.java

- `findByParcelaIdAndPerfilIdOrderByDataCriacaoDesc(Long parcelaId,Long perfilId) -> List<ParcelaObservacao>`: lista de notas por parcela/perfil, mais recentes primeiro; ParcelaObservacaoService.listarObservacoes.
- `findByIdAndPerfilId(Long id,Long perfilId) -> Optional<ParcelaObservacao>`: edição/exclusão pelo mesmo service.

### 9.5 PerfilRepositorio.java

`findByNomeIgnoreCase(String nome) -> Optional<Perfil>`: PerfilService.criar verifica duplicidade. findAll/findById/save são herdados. Unicidade no banco vem da entidade/dump; a consulta ignore-case e a constraint de nome não são a mesma definição de equivalência.

## 10. Banco e entidades

Datasource PostgreSQL único; nenhum schema explícito nas entidades. Tabelas anotadas: `clientes`, `emprestimos`, `parcelas`, `parcela_observacoes`, `perfil`. Todas possuem `id:Long`, `@GeneratedValue(IDENTITY)` e PK. Não há `@MappedSuperclass`, `@Embeddable`, inheritance mapping, `@Version`, soft delete, auditoria central ou constraints de valor financeiro positivo identificadas.

### 10.1 Perfil

Arquivo: `src/main/java/com/caio/controle_clientes/models/Perfil.java`; tabela `perfil`; repository PerfilRepositorio.

- Campos: id; nome:String `nullable=false,unique=true`; dataCriacao:LocalDateTime inicializado com now, sem not-null na annotation.
- Não declara coleção inversa de clientes/contratos/parcelas/notas.
- Papel: separar carteiras pelo ID; usado por PerfilService e como relação das quatro outras entidades. Importador instancia referências com apenas ID. Não possui senha, usuário, role, permissões ou proprietário.

### 10.2 Cliente

Arquivo: `src/main/java/com/caio/controle_clientes/models/Cliente.java`; tabela `clientes`; repository ClienteRepositorio.

- Campos: id, nome, nomeIndicador, enderecoRua, enderecoBairro (String); enderecoNumero:Integer; criadoEm:LocalDate inicializado hoje. Sem constraints JPA not-null/unique explícitas nesses campos de negócio.
- `perfil`: ManyToOne LAZY, FK `perfil_id` not-null, sem cascade.
- `emprestimos`: OneToMany mappedBy=cliente, cascade ALL, fetch padrão de OneToMany (LAZY), sem orphanRemoval declarado; `@JsonIgnore`.
- Lombok `@Data` gera getters/setters e equals/hashCode/toString; construtor vazio e construtor com nome/indicador/criadoEm/lista (sem endereço/perfil).
- Usado por ClienteService, EmprestimoController/Service, importação, exportação e relatórios. Não há unicidade de nome por perfil na tabela identificada; a regra de criação é do service.
- **Inferência de serialização/coleções:** @Data também inclui relacionamentos em métodos gerados; agrupamento por entidade Cliente no relatório deve ser avaliado com os relacionamentos carregados, não é agrupamento explícito por ID.

### 10.3 Emprestimo

Arquivo: `src/main/java/com/caio/controle_clientes/models/Emprestimo.java`; tabela `emprestimos`; repository EmprestimoRepositorio.

- LocalDate: dataDoEmprestimo, inicioPagamento, finalPagamento.
- BigDecimal: valorEmprestado, taxaDeJuros, valorAReceber, valorRecebido, valorTotalEmprestimo, valorParcela; sem precision/scale própria nas annotations.
- Integer quantidadeParcelas.
- Enums STRING: formaPagamento (sem not-null explícito), frequenciaPagamento/tipoContrato/emprestimoStatus (not-null).
- `perfil`: ManyToOne LAZY, `perfil_id` not-null, sem cascade.
- `cliente`: ManyToOne, `cliente_id`, sem not-null declarado, fetch padrão de ManyToOne (EAGER), sem cascade.
- `parcelas`: OneToMany mappedBy=emprestimo, cascade ALL, orphanRemoval=true, LAZY, ArrayList inicial; `@JsonIgnore`.
- Usado pela criação normal/importação, pagamentos/estornos/reprogramação, listagens/detalhes, relatórios/exportação. Armazena totais agregados que podem divergir das parcelas se algum caminho não os recompuser.

### 10.4 Parcelas

Arquivo: `src/main/java/com/caio/controle_clientes/models/Parcelas.java`; tabela `parcelas`; repository ParcelaRepositorio.

- numeroParcela:Integer, coluna literal `numero_parcela`, not-null.
- valorParcela e valorPago:BigDecimal not-null, precision=19,scale=2; valorPago inicial zero.
- dataVencimento:LocalDate not-null; dataPagamento:LocalDate sem not-null.
- status:ParcelaStatus STRING not-null, inicial PENDENTE.
- `perfil`: ManyToOne LAZY, `perfil_id` not-null; `emprestimo`: ManyToOne LAZY, `emprestimo_id` not-null. Sem cascade nessas duas relações.
- `observacoes`: OneToMany mappedBy=parcela, cascade ALL, orphanRemoval=true, ArrayList; sem JsonIgnore.
- UniqueConstraint **`uk_parcela_emprestimo_numero`** em (`emprestimo_id`,`numero_parcela`). Não há constraint de número>0 ou pago<=valor na annotation/dump analisado.
- Usada nos fluxos financeiros, cobrança, relatórios, importação/exportação e notas. Não existe entidade de pagamento individual; o histórico de recebimentos é reduzido ao valor acumulado/dataPagamento dessa parcela.
- Construtor alternativo inclui número, valor, vencimento, status, pagamento, empréstimo e pago; não preenche perfil. A geração normal e importada o fazem por setters/caller.

### 10.5 ParcelaObservacao

Arquivo: `src/main/java/com/caio/controle_clientes/models/ParcelaObservacao.java`; tabela `parcela_observacoes`; repository ParcelaObservacaoRepositorio.

- observacao:String not-null, length=1000; dataCriacao/dataAtualizacao:LocalDateTime not-null.
- `parcela`: ManyToOne LAZY, `parcela_id` not-null; `perfil`: ManyToOne LAZY, `perfil_id` not-null; sem cascade.
- Construtor vazio e construtor texto/parcela/perfil que inicializa datas. `@PrePersist prePersist()` sobrescreve ambas com now; `@PreUpdate preUpdate()` atualiza dataAtualizacao.
- Usada por ParcelaObservacaoService e coleção de Parcelas. Sem autor ou histórico de versões. Removida via service ou cascades da parcela.

### 10.6 Enums e estado

Arquivos em `src/main/java/com/caio/controle_clientes/models/`.

| Arquivo / enum | Valores persistidos como STRING / comportamento |
|---|---|
| FormaPagamento.java | PIX, DINHEIRO; classificação, sem integração financeira. |
| FrequenciaPagamento.java | DIARIO, SEMANAL, QUINZENAL, MENSAL; intervalo do calendário. |
| TipoContrato.java | FISICO, DIGITAL; metadado, sem upload/assinatura digital identificado. |
| EmprestimoStatus.java | EM_ABERTO, QUITADO, REFINANCIADO, QUITADO_TERCEIRO; getDescription devolve descrição textual. QUITADO_TERCEIRO é reconhecido pelo importador, não por endpoint específico. |
| ParcelaStatus.java | PENDENTE, ATRASADO, PAGO, PARCIAL; isPago=PAGO, isPendente=PENDENTE ou PARCIAL, isAtrasada=ATRASADO. |

Transições observadas de parcela: criação normal/importada aberta -> PENDENTE; consulta global com vencimento<hoje -> ATRASADO; recebimento incompleto -> PARCIAL; saldo completado -> PAGO; estorno -> PENDENTE/ATRASADO; alteração/reprogramação recalculam pela combinação pago/valor/data. Não existe transição periódica independente de HTTP.

### 10.7 Mapa de relacionamentos

```text
Perfil
 ├─ 1:N -> Cliente          (Cliente.perfil, perfil_id)
 ├─ 1:N -> Emprestimo       (Emprestimo.perfil, perfil_id)
 ├─ 1:N -> Parcelas         (Parcelas.perfil, perfil_id)
 └─ 1:N -> ParcelaObservacao(ParcelaObservacao.perfil, perfil_id)
Cliente
 └─ 1:N -> Emprestimo       (Emprestimo.cliente, cliente_id; cascade ALL em Cliente)
Emprestimo
 └─ 1:N -> Parcelas         (Parcelas.emprestimo, emprestimo_id; cascade ALL/orphanRemoval)
Parcelas
 └─ 1:N -> ParcelaObservacao(ParcelaObservacao.parcela, parcela_id; cascade ALL/orphanRemoval)
```

As cardinalidades de Perfil acima são lógicas pelas FKs; Perfil não tem os campos inversos. As quatro FKs de perfil são independentes e não há constraint composta impondo que contrato, cliente, parcela e nota pertençam ao mesmo perfil. Cascades JPA não devem ser confundidos com `ON DELETE CASCADE` SQL: o dump consultado não declara esse mecanismo nas FKs exibidas.

### 10.8 DDL complementar e migrations

`../backup.sql` é um dump SQL fora do repositório Git do backend, não uma migration versionada. Sua DDL foi consultada, sem reproduzir dados ou credenciais. Ela corrobora as 5 tabelas no schema `public`, PKs, FKs, unicidade de perfil.nome e a unique composta de parcelas. Existem CHECKs dos valores textuais dos 5 enums utilizados nas colunas de contrato/parcela. Não foram identificados índices secundários customizados no dump; PK/UNIQUE possuem seus mecanismos próprios de índice no PostgreSQL.

Diferenças entre evidência da entidade e do dump: BigDecimals de Emprestimo estão como numeric(38,2) no dump, sem essa precisão explícita no Java; os de Parcelas são numeric(19,2). `valorAReceber` aparece no dump como coluna **`valorareceber`**, não `valor_a_receber`; não deduzir nomes físicos somente separando palavras. Strings ordinárias aparecem como varchar(255); observacao varchar(1000); timestamps sem timezone com precisão 6. IDs são gerados por identity na DDL. O dump é evidência de um snapshot, não prova do schema ou conteúdo do banco atualmente conectado.

Não foram encontrados scripts em db/migration, changelogs Liquibase, procedimentos, triggers SQL de negócio, SQL nativo nos repositories ou aplicação automática desse dump no compose. A evolução declarada do backend usa ddl-auto=update.

## 11. DTOs e mapeamentos

Todos os 21 arquivos são inventariados abaixo. Os tipos BigDecimal, LocalDate e LocalDateTime são Java; a aparência exata de números/datas no JSON depende da configuração Jackson padrão/resolvida, sem custom serializer encontrado. Não há MapStruct, model mapper, projections/interface de retorno, AttributeConverter ou mapeamento genérico identificado.

### 11.1 DTOs de cliente

Diretório de cada arquivo: `src/main/java/com/caio/controle_clientes/dto/cliente/`.

| Arquivo / espécie | Campos e constraints | Uso |
|---|---|---|
| ClienteCreateDTO.java / record | nome:String NotBlank; nomeIndicador:String; enderecoRua:String NotBlank; enderecoBairro:String NotBlank; enderecoNumero:Integer NotNull | Body E05; controller aplica @Valid. Nenhuma constraint de número positivo. |
| UpdateNomeClienteDTO.java / class | nome:String, getter/setter | Body E07; sem validação. |
| UpdateEnderecoDTO.java / class | rua:String, bairro:String, numero:Integer; getters, sem setters explícitos | Body E08. Binding pretendido é via Jackson; acesso aos campos sem setters depende da configuração do mapper, não validado dinamicamente. |
| HistoricoClienteDTO.java / record | totalEmprestado,totalPago,totalAberto:BigDecimal; parcelasAtrasadas,totalEmprestimos,totalParcelas,parcelasPagas,parcelasAVencer:int | Resposta E29 construída por RelatorioService. |
| InadimplenteDTO.java / record | nomeCliente:String; quantidadeParcelasAtrasadas:long; valorTotalEmAtraso:BigDecimal; ultimoVencimento:LocalDate | Resposta E30. |

### 11.2 DTOs de empréstimo

Diretório: `src/main/java/com/caio/controle_clientes/dto/emprestimo/`.

- **EmprestimoCreateDTO.java** / record: valorEmprestado:BigDecimal, quantidadeParcelas:int, jurosCobrado:BigDecimal, formaPagamento:FormaPagamento, frequenciaPagamento:FrequenciaPagamento, tipoContrato:TipoContrato. Body E10, sem annotations de constraint e sem @Valid no controller.
- **EmprestimoDetalhadoDTO.java** / record: id:Long, nomeCliente:String, dataEmprestimo:LocalDate, formaPagamento/frequenciaPagamento/tipoContrato:String, valorEmprestado/valorTotalEmprestimo/valorParcela/valorRecebido/valorAReceber:BigDecimal, inicioPagamento/finalPagamento:LocalDate, status:EmprestimoStatus, quantidadeParcelas:Integer, parcelasPagas/parcelasPendentes/parcelasAtrasadas:Long. Resposta E14; mapper privado EmprestimoService.toDetalhadoDTO.

### 11.3 DTOs de parcela e observação

Diretório: `src/main/java/com/caio/controle_clientes/dto/parcela/`. Todos são records sem Bean Validation.

| Arquivo | Campos | Uso |
|---|---|---|
| PagarParcelaDTO.java | idEmprestimo:Long, numeroParcela:Integer | Body E18 |
| PagarParcelaParcialDTO.java | idEmprestimo:Long, numeroParcela:Integer, valorPago:BigDecimal | Body E19 |
| DesfazerPagamentoParcelaDTO.java | idEmprestimo:Long, numeroParcela:Integer | Body E20 |
| AlterarParcelaDTO.java | idEmprestimo:Long, numeroParcela:Integer, novaData:LocalDate, novoValor:BigDecimal | Body E24 |
| ReprogramarParcelasDTO.java | novaDataInicial:LocalDate, novoValorParcela:BigDecimal | Body E17 |
| ParcelaResponseDTO.java | idEmprestimo:Long, nomeCliente:String, numeroParcela:Integer, valorParcela/valorPago:BigDecimal, status:ParcelaStatus, dataVencimento:LocalDate | Respostas E21–E23; não possui id da própria parcela, dataPagamento, perfil ou observações |
| CriarObservacaoParcelaDTO.java | idEmprestimo:Long, numeroParcela:Integer, observacao:String | Body E25 |
| EditarObservacaoParcelaDTO.java | idObservacao:Long, observacao:String | Body E27 |
| ParcelaObservacaoResponseDTO.java | id:Long, observacao:String, dataCriacao/dataAtualizacao:LocalDateTime | Resposta E26 |

### 11.4 DTOs de perfil

Diretório: `src/main/java/com/caio/controle_clientes/dto/Perfil/`.

- **CriarPerfilRequest.java** / class com Lombok Getter/Setter: nome:String NotBlank; body E04, @Valid.
- **PerfilResponse.java** / class Getter/Builder: id:Long e nome:String; `public static fromEntity(Perfil) -> PerfilResponse` copia esses campos; E03/E04. Não inclui dataCriacao.

### 11.5 DTOs internos da importação

Diretório: `src/main/java/com/caio/controle_clientes/services/importService/dto/`.

- **LinhaImportacaoDTO.java** / record: dataEmprestimo:LocalDate; nomeClienteOriginal/nomeCliente/nomeIndicador:String; formaPagamento:FormaPagamento; tipoContrato:TipoContrato; frequenciaPagamento:FrequenciaPagamento; valorEmprestado:BigDecimal; totalParcelas/parcelasPagas:Integer; valorAReceber/valorRecebido/valorTotalEmprestimos/valorParcela/taxaJurosPercentual:BigDecimal; inicioPgto/fimPgto:LocalDate; situacao:EmprestimoStatus. Somente representação intermediária da linha, não body JSON HTTP.
- **ErroImportacao.java** / record: linha:int e motivo:String; número 1-based da linha física da planilha, inclui cabeçalho na numeração.
- **ImportacaoResultado.java** / record: totalLinhas/linhasImportadas/linhasIgnoradas/linhasDuplicadas:int; erros:List<ErroImportacao>. Body da resposta E31; duplicadas possuem contador próprio e também um registro em erros, não são somadas a linhasIgnoradas no branch de duplicidade.

### 11.6 Caminhos de mapeamento

```text
JSON ClienteCreateDTO -> ClientController -> parâmetros de ClienteService
 -> Cliente novo (setters) -> ClienteRepositorio.save -> 201 sem body

JSON EmprestimoCreateDTO -> EmprestimoController -> ClienteService.getClienteById
 -> EmprestimoService.montarEmprestimo -> CalculadoraEmprestimoService
 -> Emprestimo + List<Parcelas> -> repositories -> 201 sem body

EmprestimoRepositorio.findAllByClienteAndPerfilId -> Emprestimo + parcelas
 -> EmprestimoService.toDetalhadoDTO -> EmprestimoDetalhadoDTO
 -> EmprestimoController -> 200 JSON

ParcelaRepositorio (fetch empréstimo/cliente) -> Parcelas
 -> ParcelaService.toDTO -> ParcelaResponseDTO -> ParcelaController -> 200 JSON

ParcelaObservacaoRepositorio -> ParcelaObservacao
 -> ParcelaObservacaoService.toDTO -> ParcelaObservacaoResponseDTO -> 200 JSON

PerfilRepositorio -> Perfil -> PerfilResponse.fromEntity -> PerfilController

Multipart stream -> POI Row -> ImportacaoLinhaMapper/CellReader
 -> LinhaImportacaoDTO -> Validator -> ImportacaoPlanilhaService
 -> Cliente/Emprestimo -> ImportacaoParcelaFactory -> Parcelas -> repositories
 -> ImportacaoResultado -> ImportController
```

Retornos diretos de entidades: E09 Cliente, E11 Emprestimo e E12 Parcelas. `Cliente.emprestimos` e `Emprestimo.parcelas` têm JsonIgnore; `Cliente.perfil`, `Emprestimo.cliente/perfil`, `Parcelas.emprestimo/perfil/observacoes` e `ParcelaObservacao.parcela/perfil` não têm JsonIgnore. **Inferência:** no E12, parcela com observações pode formar `Parcelas.observacoes -> ParcelaObservacao.parcela -> Parcelas...`; não há JsonManagedReference/JsonBackReference ou identity info customizado observado. Serialização de proxies LAZY também depende da sessão e Jackson resolvidos. Não foi possível determinar com segurança pelo código analisado o JSON completo produzido em todas essas situações de runtime.

## 12. Segurança e autenticação

### 12.1 Requisição e identidade

Não foram encontrados starter Spring Security, SecurityFilterChain, OncePerRequestFilter, interceptors autenticadores, JWT, OAuth/OAuth2, Basic Auth, API key, login, logout, refresh token, encoder de senha, roles, authorities, `@PreAuthorize` ou `@Secured`. Não há entidade de usuário.

Fluxo real identificado:

1. Cliente envia requisição, normalmente com `X-Perfil-Id`.
2. Spring MVC converte o header para Long; se ausente em operação obrigatória, lança MissingRequestHeaderException antes do controller.
3. Controller passa perfilId a services/repos.
4. Consultas filtram registros pelo perfil; alguns fluxos buscam Perfil e confirmam cliente relacionado.
5. Nenhum passo valida quem é o remetente ou se ele pode selecionar aquele perfil.

Todos os endpoints são públicos no sentido de ausência de autenticação declarada. Quatro operações dispensam o perfil: GET /, GET /ping, GET /perfis e POST /perfis. Outras 28 exigem o ID de escopo. Não há endpoints protegidos por principal/role no backend. O cabeçalho obrigatório não deve ser descrito como credencial nem como mecanismo de autorização por proprietário.

No frontend complementar, `../frontend/src/contexts/ProfileContext.tsx` lista perfis, restaura ID salvo ou seleciona primeiro da lista; `../frontend/src/services/apiClient.ts` lê localStorage `perfil_ativo_id` e adiciona header. Isso reforça que o perfil é uma seleção de carteira. Não há prova de autenticação fornecida por proxy/infra externa; **Não foi possível determinar com segurança pelo código analisado.**

### 12.2 CORS e CSRF

Arquivo: `src/main/java/com/caio/controle_clientes/config/CorsConfig.java`. `@Bean corsConfigurer() -> WebMvcConfigurer`, implementação anônima de addCorsMappings:

- Mapping `/**`.
- `allowedOriginPatterns("http://localhost:*")`.
- Métodos GET, POST, PUT, PATCH, DELETE, OPTIONS.
- Headers `*` (permite header de perfil).
- `allowCredentials(true)`.
- Não declara exposedHeaders, maxAge, outras origens ou profiles.

CORS disciplina acesso pelo navegador; não autentica clientes HTTP. Não há configuração explícita de CSRF; não foi encontrado Spring Security para impor uma política própria de CSRF. A presença de allowCredentials não comprova autenticação por cookie.

### 12.3 Limites observados

- Perfis podem ser enumerados/criados sem identidade; ID é controlado pelo cliente.
- Filtros por perfil existem em queries de negócio; não há tenant context central ou validação repetida de todas as FKs.
- Sem password encoder, token signing/expiration, rate limiting ou auditoria por autor no código próprio.
- O advice devolve detalhes do banco e mensagens internas em alguns erros; a importação registra nome/valores de negócio em logs.
- Configuração do datasource e compose incluem fallback/valor de credencial em arquivo; o valor foi deliberadamente omitido neste relatório. Isso é fato sobre configuração, não uma alteração realizada.

## 13. Exceptions

Arquivos do pacote: `src/main/java/com/caio/controle_clientes/exceptions/`.

`GlobalExceptionHandler.java` é `@RestControllerAdvice` e, de modo incomum, estende RuntimeException. É registrado pelo framework como advice; não precisa de chamada direta. Todos os seis handlers executam `ex.printStackTrace()` e retornam Map/ResponseEntity.

| Exception -> ocorrência | Método handler | Status | Corpo declarado |
|---|---|---|---|
| MissingRequestHeaderException -> header obrigatório ausente | handleMissingRequestHeader | 400 | error=Header obrigatório ausente; message menciona nome do header |
| MethodArgumentNotValidException -> @Valid em cliente/perfil | handleValidation | 400 | error=Erro de validação; message=Um ou mais campos estão inválidos.; fields mapa campo->mensagem |
| IllegalArgumentException -> checagem de perfil nulo no ClientController e outros argumentos runtime | handleIllegalArgument | 400 | error=Requisição inválida; message=ex.getMessage ou Argumento inválido. |
| DataIntegrityViolationException -> constraints/FKs/colunas/unique | handleDataIntegrity | 409 | error=Erro de integridade no banco; message=Não foi possível salvar os dados.; details=mensagem da causa mais específica ou da exception |
| RuntimeException -> regras manuais, ausência de registros, NPE/ArithmeticException propagadas | handleRuntime | 400 | error=Erro de execução; message da exception ou Erro inesperado |
| Exception -> falha geral não coberta por handlers mais específicos | handleGeneric | 500 | error=Erro interno do servidor; message da exception ou Erro inesperado |
| ParcelaPagaException -> EmprestimoService.validarPagamentoParcela | handleRuntime (sem handler específico) | 400 | error=Erro de execução; message=Parcela já está paga. |
| EmprestimoQuitadoException -> nenhuma instanciação ativa encontrada | handleRuntime se lançada | 400 previsto pelo advice | message=Empréstimo já está quitado.; services ativos usam RuntimeException genérica em vez dessa classe |
| RegraNegocioException -> nenhuma instanciação ativa encontrada | handleRuntime se lançada; classe também tem @ResponseStatus(BAD_REQUEST) | 400 previsto | mensagem do construtor; sem payload específico próprio |

Principais pontos de lançamento manual: PerfilService nome duplicado/perfil ausente; ClienteService nome duplicado/cliente ausente/contratos existentes; EmprestimoService frequência/tipo obrigatórios, pertencimento, contrato/parcela ausente, recebimento inválido, encerramento já feito, reprogramação inválida; ParcelaService alteração/estorno inválidos; ParcelaObservacaoService texto/parcela/nota ausentes. As mensagens não usam códigos estáveis de erro.

ImportController contém tratamento independente: arquivo vazio -> 400 com ImportacaoResultado zerado; Exception no service -> 500 com o mesmo shape zerado, sem mensagem de causa. Erros por linha são capturados dentro do importador e viram ErroImportacao, não passam pelo advice naquela captura. Falha no commit pode escapar após construir o resumo. O conselho global não recebe necessariamente erros interceptados localmente.

Não há handlers próprios para ResourceNotFound/404, `HttpMessageNotReadableException`, argumentos/query ausentes ou type mismatch. A seleção entre resolução padrão MVC e catch amplo do advice precisa de teste com o runtime efetivo. Não afirmar um formato padronizado para todos os erros de transporte.

## 14. Integrações externas

### 14.1 PostgreSQL

- Configuração: `src/main/resources/application.properties`; interfaces em `src/main/java/com/caio/controle_clientes/repository/`.
- Objetivo: persistir todas as entidades e apoiar buscas derivadas/JPQL.
- Chamadores: services de perfil/cliente/contrato/parcela/notas/relatório/importação/exportação; ParcelaController diretamente nas consultas.
- Métodos: operações JpaRepository herdadas e consultas da seção 9.
- Configs necessárias: spring.datasource.url/username/password/driver-class-name, DB_HOST, DB_PORT, DB_USER, DB_PASSWORD; Hibernate PostgreSQLDialect.
- Sem timeout/retry/circuit breaker customizados no código. Pool e defaults são dependências do framework, não configurações explícitas do projeto.

### 14.2 Planilhas via Apache POI (processamento local)

- Entrada: ImportController/ImportService/ImportacaoPlanilhaService e quatro componentes auxiliares, caminhos nas seções 7/8.
- Saída: BackupController/ExportService, arquivos já listados.
- Objetivo: carregar contratos agregados e exportar resumo de carteira.
- APIs de biblioteca: WorkbookFactory.create, getSheetAt(0), FormulaEvaluator.evaluate, DataFormatter.formatCellValue, DateUtil; XSSFWorkbook/createSheet/createCell/estilos/autosize/write.
- Sem API externa, credencial, armazenamento remoto, retry, circuit breaker ou configuração específica da integração. Entrada e bytes da saída são usados dentro da requisição.

### 14.3 Consumo HTTP pelo frontend complementar

O backend não chama o frontend. O frontend React/TypeScript/Vite chama o backend por fetch. `../frontend/nginx.conf` roteia `/api/` para backend:8080 com remoção do prefixo, adicionando Host/X-Real-IP/X-Forwarded-*; não autentica. `../frontend/src/config/env.ts` escolhe VITE_API_URL ou `/api`.

Arquivos relacionados lidos: `../frontend/package.json`, `../frontend/Dockerfile`, `../frontend/nginx.conf`, `../frontend/src/config/env.ts`, `../frontend/src/contexts/ProfileContext.tsx`, `../frontend/src/services/apiClient.ts`, services clientes/dashboard/emprestimos/relatorio/simulacao e `services/parcelas/parcelas.service.ts`, `services/parcelas/parcela-observacao.service.ts`, `../frontend/src/pages/BackupPage/index.tsx`.

Divergências observáveis nas declarações de consumidores (não contam como endpoints Java): clientesService.getById aponta GET `/clientes/{id}`; dashboardService.getTotalEmprestado aponta GET `/dashboard/total-emprestado`; parcelasService.getByEmprestimo aponta GET `/parcelas/emprestimo/{id}`. Não foram encontrados mappings Java correspondentes. Não se afirma que todos esses métodos são invocados pela UI atual.

`emprestimosService.getParcelas` tipa o retorno como ParcelaResponse, mas o Java retorna List<Parcelas>. `relatorio.service.ts` monta parte do relatório no cliente usando campos financeiros agregados dos contratos e consultas de parcelas, em vez de chamar obrigatoriamente o endpoint E29; seu totalEmprestado soma valorTotalEmprestimo, diferente do principal somado em RelatorioService. `simulacao.service.ts` calcula localmente a expressão de prestação e rejeita entradas falsy, inclusive juros zero; não há endpoint Java de simulação encontrado.

BackupPage envia multipart file; para exportar usa fetch separado e recebe blob, com credentials=include e header de perfil. A UI mostra sucesso ao receber 2xx da importação sem examinar o detalhe dos contadores/erros. Esses fatos não alteram o contrato real do backend.

Sem Feign, RestTemplate, WebClient, Java HttpClient, SDK cloud, email, pagamentos externos, assinatura digital ou autenticação externa encontrados nas fontes/dependências próprias.

## 15. Mensageria e eventos

Não foram encontrados Kafka, RabbitMQ, SQS, SNS, JMS, application event publishers de negócio, producers, consumers, filas, tópicos, `@EventListener`, `@KafkaListener`, `@RabbitListener` ou `@JmsListener`. As operações financeiras não produzem mensagem identificada.

Origem/evento/producer/tópico/consumer/processamento: não aplicável aos fluxos de negócio existentes. `@PrePersist` e `@PreUpdate` de ParcelaObservacao são callbacks do ciclo de persistência JPA, não mensagens ou eventos de integração. Os eventos internos do próprio framework não constituem um fluxo de negócio implementado pelo projeto.

## 16. Schedulers e jobs

Não foram encontrados `@Scheduled`, `@EnableScheduling`, Quartz, Spring Batch, jobs, executores próprios, `@Async` ou `@EnableAsync`. Não existe processo automático periódico de cobrança ou atraso.

| Processo automático observado | Quando / efeito | Dependências / banco / integrações |
|---|---|---|
| ParcelaService.atualizarParcelasAtrasadas | Executado sincronicamente por GET E21/E22/E23; muda PENDENTE/PARCIAL vencidos para ATRASADO | ParcelaRepositorio / PostgreSQL / nenhuma API externa |
| ParcelaObservacao.prePersist | Antes de insert JPA; define dataCriacao e dataAtualizacao=agora | Ciclo ORM / PostgreSQL / nenhuma |
| ParcelaObservacao.preUpdate | Antes de update JPA; define dataAtualizacao=agora | Ciclo ORM / PostgreSQL / nenhuma |
| Inicialização de criadoEm/dataCriacao | Ao instanciar Cliente/Perfil; valores padrão Java LocalDate/LocalDateTime.now | Não é scheduler nem auditoria automática para qualquer update |

`Principal.showMenu` não é callback de startup. Embora ControleClientesApplication importe CommandLineRunner, não o implementa nem declara bean runner.

## 17. Configurações

### 17.1 Nomes e finalidade (sem valores de credenciais)

| Configuração/variável | Finalidade |
|---|---|
| DB_HOST | Host usado na URL JDBC PostgreSQL; há fallback local. |
| DB_PORT | Porta usada na URL JDBC; há fallback padrão PostgreSQL. |
| DB_USER | Usuário do datasource; há fallback definido, valor omitido. |
| DB_PASSWORD | Senha do datasource; há fallback literal definido no arquivo, valor omitido. |
| spring.application.name | Nome lógico da aplicação. |
| spring.datasource.url | URL JDBC parametrizada por host/porta, aponta ao banco da aplicação. |
| spring.datasource.username | Credencial de usuário via variável/fallback. |
| spring.datasource.password | Credencial de senha via variável/fallback. |
| spring.datasource.driver-class-name | Driver PostgreSQL. |
| spring.jpa.hibernate.ddl-auto | Estratégia de atualização automática do schema; definida como update. |
| spring.jpa.show-sql | Controle de impressão das consultas; desativado. |
| spring.jpa.properties.hibernate.format_sql | Formatação SQL; desativada. |
| spring.jpa.properties.hibernate.dialect | Dialeto Hibernate para PostgreSQL. |
| logging.level.org.hibernate.SQL | Log de SQL; OFF. |
| logging.level.org.hibernate.orm.jdbc.bind | Log de valores de bind; OFF. |
| POSTGRES_DB | Nome do banco inicial do serviço PostgreSQL no compose complementar. |
| POSTGRES_USER | Usuário criado no container PostgreSQL; valor omitido. |
| POSTGRES_PASSWORD | Credencial inicial no compose; valor omitido. |
| VITE_API_URL | Configuração do frontend complementar para base HTTP do backend. |
| perfil_ativo_id | Chave de localStorage do frontend para seleção de perfil; não é variável de ambiente nem token. |
| X-Perfil-Id | Cabeçalho HTTP de escopo; não é segredo de configuração. |

Fonte central do backend: `src/main/resources/application.properties`. Não há arquivos application por profile, `@Profile`, feature flags, configuração de Redis/broker/JWT/storage/AWS/GCP/Azure identificados. Não há limites multipart, timeout HTTP, timezone, Jackson customizado, pool, porta ou context path explicitamente configurados nesse arquivo. Os limites/defaults efetivos não foram determinados por execução.

### 17.2 Containers e build

`Dockerfile` do backend:

1. Stage build usa maven:3.9-eclipse-temurin-17 em /app; copia pom.xml e src.
2. Executa `mvn -q -DskipTests package`; testes não são executados nesse build declarado.
3. Runtime eclipse-temurin:17-jre; copia target/*.jar para app.jar; EXPOSE 8080; entrypoint java -jar.

Sem healthcheck, user não-root explícito, parâmetros JVM customizados ou volume de arquivos no Dockerfile. EXPOSE não demonstra que o container esteja em execução.

`../docker-compose.yml`: três services, postgres/backend/frontend, todos restart always. PostgreSQL 16 Alpine com volume nomeado postgres_data e porta 5432 publicada; backend build ./backend, variáveis DB_*, porta 8080; frontend build ./frontend, porta 80, Nginx. depends_on define dependência de startup, sem healthcheck/readiness declarado. Não há serviço Redis/broker ou mount automático do dump SQL identificado.

`../frontend/Dockerfile`: build Node 20 Alpine com npm ci/build e runtime Nginx Alpine; `../frontend/nginx.conf` serve SPA com fallback index.html e proxy /api/. O compose tem credenciais literais, omitidas aqui. Políticas/rede/secrets de uma implantação externa não estão disponíveis para confirmação.

`.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd`: suporte do build Maven; wrapper apenas script. `.gitignore`: ignora target e artefatos de IDE; `.gitattributes`: EOL LF para mvnw e CRLF para *.cmd. Não são fluxos de negócio. Não há pipeline CI versionado ou outro módulo Java identificado.

## 18. Testes

Único arquivo de teste identificado: `src/test/java/com/caio/controle_clientes/ControleClientesApplicationTests.java`.

- Classe package-private, `@SpringBootTest`.
- Único método `@Test void contextLoads()` com corpo vazio: pretende verificar carregamento do ApplicationContext; não possui asserts de negócio.
- Não há mocks, fixtures, testes unitários de cálculo, MockMvc/WebMvcTest, DataJpaTest, suites de controller/repository, Testcontainers, H2 ou configuração de banco isolada identificados.
- Não há profile de teste próprio; o contexto pode depender do datasource PostgreSQL da configuração corrente (inferência, não execução).
- Não foram executados testes/builds; não há percentual de cobertura calculado ou artefato de cobertura identificado.

Áreas sem testes específicos identificados: separação por perfil e acesso cruzado; cadastro/duplicidade/exclusão; fórmula e arredondamento; calendários e domingos; pagamentos/concorrência/estorno; reprogramação e estados de contrato; observações/callbacks; queries/ordenação; relatórios com parciais; importação/deduplicação/rollback; compatibilidade export-import; serialização de entidades; advice/CORS. A existência de contextLoads não comprova cobertura dessas capacidades.

## 19. Fluxos end-to-end

As seções 6 e 8 descrevem cada operação; abaixo estão fluxos combinados para orientar outra IA. Arquivos de cada classe são os listados nas seções 7–11; referências adicionais explícitas acompanham os fluxos.

### 19.1 Selecionar carteira, cadastrar cliente e contrato

1. Consumidor chama GET /perfis sem header obrigatório; PerfilController -> PerfilService -> PerfilRepositorio.findAll -> List<PerfilResponse>.
2. Consumidor escolhe ID; frontend complementar o salva em perfil_ativo_id e envia X-Perfil-Id. Não há autenticação nesse passo.
3. POST /clientes com ClienteCreateDTO é validado pelo @Valid do ClientController.
4. ClienteService verifica nome no perfil, busca Perfil, monta/salva Cliente; retorna 201 vazio.
5. Consumidor obtém cliente por GET /clientes (criação não retorna ID).
6. POST /clientes/{idCliente}/emprestimos passa EmprestimoCreateDTO sem Bean Validation.
7. EmprestimoController resolve cliente por ID/perfil; EmprestimoService valida frequência/tipo/pertencimento e calcula prestação.
8. Em transação, grava contrato e parcelas geradas, preenche período, grava contrato; 201 vazio. Sem pagamento externo/evento.

Arquivos centrais: controllers/PerfilController.java, controllers/ClientController.java, controllers/EmprestimoController.java, services/ClienteService.java, services/perfil/PerfilService.java, services/emprestimo/EmprestimoService.java, services/emprestimo/CalculadoraEmprestimoService.java, respectivos repositories/models sob `src/main/java/com/caio/controle_clientes/`.

### 19.2 Receber valor parcial e completar parcela

1. POST /parcelas/pagar-parcial informa contrato, número e incremento com header de perfil.
2. ParcelaController chama EmprestimoService.pagarParcelaParcial em transação.
3. ParcelaRepositorio consulta pela tripla; parcela inexistente falha com 400 via advice.
4. Service bloqueia contrato QUITADO/parcela PAGO, incremento<=0 ou maior que saldo.
5. Soma incremento, define PARCIAL ou PAGO e dataPagamento=hoje; salva.
6. Incrementa recebido agregado do contrato, calcula aReceber; se todas parcelas PAGO, status QUITADO; salva.
7. API retorna 204. Se depois POST /parcelas/pagar, o service calcula saldo e executa esse mesmo caminho com saldo completo.
8. GET /parcelas pode reclassificar PARCIAL vencida como ATRASADO; valorPago fica preservado.

Arquivos: controllers/ParcelaController.java, services/emprestimo/EmprestimoService.java, repository/ParcelaRepositorio.java, repository/EmprestimoRepositorio.java, dto/parcela/PagarParcelaParcialDTO.java, models/Parcelas.java, exceptions/ParcelaPagaException.java.

### 19.3 Desfazer recebimento e reabrir contrato

1. POST /parcelas/desfazer-pagamento informa ID do contrato/número e perfil.
2. ParcelaService consulta parcela e exige acumulado >0.
3. Zera acumulado todo, mantém dataPagamento antiga, escolhe ATRASADO se vencimento<hoje, senão PENDENTE.
4. Salva parcela; reconstrói total e recebido somando todas as parcelas.
5. Se o contrato era QUITADO e existe parcela não PAGO, muda para EM_ABERTO; REFINANCIADO não recebe essa reabertura automática.
6. Salva contrato; 204. Não há devolução bancária nem entidade de estorno financeiro externo.

Arquivos: controllers/ParcelaController.java, services/parcela/ParcelaService.java, dto/parcela/DesfazerPagamentoParcelaDTO.java, repositories de parcela/empréstimo.

### 19.4 Consultar vencimentos e inadimplência

1. GET /parcelas/vencendo-hoje, /mes-atual ou /parcelas?status chama atualizarParcelasAtrasadas.
2. Duas consultas por perfil selecionam PENDENTE/PARCIAL anteriores a hoje; transação os grava ATRASADO.
3. Controller realiza outra query com fetch contrato/cliente, seleciona o recorte desejado e mapeia ParcelaResponseDTO.
4. Retorna 200. Escrita do atualizador e consulta posterior não são uma transação única no controller.
5. GET /relatorios/inadimplentes usa status ATRASADO/PARCIAL + data<hoje, agrupa saldo restante por Cliente e retorna InadimplenteDTO.
6. Se o passo 1 nunca foi executado, PENDENTE vencidos não são incluídos por esse relatório; não há scheduler para corrigir antes.

Arquivos: controllers/ParcelaController.java, services/parcela/ParcelaService.java, repository/ParcelaRepositorio.java, services/RelatorioService.java, controllers/RelatorioController.java.

### 19.5 Alterar parcela e reprogramar abertas

1. PATCH /parcelas/alterar exige data, valor positivo e >=acumulado pago; proíbe passado se status antigo não é PAGO.
2. ParcelaService muda a parcela, recalcula status e totais financeiros; não sincroniza início/fim do contrato.
3. Alternativamente, PATCH do contrato /parcelas/reprogramar exige nova data inicial e novo valor positivo.
4. EmprestimoService valida dono/cliente, seleciona todas não PAGO em ordem de número e aplica novo valor e datas segundo frequência.
5. Reprogramação aceita passado, recompõe status por data/valor, recalcula financeiros, prestação agregada e período.
6. Ambas retornam 204; não há recálculo de taxa/amortização ou contrato novo.

Arquivos: controllers/ParcelaController.java, controllers/EmprestimoController.java, services/parcela/ParcelaService.java, services/emprestimo/EmprestimoService.java, dto/parcela/AlterarParcelaDTO.java e ReprogramarParcelasDTO.java.

### 19.6 Quitar ou refinanciar

1. POST do contrato /quitar ou /refinanciar contém somente paths e header.
2. EmprestimoService resolve contrato com parcelas, verifica cliente e estado atual.
3. Calcula e acrescenta saldo faltante por parcela, marcando PAGO/data atual; no refinanciamento preserva já pagas.
4. Salva todas, soma pago, subtrai do total armazenado e aplica QUITADO/REFINANCIADO.
5. Retorna 200 vazio. Valor acrescentado é contabilização no banco, sem prova de transação bancária.
6. Não emite evento, não cria empréstimo substituto, não referencia contrato anterior/novo.

Arquivos: controllers/EmprestimoController.java, services/emprestimo/EmprestimoService.java, models/EmprestimoStatus.java e repositories de empréstimo/parcela.

### 19.7 Observação com auditoria de datas

1. POST /parcelas/observacoes fornece tripla escopada e texto.
2. ParcelaObservacaoService rejeita blank/null, resolve parcela, cria nota trim com perfil da parcela.
3. Save dispara prePersist e define duas datas; commit, 204.
4. GET /parcelas/observacoes resolve a mesma parcela e busca notas por dataCriacao DESC, converte DTO e retorna 200.
5. PATCH por idObservacao/perfil atualiza texto e timestamp de update; DELETE remove a nota; ambos 204. Não registra autor.

Arquivos: controllers/ParcelaObservacaoController.java, services/parcela/ParcelaObservacaoService.java, models/ParcelaObservacao.java, repository/ParcelaObservacaoRepositorio.java, DTOs de observação em dto/parcela.

### 19.8 Importar carteira

1. POST /import multipart file e header; ImportController recusa vazio, abre stream.
2. ImportService delega ao bean transacional ImportacaoPlanilhaService.
3. POI abre Workbook, primeira aba; primeira Row é descartada; demais não vazias são mapeadas por posições.
4. CellReader normaliza tipos, LinhaMapper enum/nome, Validator primeiro problema de linha.
5. Para linha válida, resolve/cria Cliente no perfil usando cache local; consulta duplicidade composta.
6. Se duplicada, registra contador/motivo sem alterar o contrato existente; se nova, copia valores/datas/status para Emprestimo e salva.
7. Factory reconstrói parcelas constantes por contagem de pagas, ajusta domingos se diário; orquestrador preenche perfil e salva parcelas.
8. Resumo retornado em 201 pode conter falhas de linha. Falha que escapa da chamada/commit retorna 500 zerado pelo controller. Não há garantia demonstrada de rollback por linha.

Arquivos: controllers/ImportController.java, services/ImportService.java, todos os arquivos services/importService/*.java e services/importService/dto/*.java, repos de cliente/empréstimo/parcela.

### 19.9 Exportar carteira e consultar histórico

1. GET /export entrega perfil ao ExportService; contratos com cliente/parcelas são carregados por EntityGraph.
2. Ordena contratos por data; uma linha por contrato em Backup; calcula recebido/saldo das parcelas e usa outros agregados armazenados.
3. Formata 18 colunas, escreve bytes; controller adiciona MIME/download/cache-control e retorna 200.
4. Arquivo não contém layout exigido pelo importador nem histórico detalhado/notas/endereço; não presumir restauração sem transformação.
5. GET /relatorios/clientes/{idCliente}, separadamente, resolve cliente e agrega principal/status das parcelas pelo RelatorioService, com regras diferentes do exportador e do frontend.

Arquivos: controllers/BackupController.java, services/ExportService.java, repository/EmprestimoRepositorio.java, controllers/RelatorioController.java, services/RelatorioService.java.

## 20. Dependências entre componentes

```text
ControleClientesApplication -> SpringApplication / auto-configuração / component scan
CorsConfig.corsConfigurer -> WebMvcConfigurer anônimo -> CorsRegistry

PerfilController -> PerfilService -> PerfilRepositorio -> Perfil / PostgreSQL
PerfilHeaderService -> PerfilService (sem chamador ativo encontrado)

ClientController -> ClienteService
  ├─ ClienteRepositorio -> Cliente
  ├─ EmprestimoRepositorio -> Emprestimo (bloqueio de exclusão)
  └─ PerfilService -> PerfilRepositorio

EmprestimoController
  ├─ ClienteService -> ClienteRepositorio
  └─ EmprestimoService
       ├─ PerfilService -> PerfilRepositorio
       ├─ CalculadoraEmprestimoService -> Emprestimo/Parcelas/enums (somente memória)
       ├─ EmprestimoRepositorio -> Emprestimo
       └─ ParcelaRepositorio -> Parcelas

ParcelaController
  ├─ EmprestimoService (pagamentos)
  ├─ ParcelaService
  │    ├─ ParcelaRepositorio
  │    └─ EmprestimoRepositorio
  └─ ParcelaRepositorio (consultas de listagem diretamente)

ParcelaObservacaoController -> ParcelaObservacaoService
  ├─ ParcelaRepositorio
  └─ ParcelaObservacaoRepositorio -> ParcelaObservacao (callbacks JPA)

RelatorioController
  ├─ ClienteService
  └─ RelatorioService -> EmprestimoRepositorio / ParcelaRepositorio

ImportController -> ImportService -> ImportacaoPlanilhaService
  ├─ ImportacaoLinhaMapper -> ImportacaoCellReader -> POI
  ├─ ImportacaoValidator -> LinhaImportacaoDTO
  ├─ ImportacaoParcelaFactory -> Parcelas
  ├─ ClienteRepositorio
  ├─ EmprestimoRepositorio
  └─ ParcelaRepositorio

BackupController -> ExportService -> EmprestimoRepositorio / POI
HomeController -> strings constantes
GlobalExceptionHandler <- resolução de exceptions pelo Spring MVC
Principal -> stdout (sem invocação ativa encontrada)
```

Todas as interfaces de persistência são proxies Spring Data; todos os caminhos de banco convergem ao datasource PostgreSQL. Nenhuma dependência de publisher/consumer/gateway externo identificada. Fluxos de importação e cadastro normal convergem nas entidades/repositories, mas **não** nas mesmas validações ou calendários.

## 21. Grafo funcional

```text
CRIAR CONTRATO
POST /clientes/{idCliente}/emprestimos
  ↓ EmprestimoController.criarEmprestimo
  ├─ ClienteService.getClienteById -> ClienteRepositorio.findByIdAndPerfilId
  └─ EmprestimoService.criarEmprestimo [Tx]
      ├─ PerfilService.buscarPorId -> PerfilRepositorio.findById
      ├─ montarEmprestimo -> CalculadoraEmprestimoService.calcularParcela
      ├─ EmprestimoRepositorio.save
      ├─ CalculadoraEmprestimoService.gerarParcelas
      ├─ ParcelaRepositorio.saveAll
      └─ atualizarPeriodoPagamento -> EmprestimoRepositorio.save -> PostgreSQL
  ↑ 201 sem body

RECEBER PARCELA
POST /parcelas/pagar -> ParcelaController.pagarParcela
  ↓ EmprestimoService.pagarParcela [Tx]
  ├─ buscarParcela -> ParcelaRepositorio.findByEmprestimoIdAndPerfilIdAndNumeroParcela
  └─ pagarParcelaParcial
      ├─ buscarParcela -> mesmo repository
      ├─ validarPagamentoParcela -> possível ParcelaPagaException
      ├─ ParcelaRepositorio.save
      └─ atualizarFinanceiroEmprestimo -> EmprestimoRepositorio.save -> PostgreSQL
  ↑ 204; exceptions -> GlobalExceptionHandler

COBRANÇA POR STATUS
GET /parcelas?status=...
  ↓ ParcelaController.listarPorStatus
  ├─ ParcelaService.atualizarParcelasAtrasadas [Tx]
  │   ├─ duas findByStatusAndPerfilIdAndDataVencimentoBefore
  │   └─ ParcelaRepositorio.saveAll -> PostgreSQL
  ├─ ParcelaRepositorio.findParcelasComEmprestimoEClientePorStatus -> PostgreSQL
  └─ ParcelaService.toDTO -> ParcelaResponseDTO -> 200

IMPORTAR
POST /import -> ImportController -> ImportService
  ↓ ImportacaoPlanilhaService.importar [Tx]
  ├─ WorkbookFactory -> Row
  ├─ ImportacaoLinhaMapper -> ImportacaoCellReader -> LinhaImportacaoDTO
  ├─ ImportacaoValidator.validar -> erro por linha ou continuação
  ├─ obterOuCriarCliente -> cache / ClienteRepositorio
  ├─ emprestimoJaExiste -> EmprestimoRepositorio.exists...
  ├─ criarEmprestimo -> EmprestimoRepositorio.save
  └─ ImportacaoParcelaFactory.gerar -> ParcelaRepositorio.saveAll -> PostgreSQL
  ↑ ImportacaoResultado -> 201 ou catch local -> 500 zerado

NOTAS
HTTP CRUD /parcelas/observacoes -> ParcelaObservacaoController
  ↓ ParcelaObservacaoService [Tx ou RO]
  ├─ ParcelaRepositorio (criar/listar)
  └─ ParcelaObservacaoRepositorio -> PostgreSQL
       └─ callbacks prePersist/preUpdate
  ↑ DTO / 204

ENCERRAR
POST .../quitar ou .../refinanciar -> EmprestimoController
  ↓ EmprestimoService -> finalizarEmprestimo [transação do método público]
  ├─ EmprestimoRepositorio.buscarComParcelas
  ├─ ParcelaRepositorio.saveAll
  └─ EmprestimoRepositorio.save -> PostgreSQL
  ↑ 200; sem novo contrato, gateway ou evento
```

## 22. Código potencialmente legado/não referenciado

As observações abaixo vêm da busca de referências e leitura contextual; não são prova universal de código morto, pois DI, reflection, consumidores HTTP e framework podem carregar/invocar componentes sem chamada direta.

| Elemento / arquivo | Evidência e qualificação |
|---|---|
| `principal/Principal.java` | Bean ativo com showMenu só imprime uma mensagem; nenhuma chamada ativa encontrada. Menu antigo e CRUD Scanner estão comentados, usam assinaturas sem perfil e referenciam PagamentoMenuService não presente nas fontes atuais. Há indícios de migração de interface de terminal para HTTP. |
| `services/perfil/PerfilHeaderService.java` | @Service registrado, resolverPerfil sem chamadas ativas; não centraliza automaticamente os headers. |
| ClienteService.buscarPorNome | Sem chamadas ativas encontradas, referências do menu comentado. |
| EmprestimoService.listarParcelas | Sem chamadas ativas encontradas; endpoint usa listarParcelasDoCliente; menu comentado usa assinatura antiga. |
| EmprestimoService.buscarEmprestimoPorId | Helper privado sem chamada encontrada; usa repository.findByIdAndPerfilId. |
| ParcelaService.alterarDataVencimento | Método público sem chamada ativa/endpoint próprio encontrado; há alterarParcela com data+valor. |
| ClienteRepositorio.buscarClienteComEmprestimos | JPQL declarado sem chamada ativa encontrada; framework ainda pode materializar/validar consulta no bootstrap. |
| EmprestimoRepositorio.findByClienteAndPerfilId | Método derivado sem chamada ativa encontrada; variante findAllBy... com EntityGraph é utilizada. |
| ParcelaRepositorio.findByIdAndPerfilId, findParcelasEmAbertoVencidas | Sem chamadas ativas encontradas; métodos válidos de repository podem participar de usos futuros/externos ao corpus. |
| exceptions/EmprestimoQuitadoException.java | Não encontrei instanciações ativas; aparece em catch comentado. Service usa RuntimeException genérica para quitado. |
| exceptions/RegraNegocioException.java | Não encontrei instanciações/chamadas ativas; @ResponseStatus existe, mas sem fluxo produtor identificado. |
| models/EmprestimoStatus.java: QUITADO_TERCEIRO | Persistível/serializável e reconhecido na importação; sem endpoint específico ou regra de identidade do terceiro. Não chamar valor morto. |
| ControleClientesApplication.java | Imports sem uso ativo: CommandLineRunner, Autowired e serviços; não configuram execução do menu. |
| CalculadoraEmprestimoService.gerarParcelas | Juros/amortização/saldo computados localmente, sem uso no objeto persistido/retornado. |
| RelatorioService / DTOs | Histórico não trata PARCIAL; contraste com detalhes/inadimplentes/exportação; comportamento observado, intenção precisa de confirmação. |
| Importação/exportação | Layouts incompatíveis e campos ausentes; intenção de backup restaurável precisa de confirmação. |
| Frontend complementar services | Há declarações de rotas não encontradas no backend; uso atual dessas funções precisa de confirmação. |

Não foram encontrados TODO/FIXME ativos explícitos nas fontes Java; há comentário antigo “Adicionar Crud (DELETE, EDIT)” dentro do menu comentado. Não há serviços duplicados com mesma interface/qualifier identificados. Existem lógicas semelhantes de totalização/status em EmprestimoService e ParcelaService, mas com diferenças reais, não implementações intercambiáveis.

## 23. Arquivos mais importantes

Todos os caminhos são completos em relação à raiz do backend (não apenas nomes de classes). A ordem sugerida começa na configuração e segue para o domínio/serviço.

### Essenciais

- `pom.xml`: Java, parent Spring Boot, dependências, build único.
- `src/main/java/com/caio/controle_clientes/ControleClientesApplication.java`: entrypoint e raiz do component scan.
- `src/main/java/com/caio/controle_clientes/controllers/EmprestimoController.java`: contrato HTTP de contratos e relação ao cliente/perfil.
- `src/main/java/com/caio/controle_clientes/controllers/ParcelaController.java`: recebimentos, consultas com escrita e atualização de parcelas.
- `src/main/java/com/caio/controle_clientes/exceptions/GlobalExceptionHandler.java`: formato/status de falhas.

### Regras de negócio

- `src/main/java/com/caio/controle_clientes/services/emprestimo/EmprestimoService.java`: coração financeiro, totalizações, fechamento e reprogramação.
- `src/main/java/com/caio/controle_clientes/services/emprestimo/CalculadoraEmprestimoService.java`: fórmula, arredondamento e calendário normal.
- `src/main/java/com/caio/controle_clientes/services/parcela/ParcelaService.java`: atraso, estorno, edição, diferenças de recomposição.
- `src/main/java/com/caio/controle_clientes/services/ClienteService.java`: escopo/cadastro e bloqueio de exclusão.
- `src/main/java/com/caio/controle_clientes/services/RelatorioService.java`: agregações e diferenças com parciais.
- `src/main/java/com/caio/controle_clientes/services/parcela/ParcelaObservacaoService.java`: CRUD de notas com perfil.
- `src/main/java/com/caio/controle_clientes/services/perfil/PerfilService.java`: cadastro/lista/consulta de carteiras.

### Banco

- `src/main/java/com/caio/controle_clientes/models/Emprestimo.java`: agregados, enums e cascades.
- `src/main/java/com/caio/controle_clientes/models/Parcelas.java`: valores pagos, constraints e relação com notas.
- `src/main/java/com/caio/controle_clientes/models/Cliente.java`: dados cadastrais, perfil, cascade e JsonIgnore.
- `src/main/java/com/caio/controle_clientes/models/Perfil.java`: separação lógica das carteiras.
- `src/main/java/com/caio/controle_clientes/models/ParcelaObservacao.java`: texto, FKs e callbacks de datas.
- `src/main/java/com/caio/controle_clientes/repository/EmprestimoRepositorio.java`: EntityGraphs, deduplicação e inner fetch.
- `src/main/java/com/caio/controle_clientes/repository/ParcelaRepositorio.java`: recortes de cobrança, fetches e ordenação.
- `src/main/java/com/caio/controle_clientes/repository/ClienteRepositorio.java`: consultas de identidade/nome/perfil.
- `src/main/java/com/caio/controle_clientes/repository/PerfilRepositorio.java`: busca de nome de perfil.
- `src/main/java/com/caio/controle_clientes/repository/ParcelaObservacaoRepositorio.java`: acesso escopado às notas.
- `../backup.sql`: DDL de snapshot complementar; não tomar como migration ou estado vivo.

### Segurança

- `src/main/java/com/caio/controle_clientes/config/CorsConfig.java`: política de navegador; única configuração pertinente encontrada.
- `src/main/java/com/caio/controle_clientes/services/perfil/PerfilHeaderService.java`: resolvedor não referenciado ativamente.
- `src/main/java/com/caio/controle_clientes/controllers/ClientController.java`: exemplo de header obrigatório e @Valid.
- `../frontend/src/services/apiClient.ts`: consumidor adiciona perfil de localStorage; sem prova de identidade no Java.

### Integrações e arquivos

- `src/main/java/com/caio/controle_clientes/controllers/ImportController.java`: multipart e mascaramento local de exceções.
- `src/main/java/com/caio/controle_clientes/services/ImportService.java`: fachada delegadora curta.
- `src/main/java/com/caio/controle_clientes/services/importService/ImportacaoPlanilhaService.java`: orquestração, deduplicação, gravação/transação/logs.
- `src/main/java/com/caio/controle_clientes/services/importService/ImportacaoLinhaMapper.java`: layout posicional/defaults.
- `src/main/java/com/caio/controle_clientes/services/importService/ImportacaoCellReader.java`: formatos e fallback de conversão.
- `src/main/java/com/caio/controle_clientes/services/importService/ImportacaoValidator.java`: regras específicas da planilha.
- `src/main/java/com/caio/controle_clientes/services/importService/ImportacaoParcelaFactory.java`: reconstrução de pagos e calendário importado.
- `src/main/java/com/caio/controle_clientes/services/ExportService.java`: layout de saída e agregações de backup.
- `src/main/java/com/caio/controle_clientes/controllers/BackupController.java`: MIME/download/headers.

### Infra/configuração e testes

- `src/main/resources/application.properties`: datasource/ddl/logs; contém configuração de credencial cujo valor não deve ser copiado.
- `Dockerfile`: build Maven sem execução de testes e runtime Java.
- `.mvn/wrapper/maven-wrapper.properties`: versão do wrapper/distribuição Maven.
- `../docker-compose.yml`: topologia complementar com banco/backend/frontend; credenciais omitidas do relatório.
- `../frontend/nginx.conf`: prefixo externo /api e proxy.
- `src/test/java/com/caio/controle_clientes/ControleClientesApplicationTests.java`: única verificação aparente de contexto.

## 24. Pontos que não puderam ser determinados

Para os itens abaixo aplica-se explicitamente: **“Não foi possível determinar com segurança pelo código analisado.”**

1. Estado atual do banco, dados existentes, versão PostgreSQL efetiva e correspondência integral com o dump complementar.
2. Sucesso do build, do startup, dos testes e das consultas JPQL no runtime resolvido; nenhuma execução foi realizada.
3. Versões transitivas efetivamente resolvidas de Spring Framework, Spring Data, Hibernate, Jackson, Lombok e bibliotecas de teste.
4. Formato final de respostas para todos os erros de binding/transporte e serialização de entidades/proxies/ciclos em cada estado de dados.
5. Política de autenticação, TLS, autorização ou firewall eventualmente aplicada fora do código por infraestrutura de implantação.
6. Limites efetivos de upload, timeouts, tamanho de pool, timezone de produção, configuração JVM e política de logs externa.
7. Persistência parcial/rollback efetivo em cada modalidade de falha da importação e garantia de atomicidade sob concorrência.
8. Intenção de negócio sobre refinanciamento sem novo contrato, contagem de parciais, estorno com data antiga, alteração do período e calendários divergentes.
9. Layout de planilha externo originalmente pretendido e procedimento de transformação para restaurar um export; não foi fornecida planilha de referência.
10. Uso efetivo de classes/métodos sem referências ativas e de rotas divergentes declaradas no frontend.
11. Identidade de terceiro no status QUITADO_TERCEIRO: não há entidade/atributo/endpoint para isso.
12. Cobertura percentual de testes ou desempenho/volume suportado; sem relatório/métricas disponíveis.

**Partes fora da análise integral:** backend Java próprio foi integralmente lido; bibliotecas de terceiros/artefatos binários/código gerado não foram auditados; frontend irmão foi analisado apenas no consumo HTTP/infra/contratos, sem auditoria completa dos componentes de UI; dados de negócio e credenciais do dump não foram analisados/reproduzidos. Não há módulos Java adicionais identificados dentro desta raiz Git.

**Preservação:** somente este documento foi criado. Não foram propostas correções como se tivessem sido aplicadas; as divergências descritas são contexto do comportamento existente.

## 25. Índice rápido para outra IA

| Necessidade | Seção / arquivo inicial |
|---|---|
| Entender stack e escopo | 1–4; pom.xml e ControleClientesApplication |
| Descobrir capacidade de negócio | 5 |
| Encontrar endpoint e HTTP status | 6 (E01–E32) e 7 |
| Criar contrato / fórmula / datas | 8.3–8.4, 19.1; CalculadoraEmprestimoService/EmprestimoService |
| Pagar/parcial/quitar/refinanciar | 8.4, 19.2, 19.6; EmprestimoService |
| Estorno/editar/atrasos | 8.5, 19.3–19.5; ParcelaService |
| Notas de parcela | 8.6, 10.5, 19.7 |
| Histórico/inadimplentes | 8.7, E29–E30 |
| Importar/deduplicar/rollback | 8.8–8.9, 19.8; ImportacaoPlanilhaService |
| Layout do Excel / falta de round-trip | 8.9, 8.10, 19.9 |
| Entender queries/JPA | 9–10 |
| Entender campos de JSON | 11; DTOs e particularidades de entidades |
| Entender seleção de perfil / segurança | 12; não presumir JWT/login |
| Entender erros | 13; GlobalExceptionHandler e catch de ImportController |
| Integrações / frontend consumidor | 14 |
| Eventos / jobs | 15–16; não identificados, callbacks JPA apenas |
| Configuração / execução em containers | 17 |
| Testes existentes e limites | 18 |
| Dependências / chamadas | 20–21 |
| Código duvidoso / não referenciado | 22 |
| Ordem de leitura das fontes | 23 |
| Incertezas e limites | 24 |

### 25.1 Inventário completo das fontes Java conferidas

Os caminhos abaixo são relativos à raiz; a lista registra cobertura de leitura e facilita navegação. Total: 68.

```text
src/main/java/com/caio/controle_clientes/ControleClientesApplication.java
src/main/java/com/caio/controle_clientes/config/CorsConfig.java
src/main/java/com/caio/controle_clientes/controllers/BackupController.java
src/main/java/com/caio/controle_clientes/controllers/ClientController.java
src/main/java/com/caio/controle_clientes/controllers/EmprestimoController.java
src/main/java/com/caio/controle_clientes/controllers/HomeController.java
src/main/java/com/caio/controle_clientes/controllers/ImportController.java
src/main/java/com/caio/controle_clientes/controllers/ParcelaController.java
src/main/java/com/caio/controle_clientes/controllers/ParcelaObservacaoController.java
src/main/java/com/caio/controle_clientes/controllers/PerfilController.java
src/main/java/com/caio/controle_clientes/controllers/RelatorioController.java
src/main/java/com/caio/controle_clientes/dto/Perfil/CriarPerfilRequest.java
src/main/java/com/caio/controle_clientes/dto/Perfil/PerfilResponse.java
src/main/java/com/caio/controle_clientes/dto/cliente/ClienteCreateDTO.java
src/main/java/com/caio/controle_clientes/dto/cliente/HistoricoClienteDTO.java
src/main/java/com/caio/controle_clientes/dto/cliente/InadimplenteDTO.java
src/main/java/com/caio/controle_clientes/dto/cliente/UpdateEnderecoDTO.java
src/main/java/com/caio/controle_clientes/dto/cliente/UpdateNomeClienteDTO.java
src/main/java/com/caio/controle_clientes/dto/emprestimo/EmprestimoCreateDTO.java
src/main/java/com/caio/controle_clientes/dto/emprestimo/EmprestimoDetalhadoDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/AlterarParcelaDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/CriarObservacaoParcelaDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/DesfazerPagamentoParcelaDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/EditarObservacaoParcelaDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/PagarParcelaDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/PagarParcelaParcialDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/ParcelaObservacaoResponseDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/ParcelaResponseDTO.java
src/main/java/com/caio/controle_clientes/dto/parcela/ReprogramarParcelasDTO.java
src/main/java/com/caio/controle_clientes/exceptions/EmprestimoQuitadoException.java
src/main/java/com/caio/controle_clientes/exceptions/GlobalExceptionHandler.java
src/main/java/com/caio/controle_clientes/exceptions/ParcelaPagaException.java
src/main/java/com/caio/controle_clientes/exceptions/RegraNegocioException.java
src/main/java/com/caio/controle_clientes/models/Cliente.java
src/main/java/com/caio/controle_clientes/models/Emprestimo.java
src/main/java/com/caio/controle_clientes/models/EmprestimoStatus.java
src/main/java/com/caio/controle_clientes/models/FormaPagamento.java
src/main/java/com/caio/controle_clientes/models/FrequenciaPagamento.java
src/main/java/com/caio/controle_clientes/models/ParcelaObservacao.java
src/main/java/com/caio/controle_clientes/models/Parcelas.java
src/main/java/com/caio/controle_clientes/models/ParcelaStatus.java
src/main/java/com/caio/controle_clientes/models/Perfil.java
src/main/java/com/caio/controle_clientes/models/TipoContrato.java
src/main/java/com/caio/controle_clientes/principal/Principal.java
src/main/java/com/caio/controle_clientes/repository/ClienteRepositorio.java
src/main/java/com/caio/controle_clientes/repository/EmprestimoRepositorio.java
src/main/java/com/caio/controle_clientes/repository/ParcelaObservacaoRepositorio.java
src/main/java/com/caio/controle_clientes/repository/ParcelaRepositorio.java
src/main/java/com/caio/controle_clientes/repository/PerfilRepositorio.java
src/main/java/com/caio/controle_clientes/services/ClienteService.java
src/main/java/com/caio/controle_clientes/services/ExportService.java
src/main/java/com/caio/controle_clientes/services/ImportService.java
src/main/java/com/caio/controle_clientes/services/RelatorioService.java
src/main/java/com/caio/controle_clientes/services/emprestimo/CalculadoraEmprestimoService.java
src/main/java/com/caio/controle_clientes/services/emprestimo/EmprestimoService.java
src/main/java/com/caio/controle_clientes/services/importService/ImportacaoCellReader.java
src/main/java/com/caio/controle_clientes/services/importService/ImportacaoLinhaMapper.java
src/main/java/com/caio/controle_clientes/services/importService/ImportacaoParcelaFactory.java
src/main/java/com/caio/controle_clientes/services/importService/ImportacaoPlanilhaService.java
src/main/java/com/caio/controle_clientes/services/importService/ImportacaoValidator.java
src/main/java/com/caio/controle_clientes/services/importService/dto/ErroImportacao.java
src/main/java/com/caio/controle_clientes/services/importService/dto/ImportacaoResultado.java
src/main/java/com/caio/controle_clientes/services/importService/dto/LinhaImportacaoDTO.java
src/main/java/com/caio/controle_clientes/services/parcela/ParcelaObservacaoService.java
src/main/java/com/caio/controle_clientes/services/parcela/ParcelaService.java
src/main/java/com/caio/controle_clientes/services/perfil/PerfilHeaderService.java
src/main/java/com/caio/controle_clientes/services/perfil/PerfilService.java
src/test/java/com/caio/controle_clientes/ControleClientesApplicationTests.java
```

### 25.2 Segunda passagem de validação

Foi feita nova busca nas fontes por controllers/mappings, services/components/repositories, configurações/beans, entidades e herança JPA, advices/handlers, transações, annotations de segurança, filtros, clients, listeners, scheduling e async. Também foram procuradas implementações de interfaces e referências aos elementos possivelmente não utilizados. A comparação com o documento confirmou os 9 controllers/32 mappings, 11 services, 5 repositories, 5 entidades, configuração CORS, advice, componentes de importação e callbacks JPA. Ausências relevantes estão explicitadas nas seções 12, 14–16 e 22; inventário de fontes está acima. Essa verificação é estática e não substitui execução de testes.

Conferência mecânica do documento: 25 seções principais, 32 fichas de endpoints e presença dos caminhos de todas as 68 fontes Java. Busca final confirmou 21 annotations transacionais, 6 handlers, 1 bean CORS e nenhuma annotation/cliente dos grupos de segurança, scheduling, async ou mensageria pesquisados. Hashes SHA-256 dos 76 arquivos versionados foram comparados antes/depois da criação do relatório, sem diferenças; o único arquivo novo identificado pelo Git foi CONTEXTO_API_JAVA.md.
