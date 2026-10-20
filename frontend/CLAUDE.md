# frontend/

**Portal do Professor**: SPA em React 19 + TypeScript + Vite, com TanStack Query e React Router.
É um dos dois canais de entrada do professor (o outro é o Telegram) e deve ter **paridade funcional** com ele.
A área do **administrador** existe **somente na Web** (constituição 2.2.0, Princípio I): não tem equivalente no Telegram.

| Pasta              | Conteúdo                                                        |
| ------------------ | --------------------------------------------------------------- |
| `src/app/`         | Raiz da aplicação, rotas, guarda de sessão, tema                |
| `src/pages/`       | Telas (`*Screen.tsx`), uma por fluxo                            |
| `src/components/`  | Componentes reutilizáveis                                       |
| `src/services/`    | Clientes da API (`apiClient.ts` e um módulo por contexto)       |
| `src/styles/`      | `tokens.css` (identidade visual), tipografia, fontes            |
| `src/messages/`    | Textos da interface                                             |
| `public/brand/`    | Imagens da marca (`fb-*`)                                       |
| `tests/`           | Vitest (`components/`, `pages/`) e Playwright + axe (`e2e/`)    |
| `scripts/`         | `generate-messages.mjs`                                         |

Rotas da SPA em inglês: `/sign-in`, `/students…`, `/jobs/:jobId`, `/lesson-reports/:reportId…`,
`/semester-reports/:reportId…`.

Regras:

- **Gerados e ignorados pelo Git**: `src/services/api-types.ts` (vem do OpenAPI em
  `specs/001-fluxo-relatorios/contracts/`) e `src/messages/messages.json` (vem do catálogo
  `backend/application/src/main/resources/messages/messages_pt_BR.properties`).
  Não edite; altere a fonte e rode `npm run generate` (ou `generate:api` / `generate:messages`).
- Sem regra de negócio no frontend: ela vive no backend.
- Identidade visual do Instituto (referência em `portal-do-professor-design-system/`), WCAG 2.1 AA e
  mobile-first são obrigatórios (Princípio IX); o e2e valida acessibilidade com axe.
- Idioma (Princípio VI): nomes de arquivos, componentes, funções, rotas, parâmetros de consulta e classes CSS em
  **inglês**; texto mostrado ao usuário em **português do Brasil acentuado**. Nome de campo do JSON da API
  (por exemplo as chaves do relatório semestral) é contrato do backend, não renomeie. O guardião de idioma
  (`tests/language-guard.test.ts`, ver `.specify/CLAUDE.md`) reprova palavra em português em identificador,
  classe CSS e nome de arquivo.
- Comandos: `npm run dev`, `lint`, `typecheck`, `test`, `e2e`, `format`. Em CI e na validação final,
  rode via contêiner: `sh infra/scripts/local.sh tests frontend|e2e`.
