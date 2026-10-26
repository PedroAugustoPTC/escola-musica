# Quickstart: validar a feature 005 de ponta a ponta

**Feature**: `005-admin-registrations-access` | Guia de **validação e execução**, não de implementação.
Detalhes de contrato: [contracts/](./contracts/); modelo: [data-model.md](./data-model.md).

Todos os comandos rodam em contêiner (Princípio de containerização); a máquina só precisa de Docker Engine 27+ e
Compose v2. Os cenários usam dados **fictícios** (CPFs de teste válidos, e-mails `@example.test`).

## 1. Pré-requisitos

- Constituição v2.2.0 na `main` (PR #28 mesclado) e a branch `spec/005-admin-registrations-access` atualizada.
- `infra/.env` gerado pelo `local.sh up` (chaves `CPF_ENCRYPTION_KEY` e `HMAC_SECRET` já existem desde a 001).
- Variáveis novas (nomes em `infra/.env.example`, **vazias** no repositório): `ADMIN_BOOTSTRAP_NAME`,
  `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`; opcionais com padrão: `ADMIN_SESSION_HOURS` (2),
  `ADMIN_PASSWORD_ARGON2_MEMORY_KIB` (19456), `ADMIN_PASSWORD_ARGON2_ITERATIONS` (2),
  `ACCESS_CPF_PROBE_MAX_REFUSALS` (5), `ACCESS_CPF_PROBE_WINDOW_MINUTES` (15), `ACCESS_CPF_PROBE_LOCK_MINUTES` (15),
  `TELEGRAM_STUDENT_DRAFT_MINUTES` (30).

## 2. Subir o ambiente local com o primeiro administrador

```bash
sh infra/scripts/local.sh up
```

No ambiente local, o `local.sh` preenche o administrador fictício (`admin@example.test`) com senha aleatória, impressa
**uma vez** no terminal. Esperado: o log (em português) informa "primeiro administrador criado" **sem** a senha; subir de
novo com outros valores no `.env` não cria nem altera nada.

## 3. Cenários de aceitação (mapa para as histórias e critérios)

| # | Cenário | Como validar | Esperado |
|---|---|---|---|
| 1 | Entrada do administrador (US1, SC-008) | `https://localhost/admin/sign-in`; senha certa, errada ×5, certa | sessão de 2 h; 6ª tentativa recusada com tempo restante; mensagem igual para e-mail inexistente/conta inativa |
| 2 | Senha provisória (US8) | entrar com a senha do `.env` | só a tela "Defina uma nova senha"; demais rotas 403 `PASSWORD_CHANGE_REQUIRED`; depois da troca entra; a provisória não vale |
| 3 | Sessão fixa | esperar/forçar `expires_at`; usar a sessão depois | volta a `/admin/sign-in`; nada executado; "Sair" invalida só aquela sessão |
| 4 | Professor (US2) | cadastrar, copiar o código (aparece uma vez), recarregar | código não reaparece; e-mail repetido recusado; lista nunca mostra código |
| 5 | Acesso do professor | usar o código na Web e no Telegram (simulador) | acessa; inativar o professor derruba Web e Telegram na hora; reativar emite código novo |
| 6 | Aluno pelo administrador (US3) | cadastrar adulto, menor sem/com responsável, CPF inválido, CPF repetido, 31/02 | recusas com mensagens por campo; duplicidade indica o **nome** do aluno, nunca o CPF |
| 7 | Aluno pelo professor (US4, SC-006) | professores A e B; A tenta abrir/editar/inativar/buscar aluno de B e cadastrar com CPF de B | tudo `404 RESOURCE_NOT_FOUND` idêntico ao inexistente; CPF alheio → mensagem neutra (sem nome/professor); 5 recusas em 15 min bloqueiam só operações com CPF |
| 8 | Aluno compartilhado | aluno com 2 professores ativos; um tenta inativar | `SHARED_STUDENT_ACTION_DENIED`; editar dados é permitido |
| 9 | Associações (US5, SC-013) | retirar associação com relatório em aberto | tela avisa quantos congelam e **não bloqueia**; professor perde o aluno e o relatório; reassociar devolve |
| 10 | Código e Telegram (US6, SC-010) | reemitir (com e sem "encerrar Telegram"); desvincular | código antigo morre na hora; sessões Web caem; vínculo cai só se marcado; rascunho e ações pendentes descartados |
| 11 | Administradores (US7, SC-009) | criar, ativar com o código, inativar o primeiro, tentar inativar o último, duas inativações simultâneas | nunca zero ativos; reativar exige novo código |
| 12 | Bot (US4, SC-002/003) | simulador: "cadastrar aluno" → nome → data → CPF → (responsável) → instrumento → resumo → Confirmar | resumo **sem CPF**; mensagem do CPF apagada (`DELETE_MESSAGE`); segunda confirmação responde "já concluída"; cancelar/vencer não grava |
| 13 | Bot sem administração (SC-003, SC-007) | enviar "criar professor", "reemitir código", e-mail+senha de administrador | negativa neutra + menu do professor; nenhuma menção a Web/administrador |
| 14 | Credencial cruzada (SC-005) | cookie `session` em `/api/v1/admin/**`; `admin_session` em `/api/v1/my-students` | `404` byte a byte igual ao de rota inexistente; sem credencial → `401` |
| 15 | Carga inicial (SC-011, FR-044) | `sh infra/scripts/local.sh seed` com `infra/seed/example.json` e `--simulate` | mesmas regras da Web; `--simulate` não grava; autoria com origem "carga inicial" |
| 16 | Privacidade (SC-004, SC-014) | rodar os fluxos com o CPF canário e abrir `authorship_record` | canário em lugar nenhum; uma linha de autoria por operação da lista do FR-040 |
| 17 | Telas (SC-012) | e2e com axe em 390 px e 1440 px, claro e escuro | sem violações sérias/críticas; navegação por teclado; estados vazio/erro/carregando |

## 4. Suítes automáticas

```bash
sh infra/scripts/local.sh tests backend     # núcleo (≥ 90%), adaptadores, contratos, ArchUnit, guardião de idioma, privacidade, paridade
sh infra/scripts/local.sh tests frontend    # Vitest + guardião de idioma do TypeScript
sh infra/scripts/local.sh tests e2e         # Playwright + axe (telas administrativas e Meus alunos)
sh infra/scripts/local.sh tests n8n         # ação DELETE_MESSAGE no transporte (workflows e simulador)
sh infra/scripts/local.sh tests all
```

Verificações dedicadas desta feature (todas parte de `tests backend`, salvo indicação):

- **Acesso cruzado gerado da matriz**: itera `contracts/permission-matrix.json` × (administrador Web, professor Web,
  professor Telegram, sem credencial) sobre **todas** as rotas e comandos; compara com a `PermissionMatrix` do núcleo.
- **Enumerações**: `Role.values().length == 2`; nenhuma rota/comando/tela para aluno ou responsável; nenhuma referência de
  `inbound.telegram` ao administrador (ArchUnit); `Actor.system` só em `inbound.seed`, `inbound.bootstrap` e `LoadInitialRegistryUseCase` (ArchUnit).
- **Indistinguibilidade**: corpo/cabeçalhos idênticos e mesma ordem de grandeza de tempo entre aluno alheio e inexistente.
- **Concorrência** (Postgres real): duplicidade de CPF/e-mail, último administrador, contagem de tentativas, confirmação do bot.
- **Append-only**: `UPDATE`/`DELETE`/`TRUNCATE` em `authorship_record` falham.
- **Contrato**: respostas validadas contra `api-administration.openapi.yaml`; nenhuma propriedade de resposta chama-se `cpf`.

Antes de abrir o PR de qualquer fatia: `GUARD_REPORT=1` no guardião de idioma para listar termos novos de uma vez
(`docker compose -f infra/compose.test.yaml run --rm -e GUARD_REPORT=1 tests-frontend`, e o equivalente do backend).

## 5. Procedimento de recuperação do acesso administrativo (FR-034b)

Somente se **nenhum** administrador consegue entrar. Passos (documentados em `infra/README.md`):

1. Parar o backend: `docker compose stop backend`.
2. No banco, `DELETE FROM administrator;` (as sessões saem por cascata; **`authorship_record` permanece**).
3. Preencher `ADMIN_BOOTSTRAP_*` no `infra/.env` e reiniciar o backend.
4. Entrar com a senha provisória e definir a senha nova (cenário 2); apagar a senha do `.env` depois.

Não existe variável, rota ou comando que redefina a senha de um administrador existente.

## 6. Rotação de chaves do CPF (FR-020)

Fora de uso normal; ver `contracts/initial-load.md` §5. Roteiro: cópia de segurança sem as chaves → janela de manutenção →
`rotate-cpf-key --simulate` → `rotate-cpf-key` → conferir contagens → reiniciar com as chaves novas. O comando nunca imprime CPF.
