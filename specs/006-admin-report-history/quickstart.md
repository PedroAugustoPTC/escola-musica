# Quickstart: validar a feature 006 de ponta a ponta

**Feature**: `006-admin-report-history` | Guia de **validação e execução**, não de implementação.
Contratos: [contracts/](./contracts/); modelo: [data-model.md](./data-model.md); decisões: [research.md](./research.md).

Tudo roda em contêiner; a máquina só precisa de Docker Engine 27+ e Compose v2. Os dados são **fictícios**.

## 1. Pré-requisitos

- Feature 005 mesclada na `main` (área administrativa, sessão do administrador, matriz e registro de autoria).
- Branch `feat/006-admin-report-history` atualizada; nenhuma variável de ambiente nova.
- Migração `V16__authorship_read_detail.sql` aplicada pelo Flyway na subida.

## 2. Subir o ambiente e preparar os dados

```bash
sh infra/scripts/local.sh up
```

```bash
sh infra/scripts/local.sh seed
```

Com o portal do professor e o bot (simulador) da 001, produza a base de validação, ou use a massa de dados do teste de
integração `SchoolHistoryScenario` (dois professores, A e B, e três alunos):

- aluno X com aulas aprovadas de A e de B, um semestral aprovado de A e um de B **no mesmo período**, um relatório
  aprovado na versão 3 e relatórios de X em **todos** os estados não aprovados (processando, falha, aguardando
  respostas, aguardando conferência, cancelado);
- aluno Y inativo, com aprovados de um professor depois inativado;
- aluno Z sem nenhum aprovado.

## 3. Cenários de aceitação

| # | Passos | Esperado | Cobre |
|---|---|---|---|
| Q1 | Entrar como administrador; **Histórico** → digitar "X" → escolher X | lista em 3 interações; todos os aprovados de A e B, do mais recente ao mais antigo; os dois semestrais do mesmo período como itens separados, cada um com o autor; "v3" no relatório aprovado na versão 3 | SC-001, FR-005 a FR-008 |
| Q2 | Conferir a lista de X contra a base | nenhum relatório não aprovado aparece, nem no total | SC-002 |
| Q3 | Procurar Y | aparece marcado "Inativo"; aprovados do professor inativo aparecem, com "Inativo" no autor | FR-009, FR-010 |
| Q4 | Escolher Z | "Nenhum relatório aprovado encontrado para este aluno." | FR-027 |
| Q5 | Sem aluno: filtrar professor A; depois Tipo "Semestral"; depois período; depois tudo junto | só os subconjuntos esperados, com o nome do aluno em cada item; paginação "1–20 de N" estável | FR-011 a FR-013 |
| Q6 | Período com "De" depois de "Até"; data inválida | erro no campo, nada consultado | FR-012 |
| Q7 | "Abrir PDF" e "Baixar PDF" de um relatório aprovado na v3 | PDF da v3, sem marca "em conferência", igual ao do professor; nome sugerido igual | FR-016, US2 |
| Q8 | Copiar a rota do PDF; abrir em janela anônima; abrir depois de "Sair" | nada entregue (401) | FR-017, SC-003 |
| Q9 | Com o cookie do professor autor, pedir as rotas `/admin/school-history/**` | 404 igual ao de rota inexistente | FR-003, SC-003 |
| Q10 | Como administrador, pedir PDF de relatório aguardando conferência, de id inexistente e com `/versions/1/pdf` | 404, corpos idênticos | FR-018, SC-003 |
| Q11 | Consultar o banco depois de Q1 a Q10 | um registro por página, filtro e PDF; negativas registradas; nenhum nome, CPF ou conteúdo | FR-020 a FR-022, SC-005 |
| Q12 | No portal do professor A e no bot, abrir o histórico de X | só os relatórios de A, como antes | FR-026, SC-006 |
| Q13 | No bot, pedir "histórico da escola" ou similar | negativa neutra | FR-001, SC-007 |

Para Q11, por exemplo:

```bash
docker compose -f infra/compose.yaml exec postgres psql -U reports -d reports -c "select operation, target_type, result, error_code, filter_report_type, page_number, document_version from authorship_record where operation like 'SCHOOL_%' order by occurred_at"
```

## 4. Suítes automatizadas

```bash
sh infra/scripts/local.sh tests backend
```

Núcleo (≥ 90% linha e branch): `QuerySchoolHistoryUseCase`, `GetSchoolReportDocumentUseCase`, `SchoolHistoryFilter`,
`HistoryOrder`, `AuthorshipReadDetail`, `OfficialDocumentService`, regressão de `QueryStudentHistoryUseCase`.
Adaptador e integração com Postgres real: só aprovados em todos os estados; ordem SQL ≡ `HistoryOrder` em base
aleatória com empates; filtros e período; V16 (CHECKs e somente acréscimo); atomicidade (falha do registro → 500 e nada
entregue); desempenho com 10.000 aprovados (SC-008). Contrato: respostas × `api-school-history.openapi.yaml`; matriz ×
`permission-matrix.json`; acesso cruzado gerado da matriz; ArchUnit (Telegram não depende dos casos de uso novos);
privacidade com valores canário; guardiões de idioma.

```bash
sh infra/scripts/local.sh tests frontend
```

Vitest da A13 (estados, filtros, paginação, PDF, rota só com identificadores) e do serviço `services/admin/schoolHistory.ts`.

```bash
sh infra/scripts/local.sh tests e2e
```

Playwright: `admin-school-history.spec.ts` (Q1, Q5, Q7 e as 3 interações) e `admin-school-history.a11y.spec.ts`
(axe, claro e escuro, 390 e 1440 px, SC-009); `history.spec.ts` existente continua verde (SC-006).

## 5. Pronto quando

Q1 a Q13 conferidos, as três suítes verdes com cobertura ≥ 90% no núcleo, e as notas de emenda nas specs 001 e 005 e
as três operações em `permission-matrix.json` incluídas no PR.
