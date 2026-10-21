# Contrato: telas e rotas do portal — área administrativa e "Meus alunos"

**Feature**: `005-admin-registrations-access` | Referências: FR-043, FR-045, Princípio IX;
`specs/001-fluxo-relatorios/contracts/rotas-portal.md`; `portal-do-professor-design-system/project/` (referência visual:
`Design System.dc.html` para tokens e componentes, `Secretaria.dc.html` para a estrutura das telas administrativas,
`Portal do Professor.dc.html` e `Telas.dc.html` para o padrão das telas do professor).

O protótipo é **referência, não código**: reproduz-se o visual com os tokens de `frontend/src/styles/tokens.css`
(claro e escuro) e os componentes existentes (`Button`, `ConfirmationDialog`, `EmptyState`, `ErrorState`,
`LoadingState`, `Header`, `Layout`). Sem regra de negócio no frontend: validação de formulário é só de forma
(campo vazio, tamanho máximo); as regras (CPF, data, responsável de menor, unicidade) vêm do backend em
`details.fields`. Os textos vêm do catálogo gerado (`messages.json`), nunca literais nas telas.

## 1. Rotas da SPA (em inglês)

| Rota | Tela | Guarda | API |
|---|---|---|---|
| `/admin/sign-in` | A01 Entrar (e-mail e senha; bloqueio com contagem regressiva) | pública | `POST /admin/session` |
| `/admin/activate` | A02 Definir senha (e-mail + código de ativação + nova senha) | pública | `POST /admin/activation` |
| `/admin/change-password` | A03 Trocar a senha provisória | `AdminSessionGuard` (permite `mustChangePassword`) | `PUT /admin/password` |
| `/admin` | A04 Início (atalhos e contagens) | `AdminSessionGuard` | listas |
| `/admin/teachers` | A05 Professores (lista, busca, filtro) | idem | `GET /admin/teachers` |
| `/admin/teachers/new` | A06 Cadastrar professor → A07 código exibido uma vez | idem | `POST /admin/teachers` |
| `/admin/teachers/:teacherId` | A08 Professor (editar; inativar/reativar; reemitir código; desvincular Telegram) | idem | `GET/PATCH …`, ações |
| `/admin/administrators` | A09 Administradores (lista; criar → código de ativação; inativar/reativar) | idem | `/admin/administrators…` |
| `/admin/students` | A10 Alunos da escola (lista, busca, situação) | idem | `GET /admin/students` |
| `/admin/students/new` | A11 Cadastrar aluno (com professores) | idem | `POST /admin/students` |
| `/admin/students/:studentId` | A12 Aluno (editar; inativar/reativar; associações com aviso de congelados) | idem | `GET/PATCH …`, `PUT/DELETE …/teachers/…` |
| `/my-students` | P01 Meus alunos (lista, busca, situação) | `SessionGuard` (professor) | `GET /my-students` |
| `/my-students/new` | P02 Cadastrar aluno | idem | `POST /my-students` |
| `/my-students/:studentId` | P03 Aluno (editar; inativar/reativar) | idem | `GET/PATCH …` |

- A entrada do professor (`/sign-in`) ganha o link **"É administrador? Acesse a área administrativa."** (FR-043), para
  `/admin/sign-in`. O link da "secretaria" deixa de existir no código e nos textos.
- O dashboard do professor ganha o atalho **Meus alunos**; a tela existente `/students` (escolha de aluno para aula) continua e mostra
  apenas alunos ativos. Rota desconhecida em `/admin/**` sem sessão → `/admin/sign-in`; com sessão de professor →
  tela "Página não encontrada" (nunca revela a área).
- `AdminSessionGuard` e `SessionGuard` são independentes: cada um escuta o seu evento de sessão expirada
  (`ADMIN_SESSION_EXPIRED_EVENT`, `SESSION_EXPIRED_EVENT`), limpa só o seu cache e leva só à sua entrada. As duas sessões
  podem coexistir no mesmo navegador (cookies `admin_session` com `Path=/api/v1/admin` e `session`).

## 2. Layout e identidade

- **Mobile-first, a partir de 390 px** e funcional até 1440 px. A área administrativa tem layout próprio (`AdminLayout`):
  cabeçalho com selo "Área administrativa" (distingue visualmente do portal do professor), navegação
  Professores · Alunos · Administradores (barra inferior em telas estreitas, lateral/topo em largas), alternância
  claro/escuro e **Sair**. Tabelas viram **cartões empilhados** abaixo de 640 px; ações por linha ficam num menu
  com alvo de toque ≥ 44 px.
- Tipografia e cores só pelos tokens (serifada em títulos, `--gd`/`--gs` para ênfase, `--err`/`--ok`/`--warn` para
  estados). Contraste ≥ 4,5:1 para texto e ≥ 3:1 para componentes e foco, **nos dois temas**; nenhuma informação só
  por cor (situação "Inativo" leva texto e ícone).
- **Estados obrigatórios** em toda lista e formulário: carregando (`LoadingState`), lista vazia (`EmptyState` com a
  ação que resolve), busca sem resultado, falha (`ErrorState` com "Tentar de novo"), sucesso (aviso `role="status"`).
  Ação que falha não avança a tela.

## 3. Telas administrativas

- **A01 Entrar**: campos e-mail (`autocomplete="username"`) e senha (`autocomplete="current-password"`), botão
  "Entrar"; mensagem única para qualquer falha; bloqueio mostra contagem regressiva em `aria-live="polite"` e
  desabilita o envio; link "Tenho um código de ativação".
- **A02 Definir senha**: e-mail, código de ativação (campo de texto em maiúsculas, `autocomplete="off"`), nova senha e
  confirmação (`autocomplete="new-password"`), regra "12 a 128 caracteres" visível antes do envio; sem medidor de força.
- **A03 Trocar senha provisória**: só a nova senha (e confirmação); enquanto `mustChangePassword`, qualquer outra rota
  administrativa redireciona para cá.
- **Sessão fixa de 2 h**: a barra do `AdminLayout` mostra "Sua sessão expira às HH:MM"; 5 minutos antes vira aviso
  (`role="alert"` uma vez) com a orientação de concluir e entrar de novo. Ao expirar: volta a `/admin/sign-in` guardando
  o destino; **dados digitados não são mantidos**. Limite de tempo é essencial à segurança (WCAG 2.2.1, exceção de
  tempo essencial), mitigado pelo aviso prévio.
- **Código exibido uma única vez** (A07, reemissão, reativação, criação de administrador): componente `OneTimeSecretPanel`
  com o código em fonte monoespaçada grande, "Copiar" (com anúncio `role="status"`), o aviso `ADMIN_SECRET_SHOWN_ONCE`,
  e o botão "Já anotei o código", que **limpa o valor da memória** e libera a navegação. O valor vive só em estado do
  componente (`useState`), nunca em cache do TanStack Query (`gcTime: 0` na mutação), `localStorage`, URL ou título da
  página; impressão e captura de tela são responsabilidade de quem opera (risco aceito, FR-011).
- **Confirmações** (`ConfirmationDialog`, foco preso, `Esc` cancela, foco volta ao disparador): inativar professor,
  reemitir código (com a caixa "Também encerrar o vínculo do Telegram"), desvincular Telegram, retirar associação
  (mostra `frozenReports` vindo de `removal-preview`; **não bloqueia**), inativar administrador. Cancelar/fechar não grava.
- **A05/A10 listas**: busca com `<form role="search">`, filtro de situação em controle segmentado, paginação com
  "Anterior/Próxima" e total; a linha de professor mostra nome, e-mail, situação e "Telegram vinculado" (ícone + texto);
  nunca mostra código. A linha de aluno mostra nome, idade, instrumento, situação e professores associados.
- **A11/A12 formulário de aluno**: seções "Aluno" (nome, data de nascimento com `<input type="date">`, CPF com
  `inputmode="numeric"`, `autocomplete="off"`, `autocapitalize="off"`, `spellcheck="false"`; **sem máscara que vaze para
  URL**), "Responsável" (nome, telefone `type="tel"`, e-mail) e "Professores" (multisseleção de professores ativos).
  Na edição, o CPF vem **em branco** com a dica `MY_STUDENTS_CPF_KEEP`. Erros por campo vêm de `details.fields`, ligados
  por `aria-describedby`, e um resumo de erros no topo recebe o foco no envio.
- Nenhuma tela contém botão, menu ou aviso relacionado a aluno ou responsável como usuários (SC-007).

## 4. Telas do professor — "Meus alunos"

- **P01**: lista paginada dos alunos do professor com busca (nome ou instrumento), filtro Ativos/Inativos/Todos e o botão
  "Cadastrar aluno"; vazio → `MY_STUDENTS_EMPTY` com o botão primário "Cadastrar o primeiro aluno" (FR-042).
- **P02/P03**: o mesmo formulário de A11/A12 **sem** a seção de professores (o aluno nasce associado ao professor da
  sessão). A seção "Responsável" fica sempre visível, com o rótulo `MY_STUDENTS_GUARDIAN_SECTION`; a obrigatoriedade para
  menores é decidida e explicada pelo servidor.
- CPF recusado por ser de aluno alheio mostra `CPF_UNAVAILABLE` no campo, sem nome nem professor; o bloqueio contra
  sondagem (`OPERATION_TEMPORARILY_BLOCKED`) desabilita só o envio do formulário e mostra o tempo restante, mantendo o
  restante do portal utilizável.
- Inativar/reativar: confirmação para inativar (`MY_STUDENTS_CONFIRM_INACTIVATE`); aluno com mais de um professor ativo
  tem os botões desabilitados com o texto de orientação (`canInactivate = false`) e, se mesmo assim a chamada ocorrer,
  mostra `SHARED_STUDENT_ACTION_DENIED`.

## 5. Acessibilidade e privacidade verificadas

- Axe (Playwright) sem violações sérias ou críticas em todas as telas, nos dois temas, em 390 px e 1440 px (SC-012);
  navegação só por teclado; ordem de foco lógica; `lang="pt-BR"`; rótulos explícitos; mensagens de erro anunciadas.
- CPF, senha e códigos: nunca em `localStorage`/`sessionStorage`, URL, `title`, chave de consulta do TanStack Query ou
  console; `Cache-Control: no-store` nas respostas; teste de e2e confere que a URL final e o armazenamento do navegador
  não contêm o CPF digitado.
