# backend/aplicacao

Aplicação Spring Boot que **liga o `nucleo` ao mundo**: recebe requisições, implementa as portas e
sobe o serviço. Contém `Dockerfile`, `docker-entrypoint.sh` e `application.yaml`.

Pacotes em `src/main/java/br/com/felipeblaft/relatorios/`:

| Pacote | Conteúdo |
|---|---|
| `entrada/web` | API do Portal (`*Controlador`), com sessão e segurança web |
| `entrada/telegram` | Entrada do canal Telegram |
| `entrada/interno` | API interna consumida pelo n8n (transporte do Telegram), com segurança própria |
| `entrada/carga` | Carga inicial de cadastros |
| `saida/persistencia` | `*RepositorioAdaptador` (JPA/Flyway) |
| `saida/ia`, `saida/audio`, `saida/pdf`, `saida/armazenamento`, `saida/fila`, `saida/telegram`, `saida/seguranca` | `*Adaptador` das portas do núcleo |
| `configuracao` | Beans, propriedades e logging (`log/`) |

Recursos em `src/main/resources`: `db/migration` (Flyway `V<n>__*.sql`, nunca editar migração já
aplicada), `ia/schemas` (JSON Schema das saídas de IA) e `ia/simulado`.

Regras:

- Adaptador de entrada só traduz requisição → caso de uso; nenhuma regra de negócio aqui.
- Provedores de IA (transcrição, LLM) são trocáveis por configuração; o provedor real fica atrás da porta.
- Contratos: a API do Portal segue `specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml` e a API
  interna segue `specs/002-api-interna-canal-telegram/contracts/`; mudou a API, atualize o contrato e os testes de contrato.
- Testes especiais em `src/test`: `arquitetura`, `paridade` (Web ≡ Telegram), `privacidade` e `iareal`
  (chamadas reais, fora da cobertura unitária).
- Segredos e áudio nunca em log nem no código; áudio original não é retido após o processamento.
