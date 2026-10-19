# backend/application

Aplicação Spring Boot que **liga o `core` ao mundo**: recebe requisições, implementa as portas e
sobe o serviço. Contém `Dockerfile`, `docker-entrypoint.sh` e `application.yaml`.

Pacotes em `src/main/java/br/com/felipeblaft/reports/`:

| Pacote | Conteúdo |
|---|---|
| `inbound/web` | API do Portal (`*Controller`), com sessão e segurança web |
| `inbound/telegram` | Conversa do canal Telegram (interpretador e fluxos); a entrada é a rota interna do n8n em `inbound/telegram/channel` |
| `inbound/internal` | API interna consumida pelo n8n (transporte do Telegram), com segurança própria |
| `inbound/seed` | Comandos operacionais: `seed` (carga inicial), `issue-code` (reemite o código de acesso) e `demo-data` (massa de demonstração, só local) |
| `outbound/persistence` | `*RepositoryAdapter` (JPA/Flyway) |
| `outbound/ai`, `outbound/audio`, `outbound/pdf`, `outbound/storage`, `outbound/queue`, `outbound/telegram`, `outbound/security` | `*Adapter` das portas do `core` |
| `config` | Beans, propriedades (`reports.*`) e logging (`log/`) |

Recursos em `src/main/resources`: `db/migration` (Flyway `V<n>__*.sql`; **`V1` a `V8` são imutáveis**, nunca edite
migração já aplicada), `ai/schemas` (JSON Schema das saídas de IA), `ai/simulated` (respostas do provedor simulado),
`messages` (catálogo `messages_pt_BR.properties`, texto ao usuário), `prompts` e `pdf` (modelos HTML, estilos, fontes e imagens).

Regras:

- Nomes seguem o Princípio VI: identificadores em inglês, sufixos `*Controller`, `*Adapter`, `*Mapper`, `*Request`,
  `*Response`. O guardião de idioma (ver `.specify/CLAUDE.md`) reprova palavra em português em nome, arquivo, tabela,
  coluna, YAML, shell, JSON e `.properties`; texto mostrado ao usuário e log continuam em português.
- Adaptador de entrada só traduz requisição → caso de uso; nenhuma regra de negócio aqui.
- Comandos operacionais (`seed`, `issue-code`, `demo-data`) rodam no perfil `seed`, que o `docker-entrypoint.sh` ativa; o
  `SeedCommandsExecutor` só existe nesse perfil e fora dos testes (`seed & !test`). Mudar o nome do perfil exige mudar o
  entrypoint, as anotações `@Profile` e `SeedCommandsExecutorProfileTest` juntos.
- Provedores de IA (transcrição, LLM) são trocáveis por configuração; o provedor real fica atrás da porta.
- Contratos: a API do Portal segue `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml` e a API
  interna segue `specs/002-api-interna-canal-telegram/contracts/`; mudou a API, atualize o contrato e os testes de contrato.
  Nomes de campo, rota, código de erro e chave de documento são contrato: não mude sem mudar o contrato.
- Testes especiais em `src/test`: `architecture` (ArchUnit e guardião de idioma), `parity` (Web ≡ Telegram), `privacy` e
  `realai` (chamadas reais, fora da cobertura unitária).
- Telegram: o n8n é o **único** transporte (feature 004). O backend nunca recebe webhook direto do Telegram nem envia
  mensagem pela API de bots; as notificações proativas viram ações na outbox, entregues ao n8n. O backend só chama a
  API de bots para baixar o áudio (`getFile`, `TelegramFileAdapter`) e registrar os comandos (`setMyCommands`); o
  webhook é registrado por `infra/scripts/telegram-webhook.sh apply`. Nos testes, `TelegramConversationBase` usa a
  API interna com o executor em memória e entrega a outbox na publicação (`ProactiveOutboxDrain`).
- Segredos e áudio nunca em log nem no código; áudio original não é retido após o processamento.
