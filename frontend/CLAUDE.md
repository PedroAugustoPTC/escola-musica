# frontend/

**Portal do Professor**: SPA em React 19 + TypeScript + Vite, com TanStack Query e React Router.
É um dos dois canais de entrada (o outro é o Telegram) e deve ter **paridade funcional** com ele.

| Pasta | Conteúdo |
|---|---|
| `src/app/` | Raiz da aplicação, rotas, guarda de sessão, tema |
| `src/paginas/` | Telas (`Tela*.tsx`), uma por fluxo |
| `src/componentes/` | Componentes reutilizáveis |
| `src/servicos/` | Clientes da API (`clienteApi.ts` e um módulo por contexto) |
| `src/estilos/` | `tokens.css` (identidade visual), tipografia, fontes |
| `src/mensagens/` | Textos da interface |
| `tests/` | Vitest (`componentes/`, `paginas/`) e Playwright + axe (`e2e/`) |
| `scripts/` | `gerar-mensagens.mjs` |

Regras:

- **Gerados e ignorados pelo Git**: `src/servicos/api-tipos.ts` (vem do OpenAPI em
  `specs/001-fluxo-relatorios/contracts/`) e `src/mensagens/mensagens.json` (vem do catálogo `backend/aplicacao/src/main/resources/mensagens/mensagens_pt_BR.properties`).
  Não edite; altere a fonte e rode `npm run gerar`.
- Sem regra de negócio no frontend: ela vive no backend.
- Identidade visual do Instituto (referência em `portal-do-professor-design-system/`), WCAG 2.1 AA e
  mobile-first são obrigatórios (Princípio IX); o e2e valida acessibilidade com axe.
- Nomes em português sem acentos (arquivos, componentes, funções).
- Comandos: `npm run dev`, `lint`, `typecheck`, `test`, `e2e`, `formatar`. Em CI e na validação final,
  rode via contêiner: `sh infra/scripts/local.sh testes frontend|e2e`.
