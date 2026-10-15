# Research: Fluxo de envio e criação de relatórios pedagógicos (Web e Telegram)

> **Nota de histórico (feature 003)**: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [`.specify/memory/glossary.json`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.

**Feature**: `001-fluxo-relatorios` | **Data**: 2026-09-30 | **Plano**: [plan.md](./plan.md)

Este documento resolve as incógnitas do "Technical Context" do plano. Cada item segue o formato
Decisão / Justificativa / Alternativas consideradas. A constituição (v1.3.0) é agnóstica de stack
e exige que as escolhas sejam registradas e justificadas aqui.

---

## R-01. Linguagem e framework do backend

- **Decisão**: Java 25 (LTS) com Spring Boot 4.1, em Maven multimódulo. O módulo `nucleo` (domínio,
  casos de uso e portas) é Java puro, sem Spring. O módulo `aplicacao` traz Spring Boot e os
  adaptadores.
- **Justificativa**: o MVP foi escrito em Java 21 com Spring Boot 3.3, e a equipe já domina essa
  stack, além das armadilhas registradas no handoff (seção 16). A linha 3.x perdeu o suporte OSS em
  junho de 2026. O Spring Boot 4.1 é a versão recomendada hoje (a 4.0 perde suporte em dezembro de
  2026) e tem suporte de primeira classe a Java 25. Com threads virtuais, o processamento
  assíncrono (R-06) fica simples, sem framework reativo. Separar `nucleo` em módulo próprio sem
  Spring faz o compilador impor a regra de dependência do Princípio I: o núcleo não tem como
  importar um adaptador.
- **Alternativas consideradas**:
  - *Kotlin + Spring*: ganho pequeno e custo de aprendizado para a equipe.
  - *Node.js/TypeScript (NestJS)*: permitiria uma só linguagem com o frontend, mas descartaria o
    conhecimento do MVP. As bibliotecas de PDF com CSS paginado também são menos maduras.
  - *Módulo único com ArchUnit*: mais simples, mas a regra de dependência ficaria só em teste.
    O ArchUnit continua sendo usado para as regras internas do núcleo (domínio → casos de uso).

## R-02. Frontend (Portal do Professor)

- **Decisão**: SPA em React 19 + TypeScript + Vite, React Router, TanStack Query (cache, polling
  com timeout e novas tentativas) e CSS próprio com os tokens do design system em custom
  properties. Sem biblioteca de componentes. Ícones Material Symbols Outlined e fontes Cinzel,
  Cormorant Garamond e Source Sans 3 servidos localmente (licença OFL/Apache), sem CDN.
- **Justificativa**: os protótipos (`Portal do Professor.dc.html`) já definem tokens (`--bg
  #FAF7F0`, `--tx #141414`, `--gd #B8955A`, `--gs #7A5C30` etc.), tipografia e componentes. Uma
  biblioteca de UI teria de ser sobrescrita para bater com o design pixel a pixel. O MVP já usava
  React e Vite. A UI visual do MVP não é reaproveitada, como exige a constituição, mas as lições de
  cliente são (polling com timeout, fetch autenticado de PDF). Servir as fontes localmente evita
  enviar dados de navegação a terceiros e deixa o PDF e a Web com as mesmas fontes.
- **Alternativas consideradas**: *Next.js/SSR* (o portal é autenticado e não precisa de SEO, então
  o servidor Node seria só complexidade extra); *Vue/Svelte* (sem vantagem que compense trocar de
  ecossistema); *Tailwind* (os tokens já existem como CSS puro nos protótipos).

## R-03. Banco de dados e persistência

- **Decisão**: PostgreSQL 18 com migrações Flyway. Os adaptadores de persistência usam Spring Data
  JPA (Hibernate), com entidades de persistência separadas das entidades de domínio e conversão
  por `*Mapeador`. As seções estruturadas dos relatórios ficam em `jsonb`. Unicidades de regra de
  negócio viram índices únicos parciais, além da validação no caso de uso. A concorrência usa
  bloqueio otimista (coluna `bloqueio_otimista`).
- **Justificativa**: o banco relacional é a fonte única da verdade (Princípio II), inclusive para o
  estado da conversa do Telegram, a fila de processamento (R-06) e o controle de tentativas de
  acesso. Com índices parciais, as regras "no máximo um relatório em aberto por professor e aluno"
  (FR-034) e "no máximo um semestral aprovado por aluno, professor e período" (FR-046) valem mesmo
  em corrida entre canais. O bloqueio otimista resolve a aprovação dupla (caso de borda "aprovação
  concorrente"). Para não repetir a lição 14 do MVP, os valores padrão são preenchidos na aplicação.
- **Alternativas consideradas**: *Spring Data JDBC ou jOOQ* (mapeamento explícito e sem surpresas,
  mas a equipe conhece JPA e o `*Mapeador` já isola o domínio); *MongoDB* (perderia as garantias
  transacionais e de unicidade entre entidades).

## R-04. Entrada do canal Telegram: webhook direto no backend, sem orquestrador

> **Nota (feature 002, sem reescrever histórico):** o transporte do Telegram agora é feito via n8n
> (constituição v1.4.0); ver `specs/002-api-interna-canal-telegram/research.md` R-01. O texto abaixo
> registra a decisão original da 001.

- **Decisão**: o backend recebe os updates do Telegram por webhook, num adaptador de entrada
  (`adaptadores.entrada.telegram`) que apenas traduz cada update para os mesmos casos de uso da Web.
  Não há n8n nem outro orquestrador. O webhook é registrado com `secret_token`, validado em tempo
  constante. Os updates são deduplicados por `update_id`.
- **Justificativa**: o handoff deixou isso como [DECISÃO PENDENTE] (seção 11.1) e aceita os dois
  caminhos. Sem orquestrador sobra um contêiner a menos, some o token de serviço estático (17.1,
  item 7) e some a API interna paralela à pública, que no MVP ficou sem checagem de dono (17.1,
  item 2). Pelo Princípio VII (simplicidade), vence a opção sem orquestrador. O Princípio II fica
  atendido por construção: não existe outro componente que possa escrever no banco. A paridade
  (Princípio I) fica mais fácil de garantir porque os dois controladores de canal chamam os mesmos
  casos de uso no mesmo processo.
- **Alternativas consideradas**: *n8n como no MVP* (fluxo visual, mas é mais um serviço, precisa
  de API interna e tende a acumular lógica, como mostram as lições 7 e 8); *long polling
  (`getUpdates`)* (dispensa webhook, mas prende uma thread ao ciclo de polling e fica fora do
  padrão de entrada HTTP dos outros adaptadores; continua possível como plano B, porque só muda o
  transporte do adaptador).

## R-05. Arquivos de até 50 MB no Telegram: servidor próprio da API de bots

- **Decisão**: subir o servidor oficial `tdlib/telegram-bot-api` num contêiner (serviço
  `telegram-bot-api`) em modo `--local`, só na rede interna. O backend usa esse servidor como URL
  base da API de bots e registra nele o webhook apontando para o backend pela rede interna. No
  modo local, `getFile` devolve o caminho absoluto do arquivo no disco do servidor e não há limite
  de download. O diretório de trabalho do servidor fica num volume compartilhado com o backend
  (montado no mesmo caminho), e o backend apaga o arquivo logo depois de copiá-lo para o
  processamento (R-07).
- **Justificativa**: é a clarificação da spec (limite de 50 MB nos dois canais, FR-015). A API de
  bots na nuvem limita o download a 20 MB. No modo local o limite some. Como o servidor local fala
  direto com o Telegram por MTProto, o backend não precisa ficar exposto à internet para receber o
  webhook, o que reforça o Princípio IV: na VPS, só o Caddy (HTTPS) fica público.
- **Implicações operacionais** (vão para o quickstart):
  - O servidor exige `TELEGRAM_API_ID` e `TELEGRAM_API_HASH` (obtidos em my.telegram.org), que são
    segredos de ambiente.
  - Antes de migrar um bot da nuvem para o servidor local, é preciso chamar `logOut` uma vez na API
    da nuvem.
  - O áudio aguardando confirmação do aluno (FR-040) **não é baixado**. O backend guarda só o
    `file_id`, e o arquivo continua nos servidores do Telegram até a confirmação. Assim, "guardado
    apenas temporariamente" quer dizer que nenhum byte de áudio fica no sistema antes da confirmação.
  - Uma varredura periódica apaga arquivos com mais de 30 minutos no volume do servidor local, como
    defesa em profundidade para FR-018.
- **Alternativas consideradas**: *limite de 20 MB no Telegram* (quebraria a paridade e foi recusado
  pelo dono do produto); *pedir ao professor que comprima o vídeo* (atrito que contraria a proposta
  de valor).

## R-06. Processamento assíncrono (transcrição, estruturação, alteração, consolidação)

- **Decisão**: uma fila de trabalhos persistida no PostgreSQL (tabela `trabalho_processamento`),
  consumida por workers no próprio backend com `SELECT … FOR UPDATE SKIP LOCKED` e threads
  virtuais. Cada trabalho tem tipo (`REGISTRO_AULA`, `RESPOSTA_PERGUNTAS`, `ALTERACAO`,
  `CONSOLIDACAO_SEMESTRAL`, `NOVA_TENTATIVA`), etapa corrente, tentativas e prazo. As etapas
  ("Enviando o áudio", "Transcrevendo", "Estruturando o relatório", "Pronto para conferência",
  "Transcrevendo o pedido", "Aplicando a alteração") ficam persistidas e são lidas pelos dois
  canais. A Web acompanha por polling a cada 2 segundos. O Telegram recebe uma mensagem a cada
  mudança de etapa, enviada por um ouvinte de eventos de domínio.
- **Justificativa**: o MVP processava dentro da requisição e sofria timeouts (17.3, item 1). A spec
  pede etapas consultáveis (FR-021), processamento que continua mesmo se a conexão cair, aviso
  depois de 3 minutos e resultado entregue no canal de origem, com aviso pelo bot. Com a fila no
  próprio banco, não é preciso broker (Princípio VII), o trabalho sobrevive a reinícios e fica na
  mesma transação que o relatório (padrão outbox). O volume esperado, de dezenas de professores,
  cabe com folga.
- **Alternativas consideradas**: *RabbitMQ/Redis* (mais um serviço, sem ganho neste volume);
  *`@Async` em memória* (perde o trabalho num reinício e não atende ao caso de borda "conexão
  perdida"); *SSE/WebSocket para a Web* (o polling de 2 s já atende SC-001 e é mais simples de
  testar; SSE pode entrar depois atrás do mesmo endpoint de estado).

## R-07. Tratamento do áudio (formatos, vídeo, limite e não retenção)

- **Decisão**: o backend recebe o arquivo (upload multipart na Web ou cópia do volume do servidor
  local do Telegram) num diretório temporário em `tmpfs` e valida o tamanho (50 MB, configurável)
  antes de qualquer processamento. Em seguida, normaliza o áudio com **FFmpeg** (instalado na imagem
  do backend): extrai só a trilha de áudio (isso resolve os `.mp4`), converte para Ogg/Opus mono a
  16 kHz e 24 kbit/s e mede a duração. O arquivo original e o normalizado são apagados num
  `finally` logo após a transcrição, com sucesso ou falha. Uma varredura ao iniciar o backend apaga
  sobras de uma queda.
- **Justificativa**: os provedores de transcrição aceitam uploads de até 25 MB (é o caso da Groq).
  Um vídeo de 31 MB como os do cliente passaria do limite sem a normalização. Em Opus a 24 kbit/s,
  1 hora de áudio ocupa cerca de 11 MB. A normalização também resolve a lição 9 (a extensão `oga`
  não era aceita), porque o provedor sempre recebe `audio.ogg`. O `tmpfs` impede que o áudio vá
  para o disco persistente (FR-018, SC-008). Detectar "sem fala" ou áudio vazio fica a cargo do
  provedor (transcrição vazia) e da duração medida (zero ou menos de 1 s gera `AUDIO_NAO_PROCESSAVEL`).
- **Alternativas consideradas**: *enviar o arquivo original ao provedor* (falha com vídeo acima de
  25 MB e com contêineres pouco comuns); *transcodificar no navegador* (não serve para o Telegram e
  quebraria a paridade da regra).

## R-08. Provedores de transcrição e de LLM (plugáveis)

- **Decisão**: portas `TranscricaoPorta` e `LlmPorta` no núcleo, sem nome de fornecedor. Primeiros
  adaptadores: `TranscricaoOpenAiCompativelAdaptador` (API `audio/transcriptions`) e
  `LlmOpenAiCompativelAdaptador` (API `chat/completions` em modo JSON). Por configuração, os dois
  apontam para a Groq, como no MVP (`whisper-large-v3-turbo`, `language=pt`, e
  `llama-3.3-70b-versatile`, temperatura 0,2). URL, modelo e chave vêm de variáveis de ambiente.
  Timeout de 60 s para transcrição e de 45 s para LLM, com uma nova tentativa em erro transitório
  (429/5xx/rede) com espera exponencial, e disjuntor simples por provedor.
- **Justificativa**: a constituição pede provedores selecionáveis por configuração. O contrato
  "OpenAI-compatível" cobre Groq, OpenAI, Azure OpenAI e servidores locais sem código novo, e um
  fornecedor com contrato diferente entra como novo adaptador. Manter os modelos do MVP preserva a
  qualidade e o custo já validados com as gravações do cliente. A troca de modelo passa a ser uma
  decisão de configuração, medida pelo registro de custo (R-13).
- **Alternativas consideradas**: *SDK de um fornecedor específico no núcleo* (proibido pelo
  Princípio I); *LangChain/Spring AI* (abstração a mais para três chamadas simples; o Spring AI
  pode ser adotado depois dentro de um adaptador, sem tocar no núcleo).

## R-09. Prompts, validação da saída da IA e "não inventar"

- **Decisão**: os prompts ficam versionados como arquivos de recurso
  (`prompts/estruturacao-aula.v1.txt`, `revisao-aula.v1.txt`, `incorporacao-respostas.v1.txt`,
  `consolidacao-semestral.v1.txt`, `revisao-semestral.v1.txt`, `correcao-json.v1.txt`), revisados
  em português com acentuação e tom institucional. O identificador da versão do prompt é gravado em
  cada `VersaoRelatorio`. A saída da IA é validada contra um JSON Schema por tipo de tarefa (ver
  [contracts/portas-provedores.md](./contracts/portas-provedores.md)). Se falhar, há uma chamada de
  correção; se falhar de novo, o erro é `FALHA_ESTRUTURACAO` e o relatório vai para "falha no
  processamento" (FR-022). Campos ausentes viram vazios. As respostas às perguntas são
  **incorporadas** pela IA aos campos adequados (FR-025), com limite de 2 rodadas contado no
  domínio. A instrução de alteração é tratada pelo prompt de revisão, que também devolve
  `semAlteracaoDeConteudo` e `pedidoDeAcao` (aprovar ou cancelar). Assim, um áudio que diz "pode
  aprovar" nunca aprova e não cria versão (caso de borda e FR-027a).
- **Justificativa**: prompts versionados permitem testes de regressão com as gravações de
  `assets/`, e a validação por schema fecha a porta para respostas malformadas. Detectar pedido de
  aprovação ou cancelamento na IA evita que o domínio dependa de palavras-chave num texto
  transcrito. A decisão final continua no caso de uso, que ignora esse sinal para qualquer ação
  além de responder ao professor.
- **Alternativas consideradas**: *prompts do MVP sem revisão* (sem acentuação e sem incorporação de
  respostas, em desacordo com FR-025); *function calling/tool use* (nem todo provedor compatível
  suporta; o modo JSON com schema validado no backend é portátil).

## R-10. Geração de PDF com a identidade visual

- **Decisão**: HTML/CSS → PDF em processo, com **OpenHTMLtoPDF** (fork mantido
  `io.github.openhtmltopdf`) e templates Thymeleaf (`pdf/relatorio-aula.html`,
  `pdf/relatorio-semestral.html`) que reproduzem o `Modelo PDF.dc.html`: fontes TTF embutidas
  (Cinzel, Cormorant Garamond, Source Sans 3), logotipo e filete PNG, cabeçalho com aluno, professor
  e data ou período, "—" nas seções vazias, rodapé institucional com numeração por `@page` e
  `counter(page)`/`counter(pages)`. PDFs de versões não aprovadas levam a faixa "Documento em
  conferência — não oficial" (FR-049). O PDF oficial é gerado **na aprovação**, a partir da versão
  aprovada, com "Aprovado por {professor} em {data e hora} · versão N" (FR-030). A geração fica atrás
  de `GeracaoPdfPorta`.
- **Justificativa**: o layout do modelo é todo em HTML/CSS, então traduzi-lo para CSS paginado é
  direto, e o resultado é revisável lado a lado com o protótipo. Com a biblioteca em processo, não
  é preciso outro contêiner. Gerar o oficial à parte na aprovação atende à exigência de "quem
  aprovou, quando e qual versão" sem alterar os PDFs de conferência, que são imutáveis por versão.
- **Alternativas consideradas**: *OpenPDF* (usado no MVP; montar o layout via API é trabalhoso e
  difícil de manter fiel ao design); *Gotenberg/Chromium headless* (fidelidade de CSS maior, mas é
  mais um contêiner pesado, com cerca de 1 GB de imagem, e mais memória na VPS; fica como plano B
  atrás da mesma porta se o OpenHTMLtoPDF não reproduzir o modelo).

## R-11. Armazenamento de PDFs e entrega autorizada

- **Decisão**: `ArmazenamentoDocumentoPorta` com adaptador de sistema de arquivos num volume
  dedicado (`documentos`). A chave é interna (`relatorios/{tipo}/{relatorioId}/v{n}.pdf` e
  `.../oficial.pdf`) e nunca é exposta. A entrega é feita por rota do relatório
  (`GET /api/v1/relatorios-aula/{id}/versoes/{n}/pdf`, `.../pdf-oficial`), que primeiro executa o
  caso de uso `ObterDocumentoRelatorioCasoDeUso` (dono e associação) e só depois transmite os bytes.
  O Telegram usa o mesmo caso de uso e envia o arquivo com `sendDocument`.
- **Justificativa**: corrige a falha 17.1, item 1, do MVP, em que qualquer professor autenticado
  baixava o PDF de outro professor sabendo o nome do arquivo (FR-048), e elimina a validação de nome
  de arquivo contra path traversal, porque o cliente nunca informa caminho.
- **Alternativas consideradas**: *armazenamento de objetos (S3/MinIO)* (serviço a mais sem
  necessidade numa única VPS; entra depois como novo adaptador); *URLs assinadas* (desnecessárias
  com o cookie de sessão, R-12).

## R-12. Autenticação e sessão (Web e Telegram)

- **Decisão**:
  - **Código de acesso**: 8 caracteres do alfabeto `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, gerado com
    `SecureRandom`, armazenado **só como HMAC-SHA-256 com segredo do servidor** (`codigo_acesso_hash`,
    único), junto com `codigo_acesso_expira_em`. O código em claro aparece apenas na saída da carga
    inicial. A validade é renovada (+7 dias) em cada acesso ou uso autenticado, com escrita no banco
    no máximo uma vez por hora por professor (FR-005). O código nunca é consumido.
  - **Sessão Web**: token opaco de 256 bits num cookie `HttpOnly; Secure; SameSite=Strict; Path=/`.
    No banco fica o hash SHA-256 do token (`sessao_web`), com expiração deslizante de 12 h
    (configurável), renovada a cada requisição. "Encerrar sessão" apaga o registro no servidor
    (FR-008). Proteção CSRF do Spring Security com token em cookie e cabeçalho `X-CSRF-TOKEN`. Web e
    API na mesma origem (Caddy), então não há CORS.
  - **Vínculo Telegram**: `vinculo_telegram` com `telegram_usuario_id` único e `professor_id`
    único (FR-006). "sair" apaga o vínculo. A identidade no Telegram vem só do `from.id` do update
    autenticado pelo `secret_token` do webhook. A autorização de cada ação segue as mesmas regras da
    Web (FR-010).
  - **Tentativas de acesso**: tabela `tentativa_acesso` com chave `canal + hash(origem)` (IP na Web,
    `from.id` no Telegram), gravada numa transação independente (`REQUIRES_NEW`, lição 4 do MVP),
    zerada no sucesso e ao fim do bloqueio (lição 5). O bloqueio é checado antes do código.
- **Justificativa**: o cookie `HttpOnly` resolve a lição 2 (iframe e link de PDF sem cabeçalho
  `Authorization`) e protege o token contra XSS. Com a sessão no servidor, a revogação é real
  (17.1, item 5). Guardar só o hash do código segue a lógica de senha, já que o código funciona como
  credencial. O mesmo serviço de tentativas para os dois canais garante a paridade de FR-007.
- **Alternativas consideradas**: *JWT deslizante como no MVP* (sem revogação real e com o cabeçalho
  de token renovado exposto ao JavaScript); *e-mail com link mágico* (fora do escopo da spec, que
  mantém o código).

## R-13. Observabilidade, custo e privacidade nos logs

- **Decisão**: logs estruturados em JSON (Logback + encoder JSON) com `relatorioId`, `trabalhoId`,
  `canal`, `etapa`, duração e códigos de erro. Nunca registrar áudio, transcrição, conteúdo de
  relatório, nomes, CPF, código de acesso nem token (FR-055). Um filtro de mascaramento para campos
  conhecidos serve de defesa em profundidade. O custo fica na tabela `chamada_ia` por chamada:
  tipo, provedor e modelo por configuração, segundos de áudio ou tokens de entrada e saída, duração
  e custo estimado pela tabela de preços configurável (FR-056). Métricas via Micrometer e
  `/actuator/health` e `/actuator/prometheus` só na rede interna.
- **Justificativa**: atende a FR-055, FR-056, SC-001, SC-010 e SC-013 (tempo por etapa) e à métrica
  principal do produto (tempo e custo). A tabela no banco dá consultas simples de custo por aluno,
  relatório e mês.
- **Alternativas consideradas**: *stack completa de observabilidade (Prometheus/Grafana/Loki) nesta
  feature* (fica para a feature de operação; os endpoints já ficam prontos).

## R-14. Carga inicial (professores, alunos, associações e códigos)

- **Decisão**: comando de linha do próprio backend (perfil `carga`), executado com
  `docker compose run --rm backend carga --arquivo /carga/carga.json`. O arquivo JSON (formato em
  [contracts/carga-inicial.md](./contracts/carga-inicial.md)) é idempotente por e-mail (professor)
  e por hash do CPF (aluno). O comando cifra o CPF (AES-256-GCM, IV aleatório de 12 bytes, chave
  `CPF_CHAVE_CIFRAGEM`), calcula o HMAC do CPF para unicidade e emite ou reemite códigos
  (`--emitir-codigo <email>`), mostrando o código em claro só no terminal. Tudo passa pelos casos de
  uso do núcleo (`CarregarCadastroInicialCasoDeUso`, `EmitirCodigoAcessoCasoDeUso`).
- **Justificativa**: FR-011 pede um procedimento operacional até existir a feature da secretaria.
  Um comando que usa os mesmos casos de uso respeita o Princípio II (só o backend escreve) e evita o
  problema do MVP de não conseguir inserir aluno por SQL por causa da cifragem.
- **Alternativas consideradas**: *seed SQL* (não cifra o CPF); *endpoint administrativo temporário*
  (superfície de ataque sem tela nem autenticação de secretaria).

## R-15. Contêineres, proxy e implantação

- **Decisão**: Docker Compose com os serviços `web` (Caddy 2 servindo o build do SPA e fazendo
  proxy reverso de `/api`, com HTTPS automático em produção e limite de corpo de 55 MB), `backend`
  (imagem Eclipse Temurin 25 JRE + FFmpeg), `postgres` (18), `telegram-bot-api` (servidor local,
  R-05). Arquivos: `infra/compose.yaml` (base), `infra/compose.dev.yaml` (portas em loopback, bot de
  testes), `infra/compose.test.yaml` (execução dos testes em contêiner), `infra/compose.prod.yaml`
  (VPS: só o Caddy publica 80/443). Os segredos ficam em `.env` fora do Git, e
  `infra/.env.example` é versionado. A implantação usa um script versionado
  (`infra/scripts/implantar.sh`) que faz `git pull`, `docker compose build` e `up -d`, rodado na VPS
  via SSH a partir do GitHub Actions, com aprovação manual.
- **Justificativa**: é exatamente o que pedem as "Restrições Adicionais" da constituição: tudo em
  contêiner, um único comando local, mesmas imagens em todos os ambientes, só HTTPS exposto e VPS
  genérica. O Caddy resolve o certificado sem configuração extra e unifica a origem (R-12).
- **Alternativas consideradas**: *Nginx + Certbot* (dois componentes para o mesmo resultado);
  *Kubernetes* (desproporcional para uma VPS).

## R-16. Estratégia de testes (em contêiner)

- **Decisão**:
  - **Núcleo**: JUnit 5 + AssertJ + dublês escritos à mão para as portas (sem Mockito no domínio),
    com JaCoCo exigindo **≥ 90% de linhas e de branches** em `nucleo`, o que bloqueia o build e o CI
    (Princípio V).
  - **Arquitetura**: ArchUnit (domínio não depende de casos de uso; casos de uso não dependem de
    adaptadores; nomenclatura por sufixo, Princípio VI; nenhum nome de fornecedor no núcleo).
  - **Integração**: Spring Boot Test contra o serviço `postgres` do `compose.test.yaml`, com
    WireMock simulando o provedor de IA e a API de bots do Telegram. Sem Testcontainers, para não
    precisar de Docker dentro de Docker.
  - **Contrato (API do portal)**: `swagger-request-validator` valida requisições e respostas do
    MockMvc contra `contracts/api-portal.openapi.yaml`. O frontend gera os tipos a partir do mesmo
    arquivo (`openapi-typescript`), então uma divergência quebra a compilação.
  - **Contrato (Telegram)**: testes de conversa que alimentam o adaptador com updates JSON e
    verificam as chamadas feitas ao WireMock da API de bots, seguindo
    [contracts/telegram-conversa.md](./contracts/telegram-conversa.md).
  - **Paridade**: um roteiro único (`RoteiroParidadeTeste`) executado duas vezes, uma pelo
    controlador Web e outra pelo adaptador Telegram, comparando estados, versões e histórico
    (SC-006, SC-009).
  - **Frontend**: Vitest + Testing Library; Playwright com `@axe-core/playwright` a 390 px e 1440 px,
    nos modos claro e escuro (SC-011).
  - **Ponta a ponta com IA real**: suíte opcional (`-Pia-real`), fora da cobertura, que processa as
    gravações de `assets/` e registra tempo e custo (SC-001, SC-013).
- **Justificativa**: atende aos Princípios V e I (paridade verificável) e à exigência de testes em
  contêiner, com o CI como fonte de verdade.
- **Alternativas consideradas**: *Testcontainers* (exige socket do Docker no contêiner de teste);
  *Mockito no domínio* (dublês à mão deixam os testes do núcleo independentes de framework).

## R-17. Busca aproximada de aluno

- **Decisão**: normalização no domínio (minúsculas, remoção de acentos via `Normalizer` NFD e trim)
  e correspondência por prefixo de palavra ou substring entre os alunos associados ao professor. Com
  uma correspondência, o aluno é encontrado; com várias, as opções são listadas; com nenhuma, os
  alunos do professor são mostrados (FR-013). Um aluno "corresponde exatamente" (caso de borda do
  nome enviado durante a conferência) quando o texto normalizado é igual ao nome normalizado
  completo ou ao primeiro nome, sem outro aluno com o mesmo valor.
- **Justificativa**: o número de alunos por professor é pequeno, cabe em memória, e a regra fica
  testável no domínio sem depender de extensões do banco (`unaccent`, `pg_trgm`).
- **Alternativas consideradas**: *pg_trgm + unaccent* (poria regra de negócio no banco);
  *distância de edição* (tolera erros de digitação, mas traz falsos positivos entre homônimos
  parciais; pode entrar depois atrás do mesmo serviço).

## R-18. Mensagens ao professor com o mesmo conteúdo nos dois canais

- **Decisão**: o núcleo devolve **resultados e códigos estáveis** (ex.: `RELATORIO_APROVADO`,
  `VERSAO_DESATUALIZADA`, `AUDIO_MUITO_GRANDE`), nunca texto. Um catálogo único de mensagens em
  pt-BR ([contracts/mensagens.md](./contracts/mensagens.md)) é versionado e compartilhado. O backend
  o carrega (`mensagens.properties`) para o Telegram, e o frontend recebe o mesmo arquivo como JSON
  gerado no build. Cada canal só adapta a forma (Markdown do Telegram ou componentes da Web).
- **Justificativa**: FR-003 exige o mesmo conteúdo nos dois canais. Uma fonte única impede que os
  textos divirjam. Deixar o texto fora do núcleo segue o Princípio I, porque apresentação é
  responsabilidade do adaptador.
- **Alternativas consideradas**: *textos no frontend e no adaptador Telegram, cada um com o seu*
  (tende a divergir, como os textos sem acentuação do MVP).

---

## Itens do Technical Context resolvidos

| Incógnita | Resolvida em |
|---|---|
| Linguagem/versão do backend e do frontend | R-01, R-02 |
| Dependências principais | R-01 a R-11 |
| Armazenamento | R-03, R-11 |
| Testes | R-16 |
| Plataforma-alvo | R-15 |
| Recebimento de arquivos de 50 MB no Telegram | R-05 |
| Processamento assíncrono e etapas | R-06 |
| Vídeo MP4 e formatos | R-07 |
| Provedores de IA plugáveis | R-08, R-09 |
| PDF com identidade visual | R-10 |
| Autorização de PDFs | R-11 |
| Sessão, revogação e tentativas | R-12 |
| Custo e logs | R-13 |
| Carga inicial | R-14 |

Nenhum item ficou como NEEDS CLARIFICATION.
