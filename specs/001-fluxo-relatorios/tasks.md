---

description: "Lista de tarefas da feature 001-fluxo-relatorios"
---

# Tasks: Fluxo de envio e criação de relatórios pedagógicos (Web e Telegram)

**Input**: documentos de design em `specs/001-fluxo-relatorios/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios**. A constituição (Princípio V) exige ≥ 90% de cobertura de linha e de branch
no `nucleo`, testes de contrato para a API do portal e dublês para os provedores. O plano (R-16) define
também testes de conversa do Telegram e um roteiro de paridade Web × Telegram. Em cada história, as
tarefas de teste vêm primeiro: escreva-as, confirme que falham e só então implemente.

**Organization**: as tarefas estão agrupadas por história de usuário, para que cada uma possa ser
implementada, testada e entregue de forma independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: história a que a tarefa pertence (US1 a US5)
- Toda tarefa cita o caminho exato do(s) arquivo(s)

## Convenções de caminho

- Pacote-base Java: `br.com.felipeblaft.relatorios` (abaixo, `…/relatorios/`).
- Núcleo (Java puro, sem Spring): `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/` com
  `dominio/`, `casodeuso/`, `servico/` e `porta/`; testes em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/`.
- Aplicação (Spring Boot): `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/` com
  `entrada/web/`, `entrada/telegram/`, `entrada/carga/`, `saida/*/` e `configuracao/`; recursos em
  `backend/aplicacao/src/main/resources/`; testes em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/`.
- Frontend: `frontend/src/` e `frontend/tests/`. Infraestrutura: `infra/`. CI: `.github/workflows/`.
- Nomes em português sem acento, com os sufixos do Princípio VI. Na persistência: entidade JPA
  `XEntidade`, interface Spring Data `XJpa`, implementação da porta `XRepositorioAdaptador`, conversão
  `XMapeador`. Dublês de teste do núcleo ficam em `…/relatorios/dubles/` (ex.: `RelogioFixo`,
  `AlunoRepositorioEmMemoria`), sem Mockito.
- Testes de integração da aplicação terminam em `IT` e rodam contra o `postgres` de `infra/compose.test.yaml`.
- Ordem das fases: US2 (acesso, P1) vem antes de US1 (registro, P1) porque todo fluxo de US1 exige um
  professor acessado, como sugere o plano ("Fases seguintes").

---

## Phase 1: Setup (infraestrutura compartilhada)

**Purpose**: criar os projetos, as imagens de contêiner e o CI.

- [X] T001 Criar a estrutura de diretórios do plano (`backend/nucleo/`, `backend/aplicacao/`, `frontend/`, `infra/caddy/`, `infra/carga/`, `infra/scripts/`, `.github/workflows/`) e o `.gitignore` na raiz ignorando `target/`, `node_modules/`, `frontend/dist/`, `infra/.env` e `infra/carga/*.json` exceto `infra/carga/exemplo.json`
- [X] T002 [P] Criar `.editorconfig` e `.gitattributes` na raiz (UTF-8, LF, indentação de 4 espaços em Java e 2 em TS/YAML)
- [X] T003 Criar o POM pai em `backend/pom.xml` (Java 25, BOM do Spring Boot 4.1, módulos `nucleo` e `aplicacao`, plugins Spotless com google-java-format, Surefire e Failsafe para `*IT`)
- [X] T004 Criar `backend/nucleo/pom.xml` sem nenhuma dependência de Spring ou de fornecedor (só JUnit 5, AssertJ e ArchUnit em `test`) e com JaCoCo exigindo ≥ 0,90 de linhas e de branches na fase `verify`, falhando o build abaixo disso
- [X] T005 Criar `backend/aplicacao/pom.xml` dependendo de `nucleo` e de Spring Boot (web, security, data-jpa, validation, actuator, thymeleaf), Flyway, driver PostgreSQL, `io.github.openhtmltopdf` (pdfbox), networknt json-schema-validator, logstash-logback-encoder, Micrometer Prometheus; em `test`: spring-boot-test, WireMock, swagger-request-validator-mockmvc, PDFBox e ArchUnit; perfil Maven `ia-real` para a suíte opcional
- [X] T006 [P] Criar `backend/aplicacao/Dockerfile` multiestágio (Maven + Temurin 25 JDK para build; Temurin 25 JRE + `ffmpeg` para execução, usuário sem privilégios, diretório `/tmp/audios`, entrypoint que aceita `carga` e `emitir-codigo` como argumentos além do servidor)
- [X] T007 [P] Inicializar o frontend em `frontend/` (`package.json`, `vite.config.ts`, `tsconfig.json`, `index.html`) com React 19, TypeScript 5, React Router, TanStack Query, openapi-typescript, Vitest + Testing Library, Playwright + `@axe-core/playwright`, ESLint e Prettier
- [X] T008 [P] Criar `frontend/Dockerfile` (build Node → imagem `web` baseada em Caddy 2 com `dist/` e `infra/caddy/Caddyfile`)
- [X] T009 [P] Criar `infra/caddy/Caddyfile` (serve o SPA com fallback para `index.html`, `reverse_proxy /api/*` para `backend:8080`, `request_body max_size 55MB`, cabeçalhos de segurança HSTS, CSP restrita à própria origem, `X-Content-Type-Options`, `Referrer-Policy`; certificado local em dev e automático em produção por variável)
- [X] T010 Criar `infra/compose.yaml` com os serviços `web`, `backend`, `postgres` (18) e `telegram-bot-api` (`tdlib/telegram-bot-api` com `--local`), rede `interna`, volumes `pgdata`, `documentos` e `telegram-bot-api-dados` (montado no mesmo caminho no backend), `tmpfs` em `/tmp/audios` no backend e healthchecks
- [X] T011 [P] Criar `infra/compose.dev.yaml` (só o `web` publica portas, em loopback; certificado local do Caddy; montagem de `infra/carga/` em `/carga`)
- [X] T012 [P] Criar `infra/compose.test.yaml` com `postgres-teste`, `testes-backend` (`mvn verify`), `testes-frontend` (`vitest run`), `testes-e2e` (Playwright contra a pilha com `PROVEDORES_IA=simulado`) e `testes-ia-real` (perfil `ia-real`, monta `assets/` somente leitura)
- [X] T013 [P] Criar `infra/compose.prod.yaml` (VPS Hostinger: só `web` publica 80/443; política de reinício; sem portas de `backend`, `postgres` e `telegram-bot-api`)
- [X] T014 [P] Criar `infra/.env.example` com todas as variáveis de `quickstart.md` §2 (segredos vazios e padrões `AUDIO_LIMITE_MB=50`, `SESSAO_WEB_HORAS=12`, `ACESSO_MAX_TENTATIVAS=5`, `ACESSO_BLOQUEIO_MINUTOS=15`, `CODIGO_VALIDADE_DIAS=7`, `AUDIO_PENDENTE_MINUTOS=30`, `ESCOLA_FUSO_HORARIO=America/Sao_Paulo`, `PROVEDORES_IA=simulado`)
- [X] T015 [P] Criar `.github/workflows/ci.yml` que roda `testes-backend`, `testes-frontend` e `testes-e2e` de `infra/compose.test.yaml` em cada PR para a `main`, falhando com teste vermelho ou cobertura do `nucleo` abaixo de 90%
- [X] T016 [P] Copiar os ativos da marca de `portal-do-professor-design-system/project/assets/` para `frontend/public/marca/` (monograma, logo, filete, wordmark nas variações preto, marfim e ouro) e para `backend/aplicacao/src/main/resources/pdf/imagens/` (logo e filete usados no `Modelo PDF.dc.html`)
- [X] T017 [P] Incluir as fontes locais (OFL/Apache, obtidas dos repositórios oficiais): Cinzel, Cormorant Garamond, Source Sans 3 e Material Symbols Outlined em `frontend/src/estilos/fontes/` (`.woff2`) e Cinzel, Cormorant Garamond e Source Sans 3 em `backend/aplicacao/src/main/resources/pdf/fontes/` (`.ttf`), com os arquivos de licença

---

## Phase 2: Foundational (pré-requisitos bloqueantes)

**Purpose**: núcleo de cadastro e autorização, persistência base, configuração, erros, catálogo de
mensagens, carga inicial e o esqueleto do frontend.

**⚠️ CRITICAL**: nenhuma história começa antes do fim desta fase.

### Testes da fundação

- [X] T018 [P] Criar os dublês do núcleo em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dubles/` (`RelogioFixo`, `ProtecaoDadosFalsa`, `GeradorCodigoAcessoFixo`, `ProfessorRepositorioEmMemoria`, `AlunoRepositorioEmMemoria`, `AssociacaoRepositorioEmMemoria`)
- [X] T019 [P] Escrever os testes unitários de cadastro e autorização: `CpfTeste`, `CodigoAcessoTeste`, `ProfessorTeste` (validade, renovação +7 dias com no máximo 1 escrita por hora, inativo, reemissão) e `AlunoTeste` (responsável obrigatório para menor de 18, idade numa data) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/cadastro/`, e `AutorizacaoServicoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/servico/`
- [X] T020 [P] Escrever `RegrasArquiteturaTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/arquitetura/RegrasArquiteturaTeste.java` (domínio não depende de `casodeuso`, `servico` nem `porta`; casos de uso terminam em `CasoDeUso`, serviços em `Servico`, portas em `Porta` ou `Repositorio`; nenhum nome de fornecedor no núcleo: `groq`, `openai`, `whisper`, `llama`, `ffmpeg`, `openhtmltopdf`, `thymeleaf`, `postgres`, `spring`)
- [X] T021 [P] Escrever `RegrasAdaptadoresTeste` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/arquitetura/RegrasAdaptadoresTeste.java` (classes de `entrada/web` com `@RestController` terminam em `Controlador`; implementações de portas em `saida/` terminam em `Adaptador`; objetos de borda em `Dto`, `Requisicao` ou `Resposta`; `entrada` não acessa `saida` diretamente)
- [X] T022 [P] Escrever `CarregarCadastroInicialCasoDeUsoTeste` e `EmitirCodigoAcessoCasoDeUsoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/carga/` cobrindo todas as validações de `contracts/carga-inicial.md`, idempotência por e-mail e por hash do CPF, tudo ou nada, `--simular` e erros sem o valor do CPF
- [X] T023 [P] Criar as bases de teste da aplicação em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/suporte/`: `IntegracaoBase` (Spring Boot Test contra `postgres-teste`, limpeza de tabelas entre testes, relógio fixo), `ContratoApiBase` (MockMvc + swagger-request-validator lendo `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml`, login auxiliar por cookie e CSRF) e `TelegramConversaBase` (WireMock da API de bots, envio de updates JSON a `POST /telegram/webhook` com o segredo, asserções de método, `chat_id`, texto e botões)

### Núcleo: cadastro, autorização e carga

- [X] T024 [P] Criar `Canal`, `Meio` e `TipoRelatorio` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/comum/`
- [X] T025 [P] Criar `CodigoErro` (enum com todos os códigos estáveis de `contracts/mensagens.md` e `contracts/carga-inicial.md`) e `ExcecaoDominio` (código + mapa de detalhes, sem texto) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/comum/`
- [X] T026 [P] Criar os objetos de valor `Cpf` (normalização para 11 dígitos, dígitos verificadores) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/cadastro/Cpf.java` e `CodigoAcesso` (8 caracteres do alfabeto `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, normalização trim + maiúsculas) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/acesso/CodigoAcesso.java`
- [X] T027 Criar `Professor` (`codigoValidoEm`, `renovarValidade` com limite de 1 escrita por hora, `substituirCodigo`), `Aluno` (`idadeEm`, regra do responsável) e `ProfessorAluno` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/cadastro/` conforme `data-model.md` §1–3
- [X] T028 Criar as portas `RelogioPorta` (`agora`, `hojeNoFusoDaEscola`), `ProtecaoDadosPorta` (`cifrarCpf`, `hashCpf`, `hashCodigoAcesso`, `hashOrigem`), `GeradorCodigoAcessoPorta`, `ProfessorRepositorio`, `AlunoRepositorio` e `AssociacaoRepositorio` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/`
- [X] T029 Criar `AutorizacaoServico` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/servico/AutorizacaoServico.java` (`exigirAssociacao` → `ALUNO_NAO_ASSOCIADO`; `exigirDono` → `RECURSO_NAO_ENCONTRADO`)
- [X] T030 Criar `CarregarCadastroInicialCasoDeUso` e `EmitirCodigoAcessoCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/carga/` (valida o arquivo inteiro antes de gravar; professor novo recebe código; devolve resumo e códigos em claro só no resultado)

### Aplicação: persistência, segurança de dados e configuração

- [X] T031 Criar a migração `backend/aplicacao/src/main/resources/db/migration/V1__cadastros.sql` (tabelas `professor`, `aluno`, `professor_aluno`, com unicidades de e-mail, `cpf_hash`, `codigo_acesso_hash` e `(professor_id, aluno_id)`)
- [X] T032 Criar entidades, interfaces Spring Data, mapeadores e `ProfessorRepositorioAdaptador`, `AlunoRepositorioAdaptador` e `AssociacaoRepositorioAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/`
- [X] T033 [P] Criar `ProtecaoDadosAdaptador` (AES-256-GCM com IV de 12 bytes prefixado; HMAC-SHA-256 com `SEGREDO_HMAC`), `GeradorCodigoAcessoAdaptador` (`SecureRandom`) e `RelogioSistemaAdaptador` (fuso `ESCOLA_FUSO_HORARIO`) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/seguranca/`
- [X] T034 Escrever `CadastroPersistenciaIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/persistencia/CadastroPersistenciaIT.java` (ida e volta dos adaptadores, CPF cifrado nunca legível em claro no banco, HMAC estável)
- [X] T035 Criar `PropriedadesAplicacao` (`@ConfigurationProperties` com limites de áudio, sessão, tentativas, bloqueio, validade do código, áudio pendente, fuso, provedores) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/PropriedadesAplicacao.java` e `backend/aplicacao/src/main/resources/application.yaml` lendo as variáveis de `infra/.env.example`
- [X] T036 Criar `CasosDeUsoConfiguracao` (instancia os casos de uso do núcleo como beans) e `TransacaoCasoDeUsoDecorador` (envolve cada caso de uso numa transação) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/`
- [X] T037 [P] Criar `backend/aplicacao/src/main/resources/logback-spring.xml` com saída JSON e `MascaramentoDadosSensiveisConversor` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/log/` (mascara CPF, código de acesso, token e campos `transcricao`/`texto`; FR-055)
- [X] T038 Criar o catálogo `backend/aplicacao/src/main/resources/mensagens/mensagens_pt_BR.properties` com todos os códigos e textos de `contracts/mensagens.md` e `CatalogoMensagens` (busca e substituição de placeholders) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/CatalogoMensagens.java`, com o teste `CatalogoMensagensTeste` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/configuracao/` que falha se algum `CodigoErro` não tiver texto
- [X] T039 Criar `TratadorErrosControlador` (`@RestControllerAdvice`) e `ErroResposta` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/`, mapeando cada `CodigoErro` para o HTTP de `contracts/mensagens.md`, preenchendo `mensagem` pelo catálogo e devolvendo `ERRO_INTERNO` sem detalhe interno para exceções inesperadas (FR-054)
- [X] T040 Criar os comandos `CargaComando` (`--arquivo`, `--simular`) e `EmitirCodigoComando` (`--email`) no perfil `carga` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/carga/`, imprimindo o resumo sem CPF e as linhas `email → CÓDIGO (válido até dd/mm/aaaa hh:mm)`
- [X] T041 [P] Criar `infra/carga/exemplo.json` fictício: professora Clara com Matteo Mafra, Olavo Torrente e Miguel de Castro Torrente (CPFs fictícios válidos, um menor com responsável, instrumentos) e professor Bruno com um aluno próprio para o teste de acesso cruzado
- [X] T042 Escrever `CargaComandoIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/carga/CargaComandoIT.java` (carga de `exemplo.json`, segunda execução idempotente, arquivo com erro não grava nada, `--simular`, `emitir-codigo` substitui o hash)

### Frontend: base

- [X] T043 [P] Criar os tokens e temas em `frontend/src/estilos/tokens.css` (claro e escuro, copiados de `Portal do Professor.dc.html`: `--bg`, `--tx`, `--gd`, `--gs` etc.), `frontend/src/estilos/fontes.css`, `frontend/src/estilos/tipografia.css` e `frontend/src/estilos/base.css` (foco visível, alvos ≥ 44 px, sem `--gd` em texto pequeno sobre fundo claro)
- [X] T044 [P] Criar `frontend/scripts/gerar-mensagens.mjs` (lê `backend/aplicacao/src/main/resources/mensagens/mensagens_pt_BR.properties` e gera `frontend/src/mensagens/mensagens.json` no `prebuild`) e `frontend/src/mensagens/mensagens.ts` (`msg(codigo, parametros)`)
- [X] T045 [P] Criar o script `gerar:api` (openapi-typescript de `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml` para `frontend/src/servicos/api-tipos.ts`) e `frontend/src/servicos/clienteApi.ts` (fetch na mesma origem com cookie, cabeçalho `X-CSRF-TOKEN` a partir do cookie `XSRF-TOKEN`, erro tipado `Erro`, evento global em 401)
- [X] T046 Criar `frontend/src/main.tsx`, `frontend/src/app/App.tsx`, `frontend/src/app/rotas.tsx` (todas as rotas de `contracts/rotas-portal.md`, com páginas provisórias), `frontend/src/app/GuardaSessao.tsx` (401 → `/entrar` guardando o destino) e `frontend/src/app/TemaProvedor.tsx` (claro/escuro no `localStorage` com `try/catch`; primeira visita segue `prefers-color-scheme`)
- [X] T047 [P] Criar os componentes comuns `Cabecalho.tsx` (monograma e menu), `DialogoConfirmacao.tsx` (foco preso, `Esc`, rótulos), `EstadoCarregando.tsx`, `EstadoVazio.tsx`, `EstadoErro.tsx` e `Botao.tsx` em `frontend/src/componentes/`, com testes em `frontend/tests/componentes/ComponentesComuns.test.tsx`

**Checkpoint**: `docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d --build` sobe os 4 serviços, a carga de exemplo funciona e o CI fica verde.

---

## Phase 3: User Story 2 - Acessar o sistema e se orientar em qualquer canal (Priority: P1)

**Goal**: o professor entra no portal com o código, vincula o Telegram com o mesmo código, vê o início
(saudação, alunos, ações) nos dois canais, encontra alunos pela busca aproximada e encerra o acesso.
Bloqueio após 5 códigos incorretos, igual nos dois canais.

**Independent Test**: com Clara na carga, entrar no portal, vincular uma conta de teste do Telegram,
comparar o início nos dois canais, errar o código 5 vezes em cada canal e verificar o bloqueio,
encerrar o acesso nos dois canais (quickstart V1, V2, V15).

### Testes da User Story 2 ⚠️

- [X] T048 [P] [US2] Escrever `SessaoWebTeste` e `TentativaAcessoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/acesso/`, `VinculoTelegramTeste` e `ConversaTelegramTeste` (seleção, áudio pendente que substitui e expira, foco, limpar) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/telegram/` e `BuscaAlunoTeste` (acentos, maiúsculas, prefixo de palavra, substring, correspondência exata por nome completo ou primeiro nome; R-17) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/cadastro/`
- [X] T049 [P] [US2] Escrever `EntrarPortalCasoDeUsoTeste`, `ValidarSessaoWebCasoDeUsoTeste`, `EncerrarSessaoWebCasoDeUsoTeste`, `VincularTelegramCasoDeUsoTeste` (bloqueio checado antes do código, `CODIGO_EXPIRADO`, professor inativo, `TELEGRAM_JA_VINCULADO`, `PROFESSOR_JA_VINCULADO`, código não consumido), `IdentificarProfessorTelegramCasoDeUsoTeste` e `EncerrarVinculoTelegramCasoDeUsoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/acesso/` e `ControleTentativasAcessoServicoTeste` e `RenovacaoAcessoServicoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/servico/`
- [X] T050 [P] [US2] Escrever `ObterInicioProfessorCasoDeUsoTeste` (saudação, total, até 5 alunos, `SEM_ALUNOS`, relatórios em aberto vindos da porta), `BuscarAlunosCasoDeUsoTeste` (`TODOS`, `ENCONTRADOS`, `NENHUM` com todos os alunos) e `ObterResumoAlunoCasoDeUsoTeste` (não associado → `ALUNO_NAO_ASSOCIADO`) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/inicio/`
- [X] T051 [P] [US2] Escrever `AcessoContratoIT` (`POST/GET/DELETE /sessao`, cookie `HttpOnly; Secure; SameSite=Strict`, 401, 429 com `Retry-After`) e `InicioAlunosContratoIT` (`/inicio`, `/alunos?busca=`, `/alunos/{id}`, 403) em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/web/`
- [X] T052 [P] [US2] Escrever `VinculacaoConversaIT` (sequência 5.1 e estado #1 de `contracts/telegram-conversa.md`) e `InicioBuscaConversaIT` (início/menu/`/start`, busca com 1, várias e nenhuma correspondência, `sair` com confirmação, `ajuda`, grupo recusado, tipo não suportado, botão antigo → `ACAO_EXPIRADA`, segredo errado → 401, `update_id` repetido não reprocessa) em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/`
- [X] T053 [P] [US2] Escrever `TentativaAcessoIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/persistencia/TentativaAcessoIT.java` (a falha fica gravada mesmo com rollback da transação externa; zera no sucesso e ao fim do bloqueio; origem só como hash)
- [X] T054 [P] [US2] Escrever `TelaEntrar.test.tsx` (01, 01b, 01c com tempo restante, 01d), `TelaPainel.test.tsx` (02, 02b, menu 02c com tema e encerrar sessão) e `TelaAlunos.test.tsx` (03, 03b, 03c, marca "Em aberto") em `frontend/tests/paginas/`

### Implementação da User Story 2

- [X] T055 [P] [US2] Criar `SessaoWeb` (expiração deslizante de 12 h, renovação com no máximo 1 escrita por minuto) e `TentativaAcesso` (`registrarFalha`, `bloqueadoAte`, `zerar`) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/acesso/`
- [X] T056 [P] [US2] Criar `VinculoTelegram`, `ConversaTelegram`, `FluxoConversa`, `EtapaFluxoConversa` e `AudioPendente` (só referência ao arquivo do canal, com `recebidoEm` e expiração) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/telegram/` conforme `data-model.md` §5
- [X] T057 [P] [US2] Criar `BuscaAluno` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/cadastro/BuscaAluno.java` (R-17)
- [X] T058 [US2] Criar as portas `SessaoWebRepositorio`, `VinculoTelegramRepositorio`, `TentativaAcessoRepositorio` e `RelatoriosEmAbertoConsultaPorta` (`listarDoProfessor`, `buscarDoAluno`, `ultimaAulaPorAluno`) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/`, acrescentar `gerarTokenSessao` e `hashTokenSessao` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/ProtecaoDadosPorta.java` e os dublês correspondentes em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dubles/`
- [X] T059 [US2] Criar `ControleTentativasAcessoServico` e `RenovacaoAcessoServico` (renova a validade do código em todo uso com sessão ou vínculo ativos; FR-005) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/servico/`
- [X] T060 [US2] Criar `EntrarPortalCasoDeUso`, `ValidarSessaoWebCasoDeUso`, `EncerrarSessaoWebCasoDeUso`, `VincularTelegramCasoDeUso`, `IdentificarProfessorTelegramCasoDeUso` e `EncerrarVinculoTelegramCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/acesso/`
- [X] T061 [US2] Criar `ObterInicioProfessorCasoDeUso`, `BuscarAlunosCasoDeUso` e `ObterResumoAlunoCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/inicio/`
- [X] T062 [US2] Criar a migração `backend/aplicacao/src/main/resources/db/migration/V2__acesso.sql` (`sessao_web`, `vinculo_telegram` com todas as colunas de conversa e `bloqueio_otimista`, `tentativa_acesso`, `update_telegram_processado`)
- [X] T063 [US2] Criar entidades, mapeadores e `SessaoWebRepositorioAdaptador`, `VinculoTelegramRepositorioAdaptador`, `TentativaAcessoRepositorioAdaptador` (gravação em `REQUIRES_NEW`) e `UpdateTelegramProcessadoJpa` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/`, mais `RelatoriosEmAbertoConsultaProvisoriaAdaptador` (devolve listas vazias até T111)
- [X] T064 [US2] Criar a segurança da Web em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/seguranca/`: `SegurancaConfiguracao` (Spring Security sem sessão HTTP, CSRF com cookie `XSRF-TOKEN` e cabeçalho `X-CSRF-TOKEN`, `POST /api/v1/sessao` público, `/telegram/webhook` fora de CSRF e de sessão), `SessaoCookieFiltro` (cookie `sessao` → `ValidarSessaoWebCasoDeUso` → `ProfessorAutenticado`; 401 `SESSAO_EXPIRADA`), `OrigemRequisicaoResolvedor` (IP de `X-Forwarded-For` somente vindo do Caddy) e `ProfessorAutenticado`
- [X] T065 [US2] Criar `SessaoControlador`, `EntrarRequisicao`, `SessaoDto` e `SessaoMapeador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/` (define e remove o cookie; 429 com `Retry-After`)
- [X] T066 [US2] Criar `InicioControlador`, `AlunoControlador`, `InicioDto`, `AlunoResumoDto`, `BuscaAlunosDto`, `ProfessorResumoDto` e `InicioMapeador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/` (`limiteAudioMb` vindo da configuração)
- [X] T067 [US2] Criar o transporte do Telegram: `TelegramWebhookControlador` (segredo comparado em tempo constante, deduplicação por `update_id`, sempre 200 após persistir, só chat privado) e os records de update em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/`, e `TelegramConfiguracao` (registra o webhook no servidor local com `secret_token` ao iniciar, loga `webhook-registrado`, `setMyCommands`) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/TelegramConfiguracao.java`
- [X] T068 [US2] Criar `MensageriaTelegramAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/telegram/MensageriaTelegramAdaptador.java` (`sendMessage` com `parse_mode=HTML` e texto escapado, `editMessageText`, `answerCallbackQuery`, `sendDocument`, `getFile`, usando a URL do servidor local)
- [X] T069 [US2] Criar `DadosCallbackCodificador` (`v1:<acao>:<argumento>`, ≤ 64 bytes) e `PalavrasChaveTelegram` (comparação sem acento, maiúsculas e barra; tabela da seção 2 do contrato) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/`
- [X] T070 [US2] Criar `ApresentadorTelegram` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/ApresentadorTelegram.java` (textos do `CatalogoMensagens`, bloco de início com relatórios em aberto, total, até 5 alunos marcados "· Em aberto" e botões `reg`/`sem`/`hist`; listas de alunos; pedidos de confirmação)
- [X] T071 [US2] Criar `InterpretadorConversaTelegram` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/InterpretadorConversaTelegram.java`: despacho pelo estado derivado na ordem da tabela da seção 4, com os estados #1 (código com bloqueio), #2 (confirmação de `sair`) e #10 (busca de aluno por texto), os comandos `início`/`menu`/`/start`, `sair`, `ajuda`, `registrar aula` ("Para qual aluno?"), `TIPO_NAO_SUPORTADO` e `ACAO_EXPIRADA`; renova a validade do código a cada uso
- [X] T072 [US2] Criar `VarreduraAcessoTarefa` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/VarreduraAcessoTarefa.java` (diária: apaga sessões vencidas e `update_telegram_processado` com mais de 7 dias)
- [X] T073 [US2] Registrar os casos de uso de acesso e início em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/CasosDeUsoConfiguracao.java`
- [X] T074 [P] [US2] Criar `frontend/src/servicos/sessao.ts`, `frontend/src/servicos/inicio.ts` e `frontend/src/servicos/alunos.ts` (hooks do TanStack Query)
- [X] T075 [US2] Criar `frontend/src/paginas/TelaEntrar.tsx` (tela 01 e variações 01b–01d; campo de 8 caracteres em maiúsculas; contagem do tempo de bloqueio; sem o link da secretaria)
- [X] T076 [US2] Criar `frontend/src/paginas/TelaPainel.tsx` (tela 02 e variações 02b–02d; saudação, relatórios em aberto, total e até 5 alunos, ações; menu com tema claro/escuro e "Encerrar sessão" que só navega após 2xx)
- [X] T077 [US2] Criar `frontend/src/paginas/TelaAlunos.tsx` e `frontend/src/componentes/CartaoAluno.tsx` (tela 03 e variações 03b–03c; busca com atraso; marca "Em aberto"; ações Registrar aula, Relatório semestral e Histórico)
- [X] T078 [US2] Criar o roteiro de paridade em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/paridade/`: `RoteiroParidade` (passos abstratos), `CanalWebDriver` (MockMvc) e `CanalTelegramDriver` (updates no webhook), e `RoteiroParidadeAcessoIT` (entrar/vincular, início, busca, bloqueio, encerrar; compara os resultados nos dois canais)

**Checkpoint**: acesso, início, busca e encerramento funcionam nos dois canais (quickstart V1, V2, V15).

---

## Phase 4: User Story 1 - Registrar uma aula por áudio até a aprovação, em qualquer canal (Priority: P1) 🎯 MVP

**Goal**: áudio (gravação, arquivo, voz ou vídeo até 50 MB) → transcrição → estruturação em 5 seções →
até 2 rodadas de perguntas (texto ou áudio) → conferência com texto e PDF → alterações (texto ou áudio,
cada uma com nova versão e PDF) → aprovação explícita da versão vigente com PDF oficial. Tudo nos dois canais.

**Independent Test**: com Clara e seus alunos, executar o fluxo completo uma vez em cada canal com as
gravações de `assets/` (quickstart V3, V4, V5, V12, V13, V14): enviar o áudio, responder às perguntas,
pedir uma alteração por texto e outra por áudio e aprovar. Os dois relatórios ficam aprovados na versão
vigente, com `documento_pdf` do tipo `OFICIAL`, e as mesmas entradas produzem os mesmos estados. (A
listagem no histórico é validada em US5.)

### Testes da User Story 1 ⚠️

- [X] T079 [P] [US1] Escrever `RelatorioAulaTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/relatorio/RelatorioAulaTeste.java` cobrindo todas as transições e proibições de `data-model.md` §8.1 (rodadas ≤ 2, versão vigente, aprovar só sem etapa em curso, cancelar `EM_PROCESSAMENTO` só por descarte, ações sobre `APROVADO` e `CANCELADO`)
- [X] T080 [P] [US1] Escrever `ConteudoRelatorioAulaTeste`, `VersaoRelatorioTeste`, `InteracaoRelatorioTeste` e `DocumentoPdfTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/relatorio/` e `TrabalhoProcessamentoTeste` (status, etapas, `demorando` aos 3 min, tentativas ≤ 2) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/trabalho/`
- [X] T081 [P] [US1] Escrever `RegistrarAulaPorAudioCasoDeUsoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/aula/` (associação, `RELATORIO_EM_ABERTO` com detalhes, registro em transcrição conta como em aberto, `AUDIO_MUITO_GRANDE`, trabalho enfileirado com o canal, data da aula perto da meia-noite no fuso da escola)
- [X] T082 [P] [US1] Escrever `ProcessadorTrabalhoRegistroTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/servico/` (transcrição vazia → `AUDIO_NAO_PROCESSAVEL` sem nada criado; sucesso cria `Aula` + `RelatorioAula`; perguntas → `AGUARDANDO_RESPOSTAS`; sem perguntas → versão 1 + PDF de conferência; saída inválida → `FALHA_PROCESSAMENTO` com a transcrição preservada; provedor indisponível → 1 nova tentativa; `NOVA_TENTATIVA` sem áudio; áudio apagado em todos os caminhos; `ChamadaIa` e `tempo_processamento_ms` registrados; etapas notificadas; a entrada da IA nunca contém nome, CPF nem data de nascimento)
- [X] T083 [P] [US1] Escrever `ProcessadorTrabalhoRespostasAlteracoesTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/servico/` (resposta por texto e por áudio; transcrição falha → sem interação e sem consumir rodada; perguntas da 2ª rodada ignoradas; alteração cria v+1 com PDF; `semAlteracaoDeConteudo`, `pedidoDeAcao` e conteúdo idêntico não criam versão e nunca aprovam nem cancelam; `versaoBase` desatualizada → `RECUSADA_VERSAO_DESATUALIZADA`; texto entendido devolvido; resultado descartado se o relatório foi cancelado durante o trabalho)
- [X] T084 [P] [US1] Escrever `ResponderPerguntasCasoDeUsoTeste`, `SolicitarAlteracaoRelatorioAulaCasoDeUsoTeste`, `AprovarRelatorioAulaCasoDeUsoTeste` (versão desatualizada, aprovação dupla, grava canal e gera PDF oficial), `CancelarRelatorioAulaCasoDeUsoTeste`, `TentarNovamenteRelatorioAulaCasoDeUsoTeste` e `ObterRelatorioAulaCasoDeUsoTeste` (outro professor → 404) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/aula/`, `ConsultarTrabalhoCasoDeUsoTeste` em `…/casodeuso/trabalho/` e `ObterDocumentoRelatorioCasoDeUsoTeste` em `…/casodeuso/documento/` (sob `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/`)
- [X] T085 [P] [US1] Escrever `LlmOpenAiCompativelAdaptadorIT` (modo JSON, saída válida, inválida → correção, inválida duas vezes → `SaidaIaInvalidaExcecao`, 429 com nova tentativa, timeout, campos desconhecidos descartados) e `TranscricaoOpenAiCompativelAdaptadorIT` (multipart `audio.ogg`, `language=pt`, vazio → `SEM_FALA`, 4xx → `FORMATO`, 5xx → `ProvedorIndisponivelExcecao`) com WireMock em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/ia/`
- [X] T086 [P] [US1] Escrever `FfmpegNormalizacaoAudioAdaptadorIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/audio/` com amostras curtas geradas em `backend/aplicacao/src/test/resources/audio/` (ogg, mp4 com trilha de áudio, silêncio < 1 s, `.txt` renomeado para `.ogg`): saída Opus mono 16 kHz, extração do mp4, `FormatoNaoSuportadoExcecao`, `VAZIO`
- [X] T087 [P] [US1] Escrever `PdfRelatorioAulaIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/pdf/` (extrai o texto com PDFBox: título, cabeçalho, 5 seções com acentos, "—" nas vazias, faixa "Documento em conferência — não oficial", "Aprovado por … · versão N" no oficial, "Página X de Y")
- [X] T088 [P] [US1] Escrever `FilaTrabalhoPostgresAdaptadorIT` (dois workers com `SKIP LOCKED` sem duplicar trabalho, índice parcial de `REGISTRO_AULA`, recuperação de trabalho travado após reinício, marcação de demora) em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/fila/` e `RelatorioAulaConcorrenciaIT` (aprovação concorrente pelos dois canais com bloqueio otimista; índice parcial de relatório em aberto) em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/persistencia/`
- [X] T089 [P] [US1] Escrever `RelatorioAulaContratoIT` (todos os endpoints de `relatorios-aula`, variantes JSON e multipart, 202/400/404/409/413/415), `TrabalhoContratoIT` e `DocumentoConferenciaContratoIT` (PDF inline, `Cache-Control: private, no-store`, nome sem identificador interno) em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/web/`
- [X] T090 [P] [US1] Escrever `RegistroAulaConversaIT` (sequência 5.2, passos 1–5: `ALUNO_SELECIONADO`; `voice`, `audio`, `video`, `video_note` e `document`; acima do limite sem baixar; `confal`/`trocal`; novo áudio substitui; expiração em 30 min; a mesma mensagem editada a cada etapa; perguntas com lembrete de foco; conferência dividida acima de 4096 caracteres com PDF como documento; falha com `tent`/`canc`) em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/`
- [X] T091 [P] [US1] Escrever `ConferenciaConversaIT` (estados #3 a #6 e passos 6–8 da sequência 5.2: resposta por texto e por voz sem confirmação, alteração por texto e por áudio com "Entendi: …", nome exato de outro aluno → `TROCAR_ALUNO_DURANTE_CONFERENCIA`, palavras-chave antes de texto livre, `aprov`/`aprovok` citando a versão, `canc`/`cancok`, seleção e foco limpos, `PROCESSAMENTO_EM_ANDAMENTO`, `tent`) em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/`
- [X] T092 [P] [US1] Escrever `NotificacaoTelegramAdaptadorIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/telegram/` (trabalho pedido pela Web com vínculo → `AVISO_RESULTADO_WEB_*` com "Continuar no chat" sem mudar o foco; sem vínculo → nada; `PROCESSAMENTO_DEMORADO` aos 3 min)
- [X] T093 [P] [US1] Escrever `GravadorAudio.test.tsx` em `frontend/tests/componentes/` (MediaRecorder simulado: gravar, ouvir, regravar, enviar; envio só com gravação ou arquivo; sem suporte → só arquivo com `GRAVACAO_INDISPONIVEL`; limite de tamanho) e `TelaGravacao.test.tsx`, `TelaProcessando.test.tsx` (polling de 2 s que para no estado final e pausa com a aba oculta, aviso de demora), `TelaPerguntas.test.tsx`, `TelaConferencia.test.tsx` (abas a 390 px, lado a lado a 1440 px, diálogos, texto entendido, nenhuma navegação sem 2xx) e `TelaAprovado.test.tsx` em `frontend/tests/paginas/`

### Implementação da User Story 1: núcleo

- [X] T094 [P] [US1] Criar `StatusRelatorio`, `EtapaRelatorio`, `ConteudoRelatorioAula` e `Aula` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/relatorio/`
- [X] T095 [US1] Criar `RelatorioAula` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/relatorio/RelatorioAula.java` (máquina de estados do §8.1: `criar`, `registrarEstruturacao`, `falharEstruturacao`, `prepararNovaTentativa`, `iniciarEtapa`/`concluirEtapa`, `incorporarResposta`, `aplicarAlteracao`, `aprovar(versaoConfirmada, canal, agora)`, `cancelar`)
- [X] T096 [P] [US1] Criar `VersaoRelatorio`, `OrigemVersao`, `InteracaoRelatorio`, `TipoInteracao`, `ResultadoInteracao`, `DocumentoPdf` e `TipoDocumento` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/relatorio/`
- [X] T097 [P] [US1] Criar `TrabalhoProcessamento`, `TipoTrabalho`, `StatusTrabalho`, `EtapaTrabalho`, `ResultadoTrabalho` e `ChamadaIa` (com `TarefaIa`) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/trabalho/`
- [X] T098 [US1] Criar as portas de áudio e IA em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/`: `TranscricaoPorta` (`AudioNormalizado`, `ResultadoTranscricao`, `AudioNaoProcessavelExcecao`, `ProvedorIndisponivelExcecao`), `NormalizacaoAudioPorta` (`ArquivoTemporario`, `FormatoNaoSuportadoExcecao`), `ArquivoCanalPorta` (`ReferenciaArquivoCanal`) e `LlmPorta` (`TarefaLlm`, `EntradaTarefa` selada com `EntradaEstruturacaoAula`, `EntradaIncorporacaoRespostas`, `EntradaRevisaoAula`; `RespostaLlm`; `SaidaRelatorioAulaIa`; `SaidaIaInvalidaExcecao`), conforme `contracts/portas-provedores.md` §1–3
- [X] T099 [US1] Criar as demais portas em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/`: `GeracaoPdfPorta` (`ModeloPdfRelatorioAula`, `DocumentoGerado`, `SituacaoDocumento`), `ArmazenamentoDocumentoPorta`, `NotificacaoProfessorPorta`, `FilaTrabalhoPorta`, `RegistroCustoPorta`, `RelatorioAulaRepositorio` (inclui gravar a `Aula`), `VersaoRelatorioRepositorio`, `InteracaoRelatorioRepositorio` e `DocumentoPdfRepositorio`
- [X] T100 [US1] Criar os dublês em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dubles/`: `TranscricaoRoteirizada`, `NormalizacaoAudioFalsa`, `LlmRoteirizado` (respostas por tarefa, falhas programáveis, registra as entradas recebidas), `GeracaoPdfFalsa`, `ArmazenamentoDocumentoEmMemoria`, `NotificacaoRegistradora`, `FilaTrabalhoEmMemoria`, `RegistroCustoEmMemoria`, `ArquivoTemporarioRastreado` (verifica a remoção) e os repositórios em memória de relatório, versão, interação e documento
- [X] T101 [US1] Criar `AudioTemporarioServico` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/servico/AudioTemporarioServico.java` (normaliza, transcreve e apaga original e normalizado num `finally`; FR-018)
- [X] T102 [US1] Criar `GeracaoDocumentoServico` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/servico/GeracaoDocumentoServico.java` (monta o modelo a partir do relatório, da versão e dos nomes de aluno e professor, gera, salva com a chave interna de R-11 e registra `DocumentoPdf` de conferência ou oficial)
- [X] T103 [US1] Criar `ProcessadorTrabalhoServico` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/servico/ProcessadorTrabalhoServico.java` (executa `REGISTRO_AULA`, `NOVA_TENTATIVA`, `RESPOSTA_PERGUNTAS` e `ALTERACAO`; atualiza etapa do trabalho e do relatório; notifica etapas e resultado; aplica as regras da saída da IA de `contracts/portas-provedores.md` §6.1; registra `InteracaoRelatorio`, `ChamadaIa` e tempo; descarta o resultado se o relatório foi cancelado)
- [X] T104 [US1] Criar `RegistrarAulaPorAudioCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/aula/RegistrarAulaPorAudioCasoDeUso.java` (aceita `ArquivoTemporario` da Web ou `ReferenciaArquivoCanal` do Telegram; autorização, relatório em aberto, tamanho; enfileira `REGISTRO_AULA` com o canal)
- [X] T105 [US1] Criar `ResponderPerguntasCasoDeUso` (`RESPOSTA_VAZIA`), `SolicitarAlteracaoRelatorioAulaCasoDeUso` (`ALTERACAO_VAZIA`, `VERSAO_DESATUALIZADA` com a versão vigente) e `TentarNovamenteRelatorioAulaCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/aula/`, aceitando texto ou áudio e enfileirando o trabalho
- [X] T106 [US1] Criar `AprovarRelatorioAulaCasoDeUso` (gera o PDF oficial) e `CancelarRelatorioAulaCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/aula/`
- [X] T107 [US1] Criar `ObterRelatorioAulaCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/aula/`, `ConsultarTrabalhoCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/trabalho/` e `ObterDocumentoRelatorioCasoDeUso` (PDF de conferência por versão e oficial, sempre após verificar dono e associação; FR-048) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/documento/`

### Implementação da User Story 1: adaptadores de saída

- [X] T108 [US1] Criar as migrações `backend/aplicacao/src/main/resources/db/migration/V3__relatorio_aula.sql` (`aula`, `relatorio_aula` com índice único parcial de em aberto, `versao_relatorio`, `interacao_relatorio`, `documento_pdf`) e `backend/aplicacao/src/main/resources/db/migration/V4__processamento.sql` (`trabalho_processamento` com índice único parcial de `REGISTRO_AULA`, `chamada_ia`)
- [X] T109 [US1] Criar entidades (seções em `jsonb`), mapeadores e `RelatorioAulaRepositorioAdaptador`, `VersaoRelatorioRepositorioAdaptador`, `InteracaoRelatorioRepositorioAdaptador`, `DocumentoPdfRepositorioAdaptador` e `RegistroCustoAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/`
- [X] T110 [US1] Criar `FilaTrabalhoPostgresAdaptador` (`SELECT … FOR UPDATE SKIP LOCKED`, concluir, falhar, descartar, recuperar travados), `TrabalhoWorker` (threads virtuais, chama `ProcessadorTrabalhoServico`) e `AvisoDemoraTarefa` (aos 3 min marca `aviso_demora_enviado` e notifica) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/fila/`
- [X] T111 [US1] Criar `RelatoriosEmAbertoConsultaAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/RelatoriosEmAbertoConsultaAdaptador.java` (relatórios de aula em aberto + trabalhos `REGISTRO_AULA` pendentes ou em execução, com canal de origem e `atualizado_em`; última aula por aluno) e remover `RelatoriosEmAbertoConsultaProvisoriaAdaptador`
- [X] T112 [P] [US1] Criar os prompts `estruturacao-aula.v1.txt`, `incorporacao-respostas.v1.txt`, `revisao-aula.v1.txt` e `correcao-json.v1.txt` em `backend/aplicacao/src/main/resources/prompts/` (pontos obrigatórios de `contracts/portas-provedores.md` §7) e o schema `backend/aplicacao/src/main/resources/ia/schemas/relatorio-aula.schema.json` (§6.1)
- [X] T113 [US1] Criar `LlmOpenAiCompativelAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/ia/LlmOpenAiCompativelAdaptador.java` (`chat/completions` em modo JSON, temperatura 0,2, timeout de 45 s, 1 nova tentativa com espera exponencial, disjuntor simples, validação pelo schema, uma chamada de correção, tokens e custo pela tabela de preços configurada)
- [X] T114 [P] [US1] Criar `TranscricaoOpenAiCompativelAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/ia/TranscricaoOpenAiCompativelAdaptador.java` (`audio/transcriptions`, `language=pt`, timeout de 60 s, 1 nova tentativa em erro transitório, custo por segundo de áudio)
- [X] T115 [P] [US1] Criar `LlmSimuladoAdaptador` e `TranscricaoSimuladaAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/ia/simulado/` com respostas determinísticas em `backend/aplicacao/src/main/resources/ia/simulado/` (com perguntas na 1ª estruturação, sem perguntas após a resposta, alteração que muda a observação), usados quando `PROVEDORES_IA=simulado`
- [X] T116 [P] [US1] Criar `FfmpegNormalizacaoAudioAdaptador` (`ffmpeg -vn -ac 1 -ar 16000 -c:a libopus -b:a 24k`, duração por `ffprobe`, timeout, em `/tmp/audios`) e `LimpezaAudiosTemporariosTarefa` (ao iniciar apaga sobras de `/tmp/audios`; periodicamente apaga arquivos com mais de 30 min no volume do `telegram-bot-api`) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/audio/`
- [X] T117 [US1] Criar `ArquivoTelegramAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/audio/ArquivoTelegramAdaptador.java` (`getFile` no servidor local → caminho no volume compartilhado → cópia para `/tmp/audios` → remoção do original)
- [X] T118 [P] [US1] Criar o template `backend/aplicacao/src/main/resources/pdf/relatorio-aula.html` e `backend/aplicacao/src/main/resources/pdf/estilos-pdf.css` reproduzindo `portal-do-professor-design-system/project/Modelo PDF.dc.html` (fontes TTF embutidas, logo e filete, cabeçalho, seções, "—", rodapé com `counter(page)`/`counter(pages)`, faixa de conferência e bloco de aprovação)
- [X] T119 [US1] Criar `OpenHtmlToPdfGeracaoPdfAdaptador` (Thymeleaf + OpenHTMLtoPDF, SHA-256 e tamanho) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/pdf/` e `SistemaArquivosArmazenamentoDocumentoAdaptador` (volume `documentos`, escrita atômica, chaves internas) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/armazenamento/`
- [X] T120 [US1] Criar `NotificacaoTelegramAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/telegram/NotificacaoTelegramAdaptador.java` (trabalho do Telegram: edita a mensagem de etapa e entrega o resultado pelo `ApresentadorTelegram`; trabalho da Web com vínculo: `AVISO_RESULTADO_WEB_PRONTO`/`_PERGUNTAS`/`_NOVA_VERSAO`/`_FALHA` com o botão `v1:foco:<relatorioId>` "Continuar no chat", sem mudar o foco)
- [X] T121 [US1] Criar `ProvedoresIaConfiguracao` (escolhe adaptadores reais ou simulados por `PROVEDORES_IA`; URL, modelo, chave e tabela de preços por ambiente) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/ProvedoresIaConfiguracao.java` e registrar os casos de uso e serviços de US1 em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/CasosDeUsoConfiguracao.java`

### Implementação da User Story 1: canal Web

- [X] T122 [US1] Criar `RecepcaoAudioWeb` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/RecepcaoAudioWeb.java` (multipart gravado em streaming em `/tmp/audios`, limite → 413 `AUDIO_MUITO_GRANDE`, lista de tipos aceitos → 415 `FORMATO_NAO_SUPORTADO`) e os limites `spring.servlet.multipart` em `backend/aplicacao/src/main/resources/application.yaml`
- [X] T123 [US1] Criar `RelatorioAulaControlador` (registrar, obter, respostas JSON ou multipart, alterações JSON ou multipart, nova tentativa, aprovação, cancelamento) com `RelatorioAulaDto`, `ConteudoRelatorioAulaDto`, `InteracaoDto`, `AlteracaoTextoRequisicao`, `AprovacaoRequisicao`, `AprovacaoDto`, `TrabalhoRefDto` e `RelatorioAulaMapeador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/`
- [X] T124 [US1] Criar `TrabalhoControlador` (`GET /trabalhos/{id}` com `demorando` e `textoEntendido`), `TrabalhoDto`, `TrabalhoMapeador` e `DocumentoControlador` (`GET /relatorios-aula/{id}/versoes/{n}/pdf` com `Content-Disposition` inline e `Cache-Control: private, no-store`) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/`
- [X] T125 [P] [US1] Criar `frontend/src/componentes/GravadorAudio.tsx` (MediaRecorder `audio/webm;codecs=opus` ou `audio/mp4`; gravando/concluída; ouvir, regravar, enviar; seleção de arquivo; limite conferido antes do envio; fallback `GRAVACAO_INDISPONIVEL`; rótulos acessíveis)
- [X] T126 [P] [US1] Criar `frontend/src/servicos/relatoriosAula.ts`, `frontend/src/servicos/trabalhos.ts` (`usePollingTrabalho`: 2 s, pausa com aba oculta, limite de 10 min) e `frontend/src/servicos/pdf.ts` (fetch autenticado → blob URL)
- [X] T127 [US1] Criar `frontend/src/paginas/TelaGravacao.tsx` (tela 05 e variações 05b–05e; aluno e data de hoje; `FORMATOS_E_LIMITE` com `limiteAudioMb`; 409 `RELATORIO_EM_ABERTO` mostra a mensagem)
- [X] T128 [US1] Criar `frontend/src/paginas/TelaProcessando.tsx` e `frontend/src/componentes/EtapasProcessamento.tsx` (tela 06 e variações 06b–06c; `aria-live="polite"`; aviso de demora; encaminha por `resultadoCodigo` para perguntas, conferência ou falha com "Tentar novamente"/"Cancelar")
- [X] T129 [US1] Criar `frontend/src/paginas/TelaPerguntas.tsx` (tela 07; perguntas numeradas; resposta por texto ou `GravadorAudio`; `RESPOSTA_VAZIA`; "Entendi: …" depois da incorporação)
- [X] T130 [US1] Criar `frontend/src/componentes/SecoesRelatorio.tsx`, `frontend/src/componentes/VisualizadorPdf.tsx` e `frontend/src/paginas/TelaConferencia.tsx` (tela 08 e variações 08b–08c; abas "Texto"/"Ver PDF" a 390 px e lado a lado a 1440 px; "Precisa ajustar algo?" com texto ou `GravadorAudio`; `ALTERACAO_APLICADA` + `TEXTO_ENTENDIDO`; `SEM_ALTERACAO_DE_CONTEUDO`, `PEDIDO_DE_ACAO_IGNORADO`, `FALHA_ALTERACAO`; diálogos de aprovar, citando a versão, e de cancelar; envia `versaoBase`/`versaoConfirmada`)
- [X] T131 [US1] Criar `frontend/src/paginas/TelaAprovado.tsx` (tela 09 e 09b; data e hora da aprovação; "Registrar outra aula" e "Ver histórico")

### Implementação da User Story 1: canal Telegram

- [X] T132 [US1] Estender `InterpretadorConversaTelegram` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/InterpretadorConversaTelegram.java` com o registro: `reg`/"registrar aula" limpa o foco sem cancelar; aluno escolhido → `ALUNO_SELECIONADO`; estados #7 e #10 com áudio (guarda só o `file_id`, confere o tamanho, `AUDIO_RECEBIDO_CONFIRMAR_ALUNO` ou `AUDIO_RECEBIDO_ESCOLHER_ALUNO`, substitui, expira com `AUDIO_PENDENTE_EXPIRADO`, descarta ao iniciar outra ação); `confal`/`trocal`/`aluno` → `RegistrarAulaPorAudioCasoDeUso`; relatório em aberto na confirmação → descarta o áudio e mostra `RELATORIO_EM_ABERTO`
- [X] T133 [US1] Estender `InterpretadorConversaTelegram` com os estados #3 a #6: resposta por texto ou áudio direto (FR-027b), alteração por texto ou áudio com `versaoBase = versao_apresentada`, nome exato de outro aluno → `TROCAR_ALUNO_DURANTE_CONFERENCIA`, palavras-chave primeiro, `alt`, `pdf`, `aprov`/`aprovok`, `canc`/`cancok`, `tent`, `PROCESSAMENTO_EM_ANDAMENTO`, e limpeza de seleção e foco após aprovar ou cancelar (FR-038)
- [X] T134 [US1] Estender `ApresentadorTelegram` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/ApresentadorTelegram.java` com a conferência (cabeçalho, "Versão N", 5 seções com "—", divisão acima de 4096 caracteres, PDF por `sendDocument`, botões `aprov`/`alt`/`canc`/`reg`, `LEMBRETE_FOCO`), perguntas numeradas, textos de etapa, "Entendi: …", `RELATORIO_APROVADO` com "Registrar outra aula" e "Histórico"; grava `versao_apresentada` e o relatório em foco na conversa
- [X] T135 [US1] Tratar o botão `foco` ("Continuar no chat") em `InterpretadorConversaTelegram` (revalida dono e estado, põe o relatório em foco e apresenta a etapa) e acrescentar a ação `foco` (argumento: UUID do relatório) à tabela da seção 3 de `specs/001-fluxo-relatorios/contracts/telegram-conversa.md`

### Paridade da User Story 1

- [X] T136 [US1] Escrever `RoteiroParidadeRelatorioAulaIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/paridade/RoteiroParidadeRelatorioAulaIT.java` (IA simulada: enviar áudio, responder, alterar por texto, alterar por áudio, aprovar; compara estados, versões, interações com canal e meio, PDF oficial e aprovação dupla recusada, nos dois canais; SC-006)

**Checkpoint**: MVP (US2 + US1) completo nos dois canais (quickstart V3, V4, V5, V12, V13, V14).

---

## Phase 5: User Story 3 - Continuar um relatório em aberto no outro canal (Priority: P2)

**Goal**: aviso "Você tem um relatório em aberto" (canal de origem, data e hora) com continuar ou
descartar nos dois canais; relatórios em aberto no início; versão desatualizada reapresentada.

**Independent Test**: iniciar num canal, parar antes da aprovação e concluir no outro; repetir
escolhendo descartar; nos dois sentidos (quickstart V6, V7).

### Testes da User Story 3 ⚠️

- [X] T137 [P] [US3] Escrever `ObterRelatorioEmAbertoCasoDeUsoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/aula/` e `DescartarRegistroEmProcessamentoCasoDeUsoTeste` (descarta trabalho pendente ou em execução; resultado posterior do worker é ignorado; outro professor → 404) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/trabalho/`
- [X] T138 [P] [US3] Escrever `RetomadaContratoIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/web/RetomadaContratoIT.java` (`GET /alunos/{id}/relatorio-em-aberto` 200/204/403; `POST /trabalhos/{id}/descarte` 204/404/409)
- [X] T139 [P] [US3] Escrever `RetomadaConversaIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/RetomadaConversaIT.java` (sequência 5.3: aviso com canal e data; `cont` reapresenta processamento, perguntas ou conferência na versão vigente; `desc` → `CONFIRMAR_DESCARTE` → cancela → pede áudio ou reaproveita o áudio pendente válido; botões "Continuar: {aluno} ({data})" no início; aprovar versão antiga → `VERSAO_DESATUALIZADA` + conferência vigente)
- [X] T140 [P] [US3] Escrever `TelaRelatorioEmAberto.test.tsx` e `TelaConferenciaVersaoDesatualizada.test.tsx` (estado 08d) em `frontend/tests/paginas/`

### Implementação da User Story 3

- [X] T141 [US3] Criar `ObterRelatorioEmAbertoCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/aula/` e `DescartarRegistroEmProcessamentoCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/trabalho/`, e fazer `ProcessadorTrabalhoServico` ignorar resultado de trabalho `DESCARTADO` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/servico/ProcessadorTrabalhoServico.java`
- [X] T142 [US3] Acrescentar `GET /alunos/{id}/relatorio-em-aberto` (com `RelatorioEmAbertoDto` e mapeador) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/RelatorioAulaControlador.java`, `POST /trabalhos/{id}/descarte` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/TrabalhoControlador.java` e registrar os novos casos de uso em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/CasosDeUsoConfiguracao.java`
- [X] T143 [US3] Estender `InterpretadorConversaTelegram` e `ApresentadorTelegram` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/` com `cont`/`desc` e "continuar"/"descartar", o aviso `RELATORIO_EM_ABERTO` ("pelo Telegram"/"pelo portal", data e hora), o reaproveitamento do áudio pendente, os botões "Continuar: {aluno} ({data})" no início e a reapresentação da versão vigente após `VERSAO_DESATUALIZADA`
- [X] T144 [US3] Criar `frontend/src/paginas/TelaRelatorioEmAberto.tsx` (tela 04 e diálogo 04b; continuar leva a `/trabalhos/:id`, perguntas ou conferência conforme status e etapa; descartar usa cancelamento ou descarte de trabalho e segue para `/alunos/:alunoId/nova-aula`)
- [X] T145 [US3] Integrar a retomada em `frontend/src/paginas/TelaPainel.tsx` (cartões "Continuar" dos relatórios em aberto), `frontend/src/paginas/TelaAlunos.tsx` ("Registrar aula" consulta o relatório em aberto e leva à tela 04) e `frontend/src/paginas/TelaGravacao.tsx` (409 `RELATORIO_EM_ABERTO` leva à tela 04)
- [X] T146 [US3] Implementar o estado 08d em `frontend/src/paginas/TelaConferencia.tsx` (409 `VERSAO_DESATUALIZADA` recarrega o relatório, mostra o aviso e só então permite aprovar ou alterar)
- [X] T147 [US3] Escrever `RoteiroParidadeRetomadaIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/paridade/RoteiroParidadeRetomadaIT.java` (Web → Telegram e Telegram → Web, continuar e descartar, versão desatualizada; SC-009)

**Checkpoint**: US1, US2 e US3 funcionam de forma independente e combinada.

---

## Phase 6: User Story 4 - Gerar e aprovar o relatório semestral, em qualquer canal (Priority: P2)

**Goal**: escolha do semestre, prévia com as contagens, consolidação das aulas aprovadas do próprio
professor em 10 seções (Informações Gerais pelo sistema) e o mesmo ciclo de conferência, alteração e
aprovação, nos dois canais.

**Independent Test**: com aulas aprovadas, gerar o semestral de um aluno pela Web e de outro pelo
Telegram, alterar, aprovar e tentar gerar de novo; repetir num semestre sem aulas aprovadas
(quickstart V8, V9).

### Testes da User Story 4 ⚠️

- [X] T148 [P] [US4] Escrever `PeriodoSemestralTeste` (chave `AAAA-S`, datas, rótulos, "em andamento", lista desde a primeira aula ou o cadastro até o semestre corrente), `InformacoesGeraisTeste` (idade no fim do período ou hoje) e `RelatorioSemestralTeste` (estados, versões, proibições) em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/semestral/`
- [X] T149 [P] [US4] Escrever `ListarSemestresDisponiveisCasoDeUsoTeste`, `ObterPreviaSemestralCasoDeUsoTeste`, `GerarRelatorioSemestralCasoDeUsoTeste` (pré-condições na ordem de `data-model.md` §9; zero aulas → nada criado e nenhuma chamada à IA; `HA_RELATORIOS_EM_ABERTO` sem `confirmarMesmoComEmAberto`; consolidação em curso conta como em aberto), `SolicitarAlteracaoRelatorioSemestralCasoDeUsoTeste`, `AprovarRelatorioSemestralCasoDeUsoTeste`, `CancelarRelatorioSemestralCasoDeUsoTeste` e `ObterRelatorioSemestralCasoDeUsoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/semestral/`
- [X] T150 [P] [US4] Escrever `ProcessadorConsolidacaoSemestralTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/servico/` (só aulas aprovadas do professor no período, com as datas; entrada da IA sem nome, idade, CPF ou nascimento; Informações Gerais preenchidas pelo sistema e chave vinda da IA descartada; sucesso → `AGUARDANDO_CONFERENCIA` v1 + PDF; falha → nada criado e `FALHA_CONSOLIDACAO`; `quantidade_relatorios_em_aberto` gravada; alteração via `REVISAO_SEMESTRAL`)
- [X] T151 [P] [US4] Escrever `RelatorioSemestralContratoIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/web/RelatorioSemestralContratoIT.java` (semestres, prévia, geração 202 e cada 409, obter, alterações JSON e multipart, aprovação, cancelamento, PDF por versão)
- [X] T152 [P] [US4] Escrever `SemestralConversaIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/SemestralConversaIT.java` (sequência 5.4 e estado #8: aluno, períodos com "★" e "em andamento", cada ramo da prévia, `gerarsim`/`voltar`, etapas, conferência com "N relatórios…", aviso e PDF, alteração por texto e áudio, aprovar, cancelar)
- [X] T153 [P] [US4] Escrever `PdfRelatorioSemestralIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/pdf/PdfRelatorioSemestralIT.java` (10 seções, Informações Gerais, quantidade consolidada, conferência e oficial)
- [X] T154 [P] [US4] Escrever `TelaSemestral.test.tsx` (10, 10b, 10c, aviso de em aberto, já aprovado, semestral em aberto) e `TelaConferenciaSemestral.test.tsx` em `frontend/tests/paginas/`

### Implementação da User Story 4

- [X] T155 [P] [US4] Criar `PeriodoSemestral`, `InformacoesGerais` e `ConteudoRelatorioSemestral` (records das seções 2 a 10 de `data-model.md` §9) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/semestral/`
- [X] T156 [US4] Criar `RelatorioSemestral` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/semestral/RelatorioSemestral.java` (nasce em `AGUARDANDO_CONFERENCIA` v1; alterar, aprovar e cancelar com as regras do relatório de aula)
- [X] T157 [US4] Criar `RelatorioSemestralRepositorio` e acrescentar a `RelatorioAulaRepositorio` as consultas de aprovados no período (com datas e conteúdos), em aberto no período e data da primeira aula, em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/`; acrescentar `EntradaConsolidacaoSemestral`, `EntradaRevisaoSemestral` e `SaidaRelatorioSemestralIa` a `LlmPorta` e `gerarRelatorioSemestral`/`ModeloPdfRelatorioSemestral` a `GeracaoPdfPorta`; atualizar os dublês em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dubles/`
- [X] T158 [US4] Criar `ListarSemestresDisponiveisCasoDeUso`, `ObterPreviaSemestralCasoDeUso` e `GerarRelatorioSemestralCasoDeUso` (enfileira `CONSOLIDACAO_SEMESTRAL`) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/semestral/`
- [X] T159 [US4] Criar `SolicitarAlteracaoRelatorioSemestralCasoDeUso`, `AprovarRelatorioSemestralCasoDeUso`, `CancelarRelatorioSemestralCasoDeUso` e `ObterRelatorioSemestralCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/semestral/` e estender `ObterDocumentoRelatorioCasoDeUso` para `SEMESTRAL` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/documento/ObterDocumentoRelatorioCasoDeUso.java`
- [X] T160 [US4] Estender `ProcessadorTrabalhoServico` (`CONSOLIDACAO_SEMESTRAL` e `ALTERACAO` de semestral) e `GeracaoDocumentoServico` (modelo semestral) em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/servico/`
- [X] T161 [US4] Criar a migração `backend/aplicacao/src/main/resources/db/migration/V5__relatorio_semestral.sql` (tabela e os dois índices únicos parciais), a entidade, o mapeador e `RelatorioSemestralRepositorioAdaptador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/`, e incluir semestrais em aberto e consolidações em curso em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/RelatoriosEmAbertoConsultaAdaptador.java`
- [X] T162 [P] [US4] Criar os prompts `consolidacao-semestral.v1.txt` e `revisao-semestral.v1.txt` em `backend/aplicacao/src/main/resources/prompts/`, o schema `backend/aplicacao/src/main/resources/ia/schemas/relatorio-semestral.schema.json` (§6.2) e as respostas simuladas em `backend/aplicacao/src/main/resources/ia/simulado/`
- [X] T163 [P] [US4] Criar o template `backend/aplicacao/src/main/resources/pdf/relatorio-semestral.html` (10 seções, Informações Gerais, quantidade de aulas consolidadas, faixa de conferência e bloco de aprovação)
- [X] T164 [US4] Criar `RelatorioSemestralControlador` com `SemestreDto`, `PreviaSemestralDto`, `RelatorioSemestralDto`, `InformacoesGeraisDto`, `ConteudoRelatorioSemestralDto` e `RelatorioSemestralMapeador` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/`, acrescentar a rota de PDF por versão do semestral em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/DocumentoControlador.java` e registrar os casos de uso em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/CasosDeUsoConfiguracao.java`
- [X] T165 [US4] Estender `InterpretadorConversaTelegram` e `ApresentadorTelegram` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/` com o fluxo `SEMESTRAL` (`sem`, aluno, `per`, prévia, `gerar`/`gerarsim`/`voltar`, conferência com `AULAS_CONSIDERADAS`, aviso de em aberto e 10 seções) e reaproveitar alteração, aprovação e cancelamento para o tipo `SEMESTRAL`; entregar os resultados semestrais em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/telegram/NotificacaoTelegramAdaptador.java`
- [X] T166 [US4] Criar `frontend/src/servicos/semestral.ts` e `frontend/src/paginas/TelaSemestral.tsx` (tela 10 e variações 10b–10c; mais recente pré-selecionado com o intervalo de datas; aviso "Há N relatórios de aula em aberto…" com "Gerar mesmo assim"/"Voltar"; já aprovado; semestral em aberto com continuar ou descartar)
- [X] T167 [US4] Criar `frontend/src/paginas/TelaConferenciaSemestral.tsx` (tela 11 reaproveitando `SecoesRelatorio`, `VisualizadorPdf`, `GravadorAudio` e os diálogos; `AULAS_CONSIDERADAS`; aviso repetido; Informações Gerais somente leitura) e a variação semestral de `frontend/src/paginas/TelaAprovado.tsx`
- [X] T168 [US4] Escrever `RoteiroParidadeSemestralIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/paridade/RoteiroParidadeSemestralIT.java` (mesmo roteiro nos dois canais: prévia, aviso, geração, alteração, aprovação, nova geração recusada, período sem aulas)

**Checkpoint**: o semestral funciona de ponta a ponta nos dois canais.

---

## Phase 7: User Story 5 - Consultar o histórico e receber os PDFs oficiais, em qualquer canal (Priority: P3)

**Goal**: lista dos relatórios aprovados do professor para o aluno (aula e semestral), do mais recente
ao mais antigo, idêntica nos dois canais, com entrega autorizada do PDF oficial.

**Independent Test**: consultar o histórico do mesmo aluno nos dois canais e comparar; obter um PDF em
cada canal; tentar o PDF de relatório de outro professor (quickstart V10, V11).

### Testes da User Story 5 ⚠️

- [X] T169 [P] [US5] Escrever `ConsultarHistoricoAlunoCasoDeUsoTeste` em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/historico/` (só aprovados do professor; aula por `data_aula`, semestral por `periodo_inicio`, empate por `aprovado_em`; vazio; não associado → 403) e acrescentar em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/documento/ObterDocumentoRelatorioCasoDeUsoTeste.java` os casos de PDF oficial (não aprovado → 404; outro professor → 404)
- [X] T170 [P] [US5] Escrever `HistoricoContratoIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/web/HistoricoContratoIT.java` (`/alunos/{id}/historico`, `pdf-oficial` de aula e de semestral, 403 e 404 de acesso cruzado)
- [X] T171 [P] [US5] Escrever `HistoricoConversaIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/HistoricoConversaIT.java` (sequência 5.5 e estado #9: até 10 itens por página com "Mais", `hitem` → PDF oficial por `sendDocument`, `HISTORICO_VAZIO` com "Registrar aula")
- [X] T172 [P] [US5] Escrever `TelaHistorico.test.tsx` em `frontend/tests/paginas/` (12, 12b, 12c; "Abrir PDF" em nova aba via blob; aba bloqueada → download e `PDF_BLOQUEADO_NAVEGADOR`)

### Implementação da User Story 5

- [X] T173 [US5] Criar `ConsultarHistoricoAlunoCasoDeUso` em `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/casodeuso/historico/ConsultarHistoricoAlunoCasoDeUso.java` e as consultas `listarAprovados` em `RelatorioAulaRepositorio` e `RelatorioSemestralRepositorio` (`backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/`), com os adaptadores correspondentes em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/`
- [X] T174 [US5] Criar `HistoricoControlador`, `HistoricoDto`, `ItemHistoricoDto` e `HistoricoMapeador` (rótulos `ROTULO_AULA`/`ROTULO_SEMESTRAL` e datas no fuso da escola) em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/`, as rotas `pdf-oficial` de aula e de semestral em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/web/DocumentoControlador.java` e o registro em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/configuracao/CasosDeUsoConfiguracao.java`
- [X] T175 [US5] Estender `InterpretadorConversaTelegram` e `ApresentadorTelegram` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/` com o fluxo `HISTORICO` (`hist`, aluno, lista paginada com os mesmos rótulos da Web, `hitem` → PDF oficial, vazio)
- [X] T176 [US5] Criar `frontend/src/servicos/historico.ts` e `frontend/src/paginas/TelaHistorico.tsx` (tela 12 e variações 12b–12c; "Abrir PDF" com fetch autenticado → blob → nova aba, ou download se bloqueada)
- [X] T177 [US5] Escrever `RoteiroParidadeHistoricoIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/paridade/RoteiroParidadeHistoricoIT.java` (lista idêntica em ordem, rótulos e datas; PDF oficial igual byte a byte nos dois canais)

**Checkpoint**: todas as histórias funcionam de forma independente nos dois canais.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: acessibilidade, desempenho, segurança, privacidade, implantação e validação final.

- [X] T178 [P] Escrever os testes ponta a ponta `fluxo-aula.spec.ts`, `retomada.spec.ts`, `fluxo-semestral.spec.ts` e `historico.spec.ts` em `frontend/tests/e2e/` contra a pilha de `infra/compose.test.yaml` com `PROVEDORES_IA=simulado`
- [X] T179 [P] Escrever `frontend/tests/e2e/acessibilidade.spec.ts` (axe nas telas 01 a 12 e variações, a 390 px e 1440 px, temas claro e escuro, sem rolagem horizontal, navegação por teclado; SC-011) e corrigir as violações em `frontend/src/`
- [X] T180 [P] Escrever `AcessoCruzadoIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/seguranca/AcessoCruzadoIT.java` (com a sessão e o vínculo de Clara, todo endpoint e toda ação do bot sobre o aluno e os relatórios de Bruno → 403, 404 ou "Nenhum aluno encontrado…", sem revelar dados; SC-008)
- [X] T181 [P] Escrever `NaoRetencaoAudioIT` e `LogSemDadosSensiveisIT` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/privacidade/` (`/tmp/audios` e o volume do `telegram-bot-api` vazios após sucesso e falha; nenhum byte de áudio no banco; logs sem transcrição, nome de aluno, CPF, código nem token; FR-018, FR-055)
- [ ] T182 [P] Criar a suíte `IaRealSuiteIT` no perfil `ia-real` em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/iareal/IaRealSuiteIT.java` (processa as gravações de `assets/`, mede o tempo até a conferência (SC-001), alteração e resposta por áudio (SC-013) e o semestral com 30 aulas (SC-010), registra o custo e escreve `backend/aplicacao/target/ia-real/relatorio.md`), fora da cobertura
- [X] T183 Criar `infra/scripts/implantar.sh` (`git pull`, `docker compose -f infra/compose.yaml -f infra/compose.prod.yaml build` e `up -d`) e `.github/workflows/implantar.yml` (disparo manual com ambiente protegido por aprovação, SSH na VPS Hostinger)
- [X] T184 Escrever `infra/README.md` (subir local, carga, testes, `logOut` do bot, implantação na VPS, DNS, `.env` de produção, conferência com `ss -tlnp` de que só 80 e 443 estão abertas, foto do bot com o monograma FB e comandos pelo @BotFather; FR-051)
- [ ] T185 Revisar lado a lado as telas 01 a 12 com `Portal do Professor.dc.html` e os PDFs com `Modelo PDF.dc.html`, corrigir as divergências e registrar o resultado em `specs/001-fluxo-relatorios/checklists/revisao-visual.md` (Princípio IX)
- [ ] T186 Revisar o tom e a acentuação dos textos em `backend/aplicacao/src/main/resources/mensagens/mensagens_pt_BR.properties` sem mudar os códigos e refletir as mudanças em `specs/001-fluxo-relatorios/contracts/mensagens.md`
- [ ] T187 Conferir a cobertura do JaCoCo (≥ 90% de linhas e branches no `nucleo`), completar os testes que faltarem em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/` e deixar Spotless e ESLint sem apontamentos
- [ ] T188 Fazer a revisão de segurança e privacidade das mudanças (autenticação, minimização de dados enviados à IA, cabeçalhos do `infra/caddy/Caddyfile`, segredos fora das imagens) e registrar as conclusões em `specs/001-fluxo-relatorios/checklists/revisao-seguranca.md`
- [ ] T189 Executar os cenários V1 a V16 de `quickstart.md` uma vez em cada canal e registrar os resultados em `specs/001-fluxo-relatorios/checklists/validacao-quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup e **bloqueia todas as histórias**.
- **US2 (Phase 3)**: depende da Fundação. É pré-requisito prático de todas as outras histórias, que
  exigem professor acessado na Web e vinculado no Telegram.
- **US1 (Phase 4)**: depende de US2.
- **US3 (Phase 5)**: depende de US1 (retoma relatórios de aula).
- **US4 (Phase 6)**: depende de US1 (consolida aulas aprovadas); independe de US3.
- **US5 (Phase 7)**: depende de US1 (aprovados de aula); os itens semestrais do histórico dependem de
  US4. Sem US4, o histórico lista só aulas e a parte semestral fica para depois.
- **Polish (Phase 8)**: depende das histórias desejadas.

### User Story Dependencies

```text
Setup → Fundação → US2 → US1 ─┬─→ US3
                              ├─→ US4 ─→ (itens semestrais de US5)
                              └─→ US5
```

### Within Each User Story

- Testes primeiro, falhando antes da implementação.
- Domínio → portas e dublês → serviços → casos de uso → persistência e demais adaptadores de saída →
  adaptadores de entrada (Web e Telegram) → frontend → roteiro de paridade.
- Arquivos editados por várias tarefas (`CasosDeUsoConfiguracao.java`, `InterpretadorConversaTelegram.java`,
  `ApresentadorTelegram.java`, `ProcessadorTrabalhoServico.java`, `DocumentoControlador.java`) exigem
  execução sequencial dessas tarefas.

### Parallel Opportunities

- Setup: T002, T006–T009 e T011–T017 em paralelo depois de T001, T003 e T010 (quando aplicável).
- Fundação: T018–T023 (testes e bases) em paralelo; T024–T026 em paralelo; T033, T037, T041 e
  T043–T045 e T047 em paralelo com a persistência.
- Em cada história, todos os testes marcados [P] em paralelo; modelos de domínio [P] em paralelo; os
  trilhos **Web** (controladores + frontend) e **Telegram** (interpretador + apresentador) podem andar
  em paralelo depois dos casos de uso.
- Depois de US1, US3, US4 e US5 podem ser feitas em paralelo por pessoas diferentes (respeitando os
  arquivos compartilhados acima).

---

## Parallel Example: User Story 1

```bash
# Testes de US1 em paralelo:
Task: "RelatorioAulaTeste em backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/relatorio/RelatorioAulaTeste.java"
Task: "LlmOpenAiCompativelAdaptadorIT e TranscricaoOpenAiCompativelAdaptadorIT em backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/ia/"
Task: "FfmpegNormalizacaoAudioAdaptadorIT em backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/audio/"
Task: "RegistroAulaConversaIT em backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/"
Task: "GravadorAudio.test.tsx em frontend/tests/componentes/"

# Domínio de US1 em paralelo:
Task: "StatusRelatorio, EtapaRelatorio, ConteudoRelatorioAula e Aula em backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/relatorio/"
Task: "VersaoRelatorio, InteracaoRelatorio e DocumentoPdf em backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/relatorio/"
Task: "TrabalhoProcessamento e ChamadaIa em backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/trabalho/"

# Adaptadores independentes de US1 em paralelo:
Task: "Prompts e schema em backend/aplicacao/src/main/resources/prompts/ e ia/schemas/"
Task: "TranscricaoOpenAiCompativelAdaptador em backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/ia/"
Task: "FfmpegNormalizacaoAudioAdaptador em backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/audio/"
Task: "Template relatorio-aula.html em backend/aplicacao/src/main/resources/pdf/"
Task: "GravadorAudio.tsx em frontend/src/componentes/"
```

---

## Implementation Strategy

### MVP First (US2 + US1)

1. Phase 1: Setup.
2. Phase 2: Fundação (bloqueia tudo).
3. Phase 3: US2, acesso e início nos dois canais → validar V1, V2 e V15.
4. Phase 4: US1, registro até aprovação nos dois canais → validar V3, V4, V5, V12, V13 e V14.
5. **PARAR E VALIDAR**: o roteiro de paridade de US1 passa e o professor já registra e aprova aulas
   pelos dois canais. Commit e PR para a `main`, sem merge pelo autor (Princípio VIII).

### Incremental Delivery

1. Setup + Fundação → base pronta.
2. US2 → acesso nos dois canais (PR).
3. US1 → MVP (PR).
4. US3 → retomada entre canais (PR).
5. US4 → semestral (PR).
6. US5 → histórico e PDFs oficiais (PR).
7. Polish → acessibilidade, desempenho, implantação (PR).

Cada história chega completa nos **dois** canais: entregar só num canal é desvio da constituição.

### Parallel Team Strategy

1. Todos juntos em Setup, Fundação e US2.
2. Em US1: uma pessoa no núcleo e na persistência, outra nos adaptadores de IA, áudio e PDF; depois
   dos casos de uso, uma no canal Web e outra no canal Telegram.
3. Depois de US1: US3, US4 e US5 em paralelo.

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] liga a tarefa à história da spec, para rastreabilidade.
- Toda história termina com o roteiro de paridade (SC-006).
- Nenhum dado pessoal real entra no repositório: só `infra/carga/exemplo.json`, fictício.
- Os PDFs e as telas são revisados lado a lado com os protótipos antes do PR de cada história.
- Commit a cada tarefa ou grupo lógico; PR ao concluir cada história, sem merge pelo autor.
