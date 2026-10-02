# Research: API interna de canal do Telegram para orquestrador externo

**Feature**: `002-api-interna-canal-telegram` | **Data**: 2026-10-02 | **Plano**: [plan.md](./plan.md)

Este documento resolve as incógnitas do "Technical Context" do plano e registra as decisões de
desenho que a spec deixou para o plano (`Assumptions` e "Pontos a detalhar"). Formato: Decisão /
Justificativa / Alternativas consideradas. A constituição vigente é a v1.4.0. Não restou nenhum
`NEEDS CLARIFICATION`.

---

## R-01. Reversão de R-04 da feature 001: o n8n volta, só como transporte do Telegram

- **Decisão**: **revogar R-04** de `specs/001-fluxo-relatorios/research.md` ("webhook direto no
  backend, sem orquestrador") na parte "não há n8n". O n8n passa a ser o transporte do canal
  Telegram: recebe o update, o entrega ao backend e executa no chat as ações que o backend devolver.
  O backend continua sendo o único que interpreta a conversa, autoriza e escreve no banco. Permanecem
  válidos de R-04: o `secret_token` validado em tempo constante, a deduplicação por `update_id` e o
  adaptador de entrada que só traduz (Princípio I). O caminho antigo (`/telegram/webhook` + envio
  direto) **continua funcionando** nesta feature; sua remoção é da feature `003`.
- **Justificativa**: a constituição v1.4.0 (Princípio II) admitiu o n8n **somente** nesse papel, por
  decisão do dono do produto. A razão original de R-04 era evitar "a API interna paralela à pública
  sem checagem de dono" e "o token de serviço estático" do MVP. Esses riscos são tratados aqui, e não
  ignorados: (a) a API interna **não tem regra de negócio própria**, chama o mesmo interpretador do
  webhook (R-02) e a autorização de dono continua nos casos de uso; (b) o token de serviço vale só em
  `/interno/**`, é comparado em tempo constante, aceita rotação sem indisponibilidade e nunca vem do
  código (R-09); (c) o n8n nunca grava dados nem decide resposta.
- **Alternativas consideradas**: *manter R-04 (sem n8n)* (contraria a decisão de produto já
  incorporada à constituição e à spec); *n8n com lógica de conversa nos workflows* (proibido pelo
  Princípio II e foi a causa das lições 7 e 8 do MVP); *long polling no backend* (resolve só o
  transporte de entrada, não a saída para o orquestrador).
- **Efeito em artefatos da 001** (anotar na implementação, tarefa de documentação, sem alterar o
  histórico): `research.md` R-04, `plan.md` (Summary, Constitution Check II, Complexity Tracking) e
  `contracts/telegram-conversa.md` §1 passam a apontar para esta feature quanto ao transporte.

## R-02. Porta de entrada "canal Telegram" independente de transporte

- **Decisão**: extrair do `TelegramWebhookControlador` o que não é HTTP para um
  `ProcessadorUpdateTelegramServico` (pacote `entrada.telegram`), que: lê o JSON do update (mesmo
  `JsonMapper` tolerante), registra o `update_id` (`RegistroUpdatesTelegram`), chama
  `InterpretadorConversaTelegram.interpretar` e, diante de `RuntimeException`, produz a resposta
  `ERRO_INTERNO` (a lógica hoje em `respostaDeErro`). Devolve um `ResultadoUpdateTelegram(updateId,
  repetido, List<SaidaTelegram>)`. Dois controladores o usam, sem regra própria:
  1. `TelegramWebhookControlador` (existente): serializa nada; envia cada `SaidaTelegram` por
     `EnvioTelegram`, exatamente como hoje.
  2. `CanalTelegramControlador` (novo, `/interno/v1/canal/telegram/atualizacao`): converte as
     `SaidaTelegram` em DTOs v1 (R-03), grava as ações (R-05) e as devolve no corpo.
- **Justificativa**: FR-005 e FR-030 exigem comportamento idêntico; a única forma de garantir isso
  sem duplicar é uma só chamada ao interpretador. O serviço é de aplicação (`*Servico`), não um caso
  de uso: ele não representa ação de negócio, só orquestra a entrada (Princípio VI).
- **Alternativas consideradas**: *o controlador interno chamar o webhook por HTTP interno* (acopla
  dois controladores e duplica a validação de segredo); *copiar o corpo do `receber`* (duas versões
  da regra "toda mensagem recebe resposta", FR-008, que divergiriam); *mover o interpretador para o
  núcleo* (ele depende de `SaidaTelegram`, tipo de apresentação do canal; fica fora do escopo e do
  Princípio VII).

## R-03. Formato v1 das ações e serialização na camada de entrada

- **Decisão**: DTOs imutáveis no pacote `entrada.telegram.canal`, com sufixo `Dto`/`Requisicao`/
  `Resposta` (Princípio VI), serializados por Jackson. Contrato normativo em
  [contracts/acoes-v1.schema.json](./contracts/acoes-v1.schema.json) (JSON Schema 2020-12) e
  [contracts/canal-telegram-interno.md](./contracts/canal-telegram-interno.md). Pontos de desenho:
  - Corpo de resposta: `{"versao":"v1","updateId":N,"loteId":"u<N>","acoes":[…]}`; a versão está no
    corpo (FR-003).
  - Tipos de ação: `MENSAGEM`, `EDICAO`, `DOCUMENTO`, `RESPOSTA_CALLBACK`, `MENSAGEM_ETAPA`,
    espelho 1:1 de `SaidaTelegram` (FR-002). Texto já escapado para `parse_mode=HTML`; botões como
    `{rotulo, dados}`; `dados` ≤ 64 caracteres.
  - **A divisão de textos longos acontece no mapeador** (`ApresentadorTelegram.dividir`, limite 4096),
    emitindo uma `MENSAGEM` por parte, com o teclado só na última. Hoje a divisão é feita dentro de
    `MensageriaTelegramAdaptador.enviarMensagem`; no v1 o orquestrador não pode ter lógica (Princípio
    II), e a spec (US1, cenário 4) pede as partes já divididas na resposta. O envio direto antigo
    segue dividindo por conta própria (comportamento inalterado).
  - O `acaoId` é atribuído pelo mapeador **depois** da expansão, pela posição (R-04).
  - Evolução: campos opcionais e tipos novos são aditivos e ficam na v1; o receptor ignora campos
    desconhecidos e responde `ACAO_NAO_SUPORTADA` a tipos desconhecidos (FR-003a). O schema das
    nossas saídas usa `additionalProperties: false` para que um campo acidental quebre o teste de
    contrato, e o contrato obriga o **receptor** a ignorar campos desconhecidos.
  - Versão incompatível futura: novo prefixo de rota (`/interno/v2/…`) mantendo `/v1` enquanto o
    orquestrador não migrar (FR-003).
- **Justificativa**: serializar na borda mantém o interpretador e o núcleo sem conhecer o v1 e deixa
  o formato testável por schema. Espelhar `SaidaTelegram` evita um segundo modelo de conversa.
- **Alternativas consideradas**: *expor `reply_markup` e `parse_mode` no formato nativo do Telegram*
  (amarra o contrato ao Telegram e dificulta outro transporte); *deixar o n8n dividir textos longos*
  (lógica no orquestrador, proibida); *anotar `SaidaTelegram` com Jackson e serializá-lo direto*
  (vaza o tipo interno para o contrato e impede a versão).

## R-04. Identificadores estáveis, deduplicação e reentrega de resposta a update

- **Decisão**:
  - `acaoId` de resposta a update = `u<update_id>-<posicao>` (posição 0-based na lista **expandida**),
    determinístico e igual em todas as entregas do mesmo update (FR-007a). Compensações acrescentam
    `-c` ao id da ação que falhou (R-07).
  - Reaproveitar `update_telegram_processado` (V2) como a "chave de idempotência" do update, com duas
    colunas novas (V6): `total_acoes` (nulo = ainda processando) e `origem` (`INTERNO`; nulo nas
    linhas do webhook antigo). Fluxo da rota interna:
    1. `registrarSeNovo(update_id)` (já existente, `ON CONFLICT DO NOTHING`);
    2. **novo**: interpreta, mapeia e, **numa transação só**, grava as ações em `acao_canal` com estado
       `ENTREGUE` e preenche `total_acoes`;
    3. **repetido**: se `total_acoes` está preenchido, devolve as ações do update ainda **não
       confirmadas** (estado `ENTREGUE`; `PENDENTE` não ocorre nesta origem), na mesma ordem e com os
       mesmos ids; lista vazia se todas foram confirmadas (FR-007);
    4. **repetido em paralelo** (`total_acoes` nulo): espera curta por polling (até 3 s, intervalo de
       100 ms) e então se comporta como o item 3;
    5. **processamento perdido** (`total_acoes` nulo, `origem=INTERNO`, linha com mais de 30 s): o
       backend caiu entre gravar o estado e gravar as ações. O estado da conversa já foi gravado e
       não pode ser reinterpretado; responde-se `ERRO_INTERNO` ao chat (ids `u<N>-e0`) para o professor
       nunca ficar sem resposta (FR-008). Linhas com `origem` nula (webhook antigo) devolvem lista
       vazia.
  - Concorrência: o `ON CONFLICT` garante um único processamento; a janela entre o passo 1 e o passo 2
    é a única em que existe a situação 4 ou 5.
- **Justificativa**: o interpretador grava o estado ao interpretar (FR-010), então reprocessar um
  update é proibido; só as ações podem ser reentregues, e para isso precisam estar persistidas. Gravar
  ações e `total_acoes` juntos evita que um update repetido veja "processado, mas sem ações".
- **Alternativas consideradas**: *envolver interpretação + ações numa única transação* (os casos de
  uso já têm transações próprias, algumas `REQUIRES_NEW`, e o interpretador captura exceções para
  responder `ERRO_INTERNO`; uma transação externa mudaria o comportamento de commit parcial e quebraria
  a paridade com o webhook); *regenerar a resposta no repetido* (o estado já mudou, daria outra
  resposta); *devolver sempre a lista completa* (duplicaria mensagens já executadas).

## R-05. Outbox em tabela própria, reaproveitando a técnica da fila Postgres

- **Decisão**: uma tabela `acao_canal` (V6) guarda **todas** as ações de canal: respostas a update,
  proativas e compensações. Colunas e estados em [data-model.md](./data-model.md). A **fila de
  entrega** reaproveita a técnica de `trabalho_processamento` (R-06 da 001): reserva em transação
  curta com `FOR UPDATE SKIP LOCKED` e *lease* (`proxima_tentativa_em` empurrada para o futuro),
  HTTP **fora** da transação, resultado gravado em outra transação. Reaproveitamos também
  `update_telegram_processado` (R-04) e o mesmo padrão de tarefa agendada (`@Scheduled`, `@Profile
  ("!carga")`, threads virtuais). **A tabela de trabalhos de IA não é reutilizada.**
- **Ordem por chat (FR-016)**: as ações têm `seq` crescente. Um lote (`lote_id` = um evento) só é
  reservado quando **nenhuma ação anterior do mesmo chat** está `PENDENTE` ou `ENTREGUE` há menos de
  `confirmacao-max-segundos` (padrão 60). Isto é, a entrega é **serial por chat e condicionada à
  confirmação** do lote anterior, com temporização para que um orquestrador que aceitou e nunca
  confirmou não trave o chat. A mesma regra vale entre a resposta a update (já `ENTREGUE`) e as
  proativas do mesmo chat. Chats diferentes seguem em paralelo.
- **Idempotência da criação (FR-014)**: o `acaoId` proativo é derivado do evento
  (`p:<evento>:<pos>`, com evento `resultado:<trabalhoId>`, `etapa:<trabalhoId>:<ETAPA>`,
  `demora:<trabalhoId>`); `PRIMARY KEY (id)` + `ON CONFLICT DO NOTHING` impede duas ações para o mesmo
  evento, mesmo se o trabalho for notificado duas vezes (ex.: `recuperarTravados`).
- **Justificativa**: a spec deixou ao plano decidir entre reaproveitar `trabalho_processamento` ou
  tabela própria. `trabalho_processamento` tem semântica de **trabalho de IA** (tipo, etapa, índices
  únicos parciais por aluno, `canal_solicitacao`, worker que chama provedores); misturar um segundo
  ciclo de vida criaria condicionais em todo o código que a lê (`TrabalhoWorker`, retomada,
  relatórios em aberto). Tabela própria isola o risco e permite expurgo e índices próprios. A fila
  Postgres continua sendo a única infraestrutura de fila (sem broker, Princípio VII).
- **Alternativas consideradas**: *reutilizar `trabalho_processamento` com novo `tipo`* (descrito
  acima); *broker/Redis* (mais um serviço, sem ganho neste volume); *entrega em memória com retry*
  (perde ações num reinício, contraria FR-012); *entrega serial global* (um chat com problema travaria
  todos).

## R-06. Despacho HTTP ao webhook do n8n: timeouts, retentativa e idempotência

- **Decisão**: `OrquestradorN8nAdaptador` (implementa `OrquestradorCanalPorta`, saída) usa o
  `java.net.http.HttpClient` já adotado em `MensageriaTelegramAdaptador`/`ClienteHttpIa`, sem nova
  dependência. `POST {orquestrador-url}` com o lote (`LoteEntrega`, v1), cabeçalhos
  `X-Canal-Entrega-Token` (segredo do **destino**, de ambiente) e `Idempotency-Key: <loteId>`.
  - Timeouts: conexão 3 s, resposta 10 s (configuráveis). Sem seguir redirecionamentos.
  - Aceite = qualquer `2xx`. Qualquer outra coisa (timeout, `5xx`, `4xx`, erro de conexão) = recusa:
    a ação continua `PENDENTE` e é reagendada. `4xx` (exceto 408/429) é registrado com o código de
    situação, porque indica erro de configuração, mas **não** encerra as tentativas (FR-012).
  - Política: intervalo inicial 30 s, dobrando a cada tentativa, teto 15 min, prazo total 24 h desde a
    criação, com jitter de ±10% (injetável; zero nos testes). Todos os valores configuráveis (FR-018).
    Ao passar do prazo: `FALHA_DEFINITIVA`, log de situação sem dados de aluno e métrica.
  - Idempotência de ponta a ponta: o `acaoId` não muda entre tentativas; o contrato obriga o receptor
    a tratar repetições como a mesma ação (FR-013). Para o n8n **sem estado** (Princípio II), o contrato
    oferece `GET /interno/v1/canal/telegram/acoes/{acaoId}`: se o estado já é `EXECUTADA` ou `FALHOU`, o
    workflow pula a execução. A janela residual (executou no Telegram e caiu antes de confirmar) é a
    registrada em `Assumptions` da spec.
  - Resolução tardia da mensagem de etapa: ao serializar uma ação `MENSAGEM_ETAPA` com `trabalhoId`, o
    adaptador preenche `editarMensagemId` com o id gravado no trabalho (`MENSAGEM_ETAPA`) no momento do
    despacho (R-07). A ordem por chat garante que a confirmação do envio anterior já registrou o id.
  - A URL do orquestrador é fixa por configuração; **nenhum** endereço vindo de payload é acessado
    (FR-023).
- **Justificativa**: o cliente JDK já está no projeto e atende timeouts e HTTP/1.1 simples; adicionar
  Resilience4j ou Spring Retry traria dependência para uma política de 10 linhas. A decisão de
  retentativa mora no núcleo (R-12), pura e testável; o adaptador só faz a chamada.
- **Alternativas consideradas**: *Resilience4j / Spring Retry* (dependência a mais, e o estado da
  retentativa precisa sobreviver a reinício, o que o banco já faz); *RestClient do Spring* (equivale,
  mas o projeto padronizou o cliente JDK nos adaptadores de saída); *confiar em `Idempotency-Key`
  sozinho* (o n8n não guarda estado; por isso o `GET` de estado).

## R-07. Confirmação de execução, mensagem de etapa e compensação

- **Decisão**: `POST /interno/v1/canal/telegram/confirmacoes` recebe uma lista de resultados
  (`acaoId`, `situacao` = `EXECUTADA` | `FALHOU`, `codigoMotivo`, `mensagemId`). Efeitos, todos no
  núcleo (`ConfirmarExecucaoAcoesCanalCasoDeUso`):
  - `EXECUTADA`: estado `EXECUTADA`; se a ação é `MENSAGEM_ETAPA` com `trabalhoId` e veio `mensagemId`,
    grava o id no trabalho (`FilaTrabalhoPorta.registrarMensagemEtapa`, novo método na porta; hoje só
    existe na classe concreta usada por `MensageriaTelegramAdaptador`). **Só depois** a ação é marcada
    `EXECUTADA`, para o portão de ordem (R-05) liberar o próximo lote já com o id gravado.
  - `FALHOU`: estado `FALHOU` com o código. Se o código é `MENSAGEM_NAO_EDITAVEL` e a ação guarda uma
    `compensacao`, cria **uma** nova ação (`<acaoId>-c`, origem `COMPENSACAO`, novo lote) com esse
    conteúdo. Demais códigos (`CHAT_INDISPONIVEL`, `ACAO_NAO_SUPORTADA`, `DOCUMENTO_INDISPONIVEL`,
    `ERRO_TELEGRAM`) só são registrados e contados.
  - Idempotente: repetir a mesma confirmação não muda nada; confirmação divergente da já registrada é
    ignorada e contada; `acaoId` desconhecido, `DESCARTADA` ou `FALHA_DEFINITIVA` vai para a lista
    `ignoradas` da resposta, sem erro.
- **Compensação montada na criação**: o mapeador (borda) já grava, junto da ação, o JSON da
  alternativa — `EDICAO` → `MENSAGEM` com o mesmo texto e teclado; `MENSAGEM_ETAPA` com
  `editarMensagemId` → `MENSAGEM_ETAPA` **sem** `editarMensagemId`. Assim o núcleo decide *quando*
  compensar sem conhecer o formato v1 (FR-007b: quem decide é o backend).
- **Notificação de etapa como `MENSAGEM_ETAPA`**: `NotificacaoTelegramAdaptador` hoje emite `Edicao`
  para as etapas e para o "Pronto". Passa a emitir `MensagemEtapa(chatId, id, texto, trabalhoId)`, que
  o envio direto trata como "edita no lugar e regrava o mesmo id" (comportamento equivalente ao atual)
  e que, no v1, é o único tipo cuja falha de edição gera nova mensagem **e atualiza o id do trabalho**
  (a edição seguinte passa a mirar a mensagem nova, sem acumular mensagens). Sem isso, uma mensagem
  apagada geraria uma mensagem nova a cada etapa.
- **Justificativa**: cobre FR-007a/b, FR-017 e a borda "mensagem de etapa apagada". O portão de ordem
  elimina a corrida em que a primeira edição de etapa sairia antes de o `mensagemId` ser conhecido (no
  envio direto o id é gravado de forma síncrona; com n8n há latência).
- **Alternativas consideradas**: *o n8n decidir a compensação* (proibido, FR-007b); *o núcleo conhecer
  `SaidaTelegram`* (inverteria a dependência); *reentregar a ação que falhou* (editar de novo uma
  mensagem apagada nunca terá sucesso).

## R-08. PDF por identificador opaco

- **Decisão**: tabela `documento_canal` (V6). O identificador é um token aleatório de 256 bits
  (`SecureRandom`, Base64url sem preenchimento, 43 caracteres); só o **hash SHA-256** fica no banco
  (como `sessao_web`). A linha guarda a referência ao documento (`professor_id`, `relatorio_tipo`,
  `relatorio_id`, `versao` nula para o oficial), o nome do arquivo e `expira_em`. Emissão: o mapeador
  emite um token por ação `DOCUMENTO`, com validade = prazo de entrega + 1 h (24 h + 1 h por padrão),
  cobrindo todas as tentativas (US4, cenário 4). Rota:
  `GET /interno/v1/canal/telegram/documentos/{documentoId}` → `ObterDocumentoRelatorioCasoDeUso`
  (`conferencia`/`oficial`, com o `professor_id` gravado, **repetindo** a checagem de dono e
  associação) → bytes com `application/pdf`, `Content-Disposition` com o nome gravado, `Cache-Control:
  no-store`. Desconhecido, vencido, malformado ou documento que sumiu: `404` sem corpo, indistinguíveis
  (FR-022); `5xx` só para falha real de leitura, nunca conteúdo parcial.
  - `SaidaTelegram.Documento` ganha uma `ReferenciaDocumento` (professor, tipo, relatório, versão ou
    oficial). Os apresentadores a preenchem. **O campo `conteudo` permanece** para o envio direto
    antigo (zero mudança em `MensageriaTelegramAdaptador`); o v1 ignora os bytes e o `DOCUMENTO`
    carrega só o id. A limpeza do campo fica para a feature `003`, quando o envio direto sair.
  - Nada em `documentoId` é interpretado como URL ou caminho (FR-023): o valor só é procurado por
    hash. Um `documentoId` fora do formato (43 caracteres Base64url) é recusado antes de ir ao banco.
- **Justificativa**: token aleatório com hash no banco é revogável, expirável e não deixa identificador
  derivável de relatório; é o mesmo padrão já adotado para sessão (R-12 da 001). Repetir a checagem de
  dono na leitura mantém o Princípio IV mesmo se uma linha for adulterada.
- **Alternativas consideradas**: *token sem estado assinado com HMAC (como JWT curto)* (menos uma
  tabela, mas expõe UUIDs no payload, ou exige cifrar além de assinar, e não é revogável);
  *copiar o PDF para uma tabela/volume do canal* (duplica dado de aluno menor de idade); *URL do
  portal com sessão* (o n8n não tem sessão, e o token de serviço não pode abrir o portal, FR-025).

## R-09. Autenticação serviço a serviço (`/interno/**`)

- **Decisão**: uma **segunda** `SecurityFilterChain` com `securityMatcher("/interno/**")` e `@Order(1)`;
  a cadeia atual do portal passa a `@Order(2)` e **não é alterada** (FR-027), mantendo o
  `denyAll()` final. Na cadeia interna: sem sessão (`STATELESS`), CSRF desligado **só nela** (a
  autenticação é por cabeçalho, sem cookie), `SessaoCookieFiltro` **não** é adicionado (o cookie do
  portal é ignorado, FR-025), CORS e `httpBasic`/`formLogin` desligados, `authenticationEntryPoint`
  devolvendo `401` sem corpo, e `anyRequest().hasAuthority("SERVICO_CANAL")`. O
  `TokenServicoFiltro` (`OncePerRequestFilter`) autentica com a autoridade `SERVICO_CANAL`,
  **inspirado** em `N8nServiceTokenFilter` do MVP (somente leitura), com estas diferenças:

  | `N8nServiceTokenFilter` (MVP) | `TokenServicoFiltro` (esta feature) |
  |---|---|
  | `String.equals` (curto-circuito, vaza o prefixo comum pelo tempo) | compara `SHA-256(recebido)` com `SHA-256(configurado)` por `MessageDigest.isEqual`, com digests de tamanho fixo (não vaza nem o tamanho) |
  | um único token | até **dois** (`atual` e `proximo`); **ambos** são comparados sempre, com `&` bit a bit (sem curto-circuito); se só um está configurado, o outro vira um digest-isca |
  | corpo `401` com mensagem "Token ... invalido" | `401` **sem corpo** |
  | token vazio é recusado, mas o bean aceita configuração ausente | nenhum token configurado = **toda** a área é recusada (fail-closed), e a aplicação loga um aviso na subida |
  | `ROLE_N8N_SERVICE`, válido em qualquer cadeia onde o filtro entrar | só existe na cadeia `/interno/**`; as rotas do portal não aceitam essa autoridade |

  - Cabeçalho: `X-N8N-Service-Token` (FR-024).
  - Segredo do webhook (`X-Telegram-Bot-Api-Secret-Token`) na rota de atualização: validado **dentro do
    controlador**, depois do token de serviço, pelo mesmo `ComparadorSegredo` (extraído do
    `segredoValido` do webhook; passa a comparar digests, e o webhook antigo herda o ganho). Falha =
    `401` sem corpo (FR-006).
  - Tokens vêm de `relatorios.canal-telegram.token-atual` / `token-proximo` (variáveis
    `CANAL_TELEGRAM_TOKEN_ATUAL`/`CANAL_TELEGRAM_TOKEN_PROXIMO`), nunca do código; `toString` mascarado
    como já fazem `Seguranca` e `Telegram` em `PropriedadesAplicacao`. Nenhum token aparece em log,
    ação ou erro (FR-026a/FR-029).
  - Rotação: adicionar o `proximo` → reconfigurar o n8n → promover (`atual ← proximo`, `proximo`
    vazio). Nenhuma janela de recusa (FR-026a).
- **Justificativa**: cadeia separada é a forma mais simples de garantir os dois isolamentos de uma vez
  (token não abre o portal, cookie não abre o interno) sem condicionais na cadeia existente, e deixa
  o CSRF do portal intacto. Comparar digests de tamanho fixo é o padrão para tempo constante em Java
  (`MessageDigest.isEqual` é tempo constante apenas para entradas de mesmo tamanho).
- **Alternativas consideradas**: *um único filtro na cadeia existente com ramo por caminho* (mistura
  credenciais e arrisca regressão de CSRF); *Spring Security `BearerTokenAuthenticationFilter`*
  (o cabeçalho exigido é `X-N8N-Service-Token`, não `Authorization`); *mTLS* (infraestrutura da `003`).

## R-10. Escolha do caminho de entrega proativa (exatamente um)

- **Decisão**: nova porta `PublicacaoAcaoTelegramPorta` (pacote `entrada.telegram`, ao lado de
  `EnvioTelegram`; mesmo padrão: a entrada define, a saída implementa), com
  `publicar(String eventoId, List<SaidaTelegram> saidas)`. Duas implementações em `saida.telegram`,
  selecionadas por `relatorios.canal-telegram.entrega-proativa` (`DIRETA` | `ORQUESTRADOR`, padrão
  `DIRETA`) via `@ConditionalOnProperty`, de modo que **só um bean existe** (FR-019):
  - `PublicacaoDiretaTelegramAdaptador`: o comportamento de hoje (chama `MensageriaTelegramAdaptador`,
    inclusive o registro do id da etapa).
  - `PublicacaoOutboxTelegramAdaptador`: mapeia para v1 e grava `acao_canal` com origem `PROATIVA`
    por `GravarAcoesCanalCasoDeUso` (idempotente, R-05).

  `NotificacaoTelegramAdaptador` deixa de depender de `MensageriaTelegramAdaptador` e passa a depender
  da porta. O padrão `DIRETA` preserva o comportamento enquanto o orquestrador não existe (spec,
  `Assumptions`). Com `ORQUESTRADOR`, a subida **falha** se `orquestrador-url` estiver vazio.
  A tarefa de despacho só roda se houver `orquestrador-url`, porque as compensações (R-07) de uma
  conversa atendida pelo n8n são sempre entregues ao orquestrador, mesmo com `DIRETA`.
- **Justificativa**: o pedido manda o `NotificacaoTelegramAdaptador` publicar numa porta de saída que
  grava em outbox; a spec (FR-019) acrescenta que o caminho atual continua padrão e que não pode haver
  envio duplicado nem perda. Duas implementações de uma porta é o desenho mais simples que cumpre as
  duas coisas.
- **Alternativas consideradas**: *um `if` em runtime dentro do adaptador* (as duas implementações
  ficariam vivas e o "exatamente um" só seria convenção); *remover o envio direto agora* (fora do
  escopo, é a `003`).

## R-11. Privacidade: texto de aluno na outbox e nos logs

- **Decisão**: a ação carrega, por definição, o texto que o professor verá (nomes de aluno, trechos de
  relatório). Portanto FR-029 é cumprido assim: (a) **logs, métricas, mensagens de erro e o corpo
  de `401/404/5xx`** nunca contêm payload, texto, nome, CPF nem áudio, só `acaoId`, `loteId`,
  `updateId`, estado, código de situação e contagens; (b) o payload em `acao_canal` existe só enquanto
  é necessário e é **zerado** (`payload = null`, `compensacao = null`) `retencao-payload-horas` (padrão
  24 h) depois do estado final; a linha de metadados some após `retencao-registro-dias` (padrão 7,
  igual a `update_telegram_processado`); (c) `documento_canal` guarda só referência e hash, nunca
  bytes; (d) o filtro `MascaramentoDadosSensiveisConversor` já existente continua como defesa em
  profundidade e um teste (`LogSemDadosSensiveisIT` estendido) varre os logs desta feature.
- **Ponto para o dono do produto** (registrado, sem bloqueio): a redação literal de FR-029 ("nenhuma
  **ação** ou **entrega pendente** … contém nome de aluno") é incompatível com entregar a mensagem, que
  precisa do texto. Este plano a lê como "nenhum dado de aluno **fora do que a própria mensagem exige**,
  nada em logs e retenção mínima". Se a leitura estiver errada, a alternativa é guardar só referências
  (tipo + `trabalhoId`) e **renderizar no despacho**, o que reabre o estado da conversa já gravado
  (foco) e foi rejeitado em R-04.
- **Justificativa**: o dado já está no banco do próprio sistema (relatórios), sob as mesmas proteções;
  o risco novo é duplicação e retenção, que (b) limita.
- **Alternativas consideradas**: *cifrar o payload em repouso* (acrescenta gestão de chave sem reduzir
  a exposição durante a janela de entrega; reavaliar se a retenção crescer); *renderizar no despacho*
  (acima).

## R-12. Onde moram as regras, para cumprir os 90% do núcleo (FR-032, Princípio V)

- **Decisão**: o JaCoCo (≥ 90% de linhas **e** branches) só vigia o módulo `nucleo`. Então as regras
  novas **ficam no núcleo**, como Java puro, e o `aplicacao` só traz adaptadores finos:
  - `dominio.canal`: `AcaoCanal` (estado e transições), `EstadoAcaoCanal`, `OrigemAcaoCanal`,
    `PoliticaRetentativa` (intervalo, teto, prazo), `DocumentoCanal` (validade).
  - `casodeuso.canal`: `GravarAcoesCanalCasoDeUso`, `ObterAcoesNaoConfirmadasDoUpdateCasoDeUso`,
    `ConfirmarExecucaoAcoesCanalCasoDeUso`, `EntregarAcoesPendentesCasoDeUso`,
    `EmitirDocumentoCanalCasoDeUso`, `ObterDocumentoCanalCasoDeUso`, `LimparAcoesCanalCasoDeUso`.
  - `porta`: `AcaoCanalRepositorio`, `DocumentoCanalRepositorio`, `OrquestradorCanalPorta`; mais
    `registrarMensagemEtapa` em `FilaTrabalhoPorta`.
  - O payload é uma `String` opaca no núcleo (o núcleo não conhece v1 nem Telegram além do que o
    pacote `dominio.telegram` já conhece), preservando o Princípio I e a regra "nenhum fornecedor no
    núcleo" (o n8n só aparece em `aplicacao`).
- **Justificativa**: sem isso, a parte mais arriscada da feature (ordem, retentativa, compensação,
  idempotência) ficaria sem o gate de cobertura que o Princípio V exige para "regra de negócio e casos
  de uso envolvidos".
- **Alternativas consideradas**: *deixar a lógica em `saida.fila` e estender o JaCoCo ao `aplicacao`*
  (muda o gate de todo o projeto e arrasta adaptadores de IA e PDF); *colocar tudo no adaptador* (a
  regra ficaria presa a Spring/JPA e sem teste unitário barato).

## R-13. Estratégia de testes

- **Decisão**:
  - **Conversa e paridade pela nova porta (FR-030, SC-001)**: o `TelegramConversaBase` ganha um
    **executor de transporte** substituível. Padrão `WEBHOOK` (hoje). Modo `INTERNO`: `enviarUpdate`
    faz `POST /interno/v1/canal/telegram/atualizacao` com o token e o segredo, **valida o corpo
    contra o schema**, e então um `ExecutorAcoesSimuladoTeste` percorre `acoes[]` em ordem e as traduz
    para as chamadas à API de bots no WireMock (`MENSAGEM`→`sendMessage`, `EDICAO`→`editMessageText`,
    `DOCUMENTO`→`GET` do PDF + `sendDocument`, `RESPOSTA_CALLBACK`→`answerCallbackQuery`,
    `MENSAGEM_ETAPA`→send/edit) e envia as confirmações. **Todas** as asserções existentes
    (`textosEnviados`, `ultimaMensagem`, `botoes`, `chamadas`) ficam como estão, porque o WireMock
    recebe as mesmas chamadas. O modo é escolhido pela propriedade de sistema
    `telegram.transporte` (`webhook`|`interno`); o Failsafe roda os ITs de conversa e de paridade em
    **duas execuções** (uma por modo) e o CI exige as duas. Alternativa avaliada e descartada:
    `@ParameterizedClass`, por interferir no cache de contexto do Spring e no ciclo de `@BeforeEach`.
  - **Contrato**: o JSON Schema valida (1) toda resposta de `/atualizacao`, (2) todo corpo enviado ao
    n8n (capturado no WireMock do orquestrador) e (3) as requisições/respostas de confirmação e
    estado, usando o `networknt json-schema-validator` já presente. O OpenAPI do canal
    ([contracts/canal-telegram-interno.openapi.yaml](./contracts/canal-telegram-interno.openapi.yaml))
    é validado com o `swagger-request-validator` já usado no portal.
  - **Segurança** (`SegurancaInternaIT`): matriz rota × credencial (sem, errada, correta, `proximo`,
    ambos, cookie de portal, token de serviço no portal) e "nenhum token configurado"; teste unitário
    do `ComparadorSegredo` (digests, tokens de tamanhos diferentes, nulos e isca).
  - **Entrega** (`EntregaAcoesCanalIT` com WireMock do n8n): indisponível → reentrega com ids
    iguais; `5xx`/timeout/`4xx`; prazo esgotado → `FALHA_DEFINITIVA`; ordem por chat; portão de
    confirmação e seu *timeout*; idempotência na criação; compensação; vínculo desfeito → `DESCARTADA`.
    O relógio é o `RelogioAjustavel` existente, para não dormir em teste.
  - **Concorrência**: dois `update_id` iguais em paralelo (`CountDownLatch`) → um só processamento;
    dois dispatchers → uma só entrega por lote (SKIP LOCKED).
  - **Arquitetura** (ArchUnit): as regras atuais continuam (`entradaNaoAcessaSaidaDiretamente` etc.);
    novas: controladores de `entrada.telegram.canal` terminam em `Controlador`, records de borda em
    `Dto`/`Requisicao`/`Resposta`, `entrada.interno` não depende de `saida`, e o núcleo continua sem
    Spring nem nome de fornecedor (`n8n` entra na lista `FORNECEDORES` de `RegrasArquiteturaTeste`).
  - **Privacidade**: `LogSemDadosSensiveisIT` estendido para o fluxo interno e a entrega; teste que
    confirma `payload` nulo após a retenção.
- **Justificativa**: trocar só o "executor" prova SC-001 sem reescrever nenhuma expectativa e deixa o
  simulador de n8n do teste como a especificação executável do que o workflow da `003` deve fazer.
- **Alternativas consideradas**: *duplicar os ITs de conversa para o interno* (divergiriam);
  *Testcontainers com n8n real* (fora do escopo e exigiria Docker em Docker, R-16 da 001).

## R-14. Migração Flyway V6 e retenção

- **Decisão**: **a outbox exige tabela**, então `V6__canal_telegram_interno.sql` cria `acao_canal` e
  `documento_canal` e acrescenta `total_acoes` e `origem` a `update_telegram_processado`. Sem
  alteração destrutiva (colunas novas nulas), compatível com o webhook antigo em execução durante a
  implantação. Expurgo: `LimpezaAcoesCanalTarefa` (diária, junto da janela de `VarreduraAcessoTarefa`)
  zera payloads vencidos (R-11), apaga linhas finais com mais de `retencao-registro-dias` e
  `documento_canal` vencido. Detalhes de colunas, índices e restrições: [data-model.md](./data-model.md).
- **Justificativa**: entrega com retentativa sobrevivendo a reinício (FR-012) exige persistência;
  `trabalho_processamento` foi descartada em R-05.
- **Alternativas consideradas**: *sem tabela, só `update_telegram_processado`* (não cobre ações
  proativas nem compensações).

## R-15. Observabilidade

- **Decisão**: métricas Micrometer (já expostas em `/actuator/prometheus`, rede interna):
  `canal_acoes_pendentes` (gauge), `canal_acao_pendente_mais_antiga_segundos` (gauge),
  `canal_entregas_total{resultado=aceita|recusada}`, `canal_acoes_falha_definitiva_total`,
  `canal_acoes_sem_confirmacao_total` (entregues e não confirmadas dentro de
  `confirmacao-max-segundos`), `canal_confirmacoes_total{situacao,codigo}`. Logs estruturados com
  `acaoId`/`loteId`/`updateId` e códigos (padrão `telegram-update-repetido updateId=…` já usado).
  A falha definitiva fica **visível ao mantenedor** pela métrica e por um log `WARN` por ação
  (FR-018, US3 cenário 6).
- **Justificativa**: as duas únicas situações que o professor não percebe (ação sem confirmação e falha
  definitiva) precisam ser percebidas pelo mantenedor.
- **Alternativas consideradas**: *tabela de alertas* (sem leitor definido; a métrica basta).

---

## Itens do Technical Context resolvidos

| Item | Resolução |
|---|---|
| Linguagem/framework | herdados da 001 (Java 25, Spring Boot 4.1, Maven multimódulo) |
| Formato exato do JSON v1 | R-03 e `contracts/acoes-v1.schema.json` |
| Rotas | `/interno/v1/canal/telegram/{atualizacao,confirmacoes,documentos/{id},acoes/{id}}` |
| Intervalos e prazo de retentativa | R-06 (30 s → 15 min, 24 h, jitter ±10%) |
| Emissão e expiração do identificador de documento | R-08 |
| Reaproveitar o interpretador sem duplicar regra | R-02 |
| Reaproveitar `trabalho_processamento` ou tabela própria | R-05 (tabela própria) |
| Dois tokens e tempo constante | R-09 |
| Exatamente um caminho proativo | R-10 |
