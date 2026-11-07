# frontend/

**Portal do Professor**: SPA em React 19 + TypeScript + Vite, com TanStack Query e React Router.
É um dos dois canais de entrada do professor (o outro é o Telegram) e deve ter **paridade funcional** com ele.
A área do **administrador** existe **somente na Web** (constituição 2.2.0, Princípio I): não tem equivalente no Telegram.

| Pasta              | Conteúdo                                                        |
| ------------------ | --------------------------------------------------------------- |
| `src/app/`         | Raiz da aplicação, rotas, guardas de sessão (`SessionGuard`, `AdminSessionGuard`), `AdminLayout`, tema |
| `src/pages/`       | Telas (`*Screen.tsx`), uma por fluxo; `pages/admin/` é a área administrativa e `My*Screen` é "Meus alunos" |
| `src/components/`  | Componentes reutilizáveis; `components/admin/` (moldura e peças administrativas) e `components/student/` (formulário e lista de alunos, compartilhados pelo administrador e pelo professor) |
| `src/services/`    | Clientes da API (`apiClient.ts` e um módulo por contexto); `services/admin/` para `/admin/**` |
| `src/styles/`      | `tokens.css` (identidade visual), tipografia, fontes, `admin.css`, `student-registry.css` |
| `src/messages/`    | Textos da interface                                             |
| `public/brand/`    | Imagens da marca (`fb-*`)                                       |
| `tests/`           | Vitest (`app/`, `components/`, `pages/`, `services/`) e Playwright + axe (`e2e/`) |
| `scripts/`         | `generate-messages.mjs`                                         |

Rotas da SPA em inglês: `/sign-in`, `/students…`, `/my-students…`, `/jobs/:jobId`, `/lesson-reports/:reportId…`,
`/semester-reports/:reportId…` e, da área administrativa, `/admin/sign-in`, `/admin/activate`, `/admin` (início),
`/admin/change-password`, `/admin/administrators`, `/admin/teachers…` e `/admin/students…`.

## Áreas da feature 005

- **`admin`** (somente Web): `adminRoutes.tsx`. Entrada e ativação são públicas; o restante fica atrás do `AdminSessionGuard` e do
  `AdminLayout`. O guarda é **independente** do `SessionGuard` do professor: escuta só os eventos do administrador
  (`ADMIN_SESSION_EXPIRED_EVENT`, `ADMIN_PASSWORD_CHANGE_REQUIRED_EVENT`), limpa só o cache dele e leva só à entrada dele, guardando
  o destino. Enquanto a senha for provisória (`mustChangePassword`), toda rota administrativa leva a `/admin/change-password`. Quem
  chega com outra credencial recebe "Página não encontrada" (a área não é revelada). Sessão fixa de 2 h, sem renovação: o relógio
  (`SessionClock`) só mostra o que o servidor informa; as mensagens de bloqueio saem de `blockedRemainingSeconds`
  (`useBlockedCountdown`).
- **`my-students`** ("Meus alunos", professor): `myStudentsRoutes.tsx` atrás do `SessionGuard`; usa o mesmo `StudentForm` do
  administrador e as mesmas regras de privacidade do CPF.
- **`OneTimeSecretPanel`** (`components/admin/`): exibe uma única vez o código de acesso do professor ou o código de ativação do
  administrador. O valor vive **só em `useState`**: nunca em cache de consulta, `localStorage`, URL nem título da página; "Já anotei
  o código" o apaga da memória; enquanto está na tela, sair (rota ou recarga) pede confirmação.

## Regras

- **Gerados e ignorados pelo Git**: `src/services/api-types.ts` (vem do OpenAPI em
  `specs/001-fluxo-relatorios/contracts/`), `src/services/administration-api-types.ts` (vem do segundo OpenAPI, em
  `specs/005-admin-registrations-access/contracts/api-administration.openapi.yaml`, pelo `generate:administration-api`) e
  `src/messages/messages.json` (vem do catálogo `backend/application/src/main/resources/messages/messages_pt_BR.properties`).
  `src/services/school-history-api-types.ts` (terceiro OpenAPI, em
  `specs/006-admin-report-history/contracts/api-school-history.openapi.yaml`, pelo `generate:school-history-api`; usado só pela
  tela `/admin/history`, histórico da escola).
  Não edite; altere a fonte e rode `npm run generate` (ou `generate:api` / `generate:administration-api` / `generate:school-history-api` / `generate:messages`).
  Os geradores de tipos são independentes: a área do professor lê `api-types.ts`; a administrativa e "Meus alunos", o segundo; o histórico da escola, o terceiro.
- Sem regra de negócio no frontend: ela vive no backend. As conferências de formulário são só de forma (campo vazio, confirmação
  igual); CPF, data, responsável de menor, unicidade e tamanho da senha vêm do servidor em `details.fields`.
- **Privacidade do CPF** (Princípios III e IV): o CPF só trafega no corpo do cadastro e da edição (`POST`/`PATCH`), nunca volta da
  API, nunca vai a URL, chave de consulta, cache, `localStorage`, título nem log; em branco na edição mantém o atual. Não existe
  busca por CPF: o termo com forma de CPF é recusado **antes** de chegar à URL, à API ou à chave de consulta
  (`looksLikeCpf` / `searchTermOf` em `components/student/cpfShape.ts`; `useSearchParamTerm` lê `?search=` da URL e, se o valor
  tiver forma de CPF, limpa a URL com `replace`, devolve termo vazio e mostra o aviso de recusa). Defesa em profundidade nos
  serviços (`tests/services/cpfSearchGuard.test.tsx`). O campo do CPF usa `autocomplete="off"`.
- Identidade visual do Instituto (referência em `portal-do-professor-design-system/`), WCAG 2.1 AA e
  mobile-first são obrigatórios (Princípio IX); o e2e valida acessibilidade com axe, em claro e escuro, a 390 e 1440 px.
  **Régua do axe**: a área administrativa e "Meus alunos" reprovam só impacto `serious`/`critical` (SC-012 diz "sem violação
  séria ou crítica"; `moderate`/`minor` são boas práticas, não critério WCAG e não derrubam o build); `accessibility.spec.ts`
  (telas do professor, SC-011) reprova qualquer violação. `tests/e2e/icons-render.spec.ts` prova no navegador que cada nome de
  `included-icons.txt` tem glifo na fonte (largura de 1em). Orçamentos de tempo (SC-001/SC-002) usam `test.setTimeout` maior que
  o orçamento, senão o timeout padrão de 90 s mataria o teste antes da asserção de 180 s. Datas de "menor de idade" nos testes
  vêm de `minorBirthDate()` (relativas a hoje), nunca fixas. O login
  do administrador de teste usa `E2E_ADMIN_EMAIL` e `E2E_ADMIN_PASSWORD` (`tests/e2e/support.ts`); a senha nunca fica no código.
- Idioma (Princípio VI): nomes de arquivos, componentes, funções, rotas, parâmetros de consulta e classes CSS em
  **inglês**; texto mostrado ao usuário em **português do Brasil acentuado**. Nome de campo do JSON da API
  (por exemplo as chaves do relatório semestral) é contrato do backend, não renomeie. O guardião de idioma
  (`tests/language-guard.test.ts`, ver `.specify/CLAUDE.md`) reprova palavra em português em identificador,
  classe CSS e nome de arquivo.
- Telas administrativas da feature 007 (rotas em `app/adminRoutes.tsx`): trilha (`/admin/audit`), consentimentos pendentes e termos (`/admin/consents/pending`, `/admin/consent-terms`), eliminação (`/admin/students/:studentId/erasure`, `/admin/erasure-requests`, comprovante), custos e alertas (`/admin/operations/costs`, `/admin/operations/alerts`) e o indicador de alertas na navegação (`AlertIndicator`). Só chamam a API; nenhuma regra de retenção, consentimento ou custo mora aqui.
- Comandos: `npm run dev`, `lint`, `typecheck`, `test`, `e2e`, `format`. Em CI e na validação final,
  rode via contêiner: `sh infra/scripts/local.sh tests frontend|e2e`.
