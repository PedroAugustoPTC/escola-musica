# Research: Administração — visão da escola (histórico de relatórios aprovados)

**Feature**: `006-admin-report-history` | **Data**: 2026-11-03 | **Spec**: [spec.md](./spec.md)

Cada decisão segue o formato Decisão / Motivo / Alternativas. Os nomes de código citados existem hoje na `main`
(commit `74f1e3c`), salvo quando marcados como **novos**.

## Ponto de partida (o que já existe)

| Peça | Onde | O que faz hoje |
|---|---|---|
| `QueryStudentHistoryUseCase` | `core/.../usecase/history/` | Histórico do professor (FR-047 da 001): junta `LessonReportRepository.listApproved` e `SemesterReportRepository.listApproved` de um par professor–aluno e ordena **em memória** com `NEWEST_FIRST` (referência desc., aprovação desc.). Sem paginação, sem versão aprovada no item. |
| `ReportAccessService`, `SemesterAccessService` | `core/.../service/` | "Só o dono": relatório de outro professor ou de aluno desassociado vira `RESOURCE_NOT_FOUND`. |
| `GetReportDocumentUseCase` + `GetSemesterReportUseCase` (`DocumentSource`) | `core/.../usecase/document/`, `.../semester/` | Entrega o PDF `REVIEW` de uma versão ou o `OFFICIAL` da versão aprovada, sempre depois da checagem de dono; nome sugerido `relatorio-aula-AAAA-MM-DD.pdf` / `relatorio-semestral-<período>.pdf`. |
| `DocumentController` | `application/.../inbound/web/` | Rotas do professor; `Cache-Control: no-store, private`; `Content-Disposition: inline`. |
| `PermissionMatrix`, `ActorAuthorizationService`, `Actor` | `core/.../domain/access/`, `.../service/` | Matriz da 005, negada por padrão; o papel sem a operação recebe `RESOURCE_NOT_FOUND`. `LESSON_AND_SEMESTER_REPORTS` é `DENY` para o administrador. |
| `AuthorshipService`, `AuthorshipRecorderPort`, `authorship_record` (V10) | `core/...`, `application/...` | Registro somente de acréscimo; sucesso na mesma transação, falha/negativa em transação independente; alvo único (`target_type`, `target_id`) com tipos `TEACHER`, `ADMINISTRATOR`, `STUDENT`, `ASSIGNMENT`. |
| Área administrativa Web | `inbound/web/admin/`, `AdminSessionFilter`, `CrossCredentialAnswerer`; `frontend/src/pages/admin/`, `services/admin/` | Sessão `admin_session` (`Path=/api/v1/admin`, 2 h fixas); credencial do outro papel recebe 404 e gera `AUTHORIZATION_DENIED`; listas paginadas por `PageRequest` (0-based, 20 padrão, 100 máx.) e `AdminPageResponse`. |
| Busca de alunos do administrador | `ListStudentsUseCase` + `GET /admin/students?search=&status=` | Busca por nome sem acento/caixa, filtro `ACTIVE`/`INACTIVE`/`ALL`, recusa termo com forma de CPF. |
| Abrir PDF na SPA | `frontend/src/services/pdf.ts` (`openPdf`) | Busca o PDF como `blob` e abre por URL de objeto; trata bloqueio de janela. |

## R-01 — Uma única consulta de aprovados para os dois papéis

**Decisão**: criar a porta **nova** `ApprovedReportQueryPort` (núcleo) com o critério `ApprovedReportCriteria`
(`teacherId?`, `studentId?`, `type?`, `from?`, `to?`) e dois métodos que compartilham a mesma consulta no adaptador:
`page(criteria, PageRequest) → PageResult<ApprovedReportRow>` e `all(criteria) → List<ApprovedReportRow>`. O adaptador
(`ApprovedReportQueryAdapter`, **novo**) faz um `UNION ALL` de `lesson_report ⨝ lesson` e `semester_report`, sempre com
`status = 'APPROVED'`, junta `teacher` e `student` para nome e situação, ordena e pagina no banco.

O `QueryStudentHistoryUseCase` do professor passa a usar `all(criteria(teacherId = ator, studentId))`, depois da
mesma checagem de aluno associado que já faz (`GetStudentSummaryUseCase`). O novo `QuerySchoolHistoryUseCase` usa
`page(...)` com os filtros livres do administrador. A regra "o que entra num histórico" (só aprovados, de quais tipos,
com qual data de referência e em qual ordem) fica **num lugar só**.

**Ordem**: o núcleo mantém a regra como `HistoryOrder.NEWEST_FIRST` (**novo** nome do comparador que hoje é
`QueryStudentHistoryUseCase.NEWEST_FIRST`): referência desc. (data da aula; início do período), aprovação desc. e,
**novo**, identificador do relatório desc. como desempate estável (FR-007). O adaptador traduz a mesma regra em
`ORDER BY reference_date DESC, approved_at DESC, report_id DESC`; um teste de adaptador com base aleatória (inclusive
empates) compara a ordem do SQL com o comparador do núcleo, e outro semeia relatórios em **todos** os estados e confere
que só os aprovados voltam (SC-002).

**Motivo**: o pedido manda reaproveitar o caso de uso de histórico "sem duplicar regra". Ordenar e paginar em memória
a escola inteira não escala ao volume do SC-008 e duplicaria a ordenação; um segundo caso de uso com consulta própria
duplicaria o "só aprovados". O desempate pelo id torna a paginação determinística.

**Alternativas**: (a) estender `listApproved` com mais parâmetros e paginar em memória — simples, mas carrega a escola
inteira a cada página; (b) visão materializada de aprovados — complexidade sem ganho no volume previsto (Princípio VII);
(c) manter o histórico do professor como está e escrever outra consulta para o administrador — duas fontes da mesma
regra, exatamente o que o pedido proíbe.

## R-02 — Quem pode ver o quê é decidido no núcleo

**Decisão**: duas operações **novas** na `Operation`/`PermissionMatrix` e em `contracts/permission-matrix.json` da 005:

| Operação | administratorWeb | teacherWeb | teacherTelegram | Rotas |
|---|---|---|---|---|
| `SCHOOL_HISTORY_QUERY` | `ALLOW` | `DENY` | `DENY` | `GET /admin/school-history` |
| `SCHOOL_REPORT_DOCUMENT_OPEN` | `ALLOW` | `DENY` | `DENY` | `GET /admin/school-history/{lesson,semester}-reports/{reportId}/pdf` |
| `SCHOOL_REPORT_DOCUMENT_DOWNLOAD` | `ALLOW` | `DENY` | `DENY` | `GET /admin/school-history/{lesson,semester}-reports/{reportId}/pdf/download` |

`LESSON_AND_SEMESTER_REPORTS` continua `DENY` para o administrador (Princípio III). Todo caso de uso novo começa por
`ActorAuthorizationService.require(actor, operation)`; o administrador com senha provisória recebe
`PASSWORD_CHANGE_REQUIRED`, como nas demais rotas. O escopo de leitura é derivado da permissão: `ALLOW` (escola
inteira) só existe para o administrador; o professor continua lendo pelo caminho da 001, que força `teacherId = ator`.
O teste de acesso cruzado gerado da matriz (SC-005 da 005) passa a cobrir as três operações.

**Motivo**: a matriz já é a fonte única de autorização (FR-048 da 005); abrir e baixar em operações separadas deixa o
registro de autoria com o nome exato da ação (R-04) sem coluna extra.

**Alternativas**: uma operação só para PDF com um parâmetro `disposition` — exigiria gravar o modo numa coluna nova e
validar o parâmetro; reaproveitar `LESSON_AND_SEMESTER_REPORTS` com `ALLOW` para o administrador — abriria as rotas de
escrita da 001.

## R-03 — PDF: a mesma leitura do documento oficial, outra checagem de acesso

**Decisão**: extrair de `GetReportDocumentUseCase` e de `GetSemesterReportUseCase` o serviço **novo**
`OfficialDocumentService.read(ReportType, reportId, approvedVersion)` (núcleo), que localiza a versão aprovada, o
`PdfDocument` `OFFICIAL` e os bytes, e monta o nome sugerido. O caminho do professor fica igual (checagem de dono e
associação, depois o serviço). O caso de uso **novo** `GetSchoolReportDocumentUseCase(actor, type, reportId,
disposition)`:

1. `require(actor, SCHOOL_REPORT_DOCUMENT_OPEN | _DOWNLOAD)`;
2. carrega o relatório pelo id (`searchById` dos repositórios existentes); ausente **ou** com estado diferente de
   `APPROVED` → `RESOURCE_NOT_FOUND` (uma consulta só nos dois casos, sem caminho mais curto para o inexistente, FR-018);
3. lê o documento oficial da `approvedVersion` pelo serviço comum; documento ausente ou ilegível → `INTERNAL_ERROR`
   com código de referência (caso de borda "PDF indisponível"), sem gerar nem trocar de versão;
4. grava o registro de autoria (R-04) e devolve os bytes.

Não existe rota administrativa por número de versão: o PDF de versão não aprovada é inalcançável por construção, e
qualquer caminho com versão em `/admin/**` cai no 404 de rota inexistente, com o mesmo corpo (FR-018). A resposta
reaproveita `DocumentController.pdf(...)` (no-store, privado), com `inline` para abrir e `attachment` para baixar, e o
mesmo nome sugerido do professor (FR-016).

**Motivo**: "a autorização de PDF por dono" é estendida sem copiar a leitura do documento: só a checagem de acesso muda
por papel, e ela fica no núcleo. O documento `OFFICIAL` já é o PDF sem a marca "em conferência" (FR-049 da 001).

**Alternativas**: um parâmetro `actor` dentro de `ReportAccessService` que libere tudo para o administrador — misturaria
a regra "só o dono" com "qualquer aprovado" num serviço usado também pelas operações de escrita da 001; URL assinada de
curta duração — dispensável, porque a sessão já é verificada em toda requisição e o endereço não deve valer fora dela
(FR-017).

## R-04 — Registro de autoria das leituras, compatível com a trilha da 007

**Decisão**:

- **Operações** registradas: `SCHOOL_HISTORY_QUERY`, `SCHOOL_REPORT_DOCUMENT_OPEN`, `SCHOOL_REPORT_DOCUMENT_DOWNLOAD`
  (os nomes da matriz, como as demais operações da 005).
- **Alvo**: na consulta, `STUDENT` + id quando há filtro de aluno; sem filtro de aluno, alvo nulo. No PDF, os tipos de
  alvo **novos** `LESSON_REPORT` e `SEMESTER_REPORT` + id do relatório pedido (mesmo que não exista: o alvo não tem
  chave estrangeira).
- **Detalhe da leitura**: migração **nova** `V16__authorship_read_detail.sql` acrescenta a `authorship_record` colunas
  tipadas e anuláveis — `filter_teacher_id uuid`, `filter_report_type varchar(16)` (`LESSON`/`SEMESTER`),
  `filter_period_start date`, `filter_period_end date`, `page_number integer`, `document_version integer` — e um CHECK
  que só as admite nas três operações acima. No núcleo, `AuthorshipRecord` ganha um componente opcional
  `AuthorshipReadDetail` (**novo**, só identificadores, enumerações, datas e inteiros); o CHECK de `target_type` passa a
  aceitar os dois tipos novos. Nenhum campo de texto livre é criado.
- **Atomicidade (FR-023)**: o caso de uso roda dentro do `TransactionPort` existente; o sucesso usa
  `AuthorshipRecorderPort.record` (mesma transação). Se a gravação falhar, a exceção sobe, nada é devolvido e o
  `ErrorHandlerController` responde `INTERNAL_ERROR` com código de referência. A resposta só é escrita depois que o
  caso de uso retorna, então não há entrega parcial.
- **Negativas (FR-021)**: PDF negado (inexistente, não aprovado) grava `FAILURE` com `RESOURCE_NOT_FOUND` em transação
  independente (`recordIndependent`), como as negativas da 005; credencial de outro papel já gera
  `AUTHORIZATION_DENIED` pelo `CrossCredentialAnswerer` e passa a valer para as rotas novas por estarem sob
  `/admin/**`. Expiração de sessão não gera registro (mesma regra da 005; a spec foi ajustada no FR-021).
- **Busca de aluno** pelo nome é a operação `STUDENT_LIST` da 005 e segue a regra dela (sem registro de leitura até a
  007).

**Motivo**: a 007 lê a trilha de `authorship_record`; manter as leituras na mesma tabela, com colunas tipadas, preserva
o formato (quem, papel, canal, quando, operação, alvo, resultado, correlação) e impede, pelo banco, que nome, CPF ou
conteúdo entrem por engano (SC-005).

**Alternativas**: (a) serializar os filtros em JSON numa coluna de texto — abre caminho para texto livre e quebra a
regra "só códigos" da V10; (b) tabela separada de leituras — a 007 teria de unir duas trilhas; (c) registrar só o PDF —
descartado no `/speckit-clarify` (D9).

## R-05 — Contrato da consulta administrativa

**Decisão**: contrato **próprio** da feature, `contracts/api-school-history.openapi.yaml`, no mesmo estilo e com o mesmo
`Error` do contrato da 005, e um terceiro gerador de tipos no frontend (`generate:school-history-api` →
`src/services/school-history-api-types.ts`, ignorado pelo Git), encadeado em `npm run generate`. Rotas sob `/admin/`
para herdar o cookie `admin_session` (`Path=/api/v1/admin`), a cadeia de segurança e o 404 de credencial cruzada.

- `GET /admin/school-history?studentId&teacherId&type&from&to&page&size` → `SchoolHistoryPage` (`items`, `page`,
  `size`, `total`, e `student` quando filtrado por aluno). Datas em `AAAA-MM-DD`; `from > to`, data inválida, `type`
  desconhecido, `page < 0` ou `size` fora de 1..100 → 400 `VALIDATION_FAILED` com `details.fields`.
- `GET /admin/school-history/lesson-reports/{reportId}/pdf` e `.../pdf/download` (idem `semester-reports`) →
  `application/pdf`.
- Todas as respostas: `Cache-Control: no-store, private`; 401 sem credencial; 404 `RESOURCE_NOT_FOUND` para credencial
  do outro papel, rota inexistente, relatório inexistente ou não aprovado — mesmo corpo.
- Filtro com `studentId`/`teacherId` inexistente → página vazia, `student` ausente (FR-014).

**Motivo**: specs/CLAUDE.md manda a mudança de API começar pelo contrato da feature; um arquivo próprio não reescreve o
contrato da 005 e segue o precedente da 005, que também repetiu o `Error` da 001.

**Alternativas**: acrescentar as rotas ao `api-administration.openapi.yaml` da 005 — um gerador a menos, mas a feature
alteraria o contrato de outra sem que a mudança fique visível na pasta dela.

## R-06 — Paginação

**Decisão**: paginação por deslocamento, igual às listas da 005: `PageRequest` (página a partir de 0, tamanho padrão
20, máximo 100), total por `COUNT(*)` do mesmo critério. A ordem total (com o id como desempate) garante páginas
estáveis enquanto nada novo é aprovado; quando algo é aprovado entre duas páginas, os itens podem se deslocar, como a
spec admite (Edge Cases), e cada página é recalculada com os filtros.

**Motivo**: o administrador navega por número de página e precisa do total (FR-013); o volume previsto não justifica
paginação por cursor.

**Alternativas**: cursor (keyset) pela chave de ordenação — sem total nem salto para página N, e mais complexo.

## R-07 — Desempenho (SC-008)

**Decisão**: a `V16` cria índices parciais `WHERE status = 'APPROVED'`: `lesson_report (student_id)`,
`semester_report (student_id)`, `semester_report (teacher_id)` (o par `teacher_id, student_id` aprovado já existe para a
aula, V8) e `semester_report (period_start)`; `lesson (lesson_date)` já tem índice composto por professor e aluno. A
meta (primeira página ≤ 2 s em 95% das consultas com 10.000 aprovados) é verificada por um teste de integração com
Postgres real e base semeada de 10.000 relatórios, sem filtro, só com professor e só com período (o pior caso).

**Motivo**: 10.000 linhas cabem com folga num `UNION ALL` ordenado; os índices cobrem os filtros mais comuns (aluno,
professor).

**Alternativas**: cache de consulta — dispensável e arriscado (dado de menor em memória compartilhada, Princípio IV).

## R-08 — Tela Web

**Decisão**: rota **nova** da SPA `/admin/history`, no `AdminLayout`, com item "Histórico" no `AdminNav` e atalho
"Ver histórico" na ficha do aluno (`StudentDetailScreen`), que abre `/admin/history?studentId=…`.

- **Endereço**: só identificadores e filtros (`studentId`, `teacherId`, `type`, `from`, `to`, `page`); nenhum nome
  (FR-015). Recarregar a página refaz a consulta (e o registro) a partir do endereço.
- **Escolha do aluno**: campo de busca que reaproveita `GET /admin/students?search=&status=ALL` (FR-010), com
  `looksLikeCpf`/`searchTermOf` da 005; cada resultado mostra nome, situação ("Inativo") e professores associados. O
  caminho principal tem 3 interações: abrir "Histórico", digitar e escolher o aluno (SC-001).
- **Filtros**: professor (lista de `GET /admin/teachers` com inativos, marcados), tipo (Aula / Semestral / Todos) e
  período (duas datas no formato do portal); "Limpar filtros". Erros de validação vêm do servidor em `details.fields`.
- **Lista**: mobile-first — cartões empilhados a 390 px, tabela a partir do ponto de quebra do design system; colunas
  Relatório ("Aula de dd/mm/aaaa" / "Semestral (dd/mm/aaaa a dd/mm/aaaa)"), Aluno (sem filtro de aluno), Professor
  (com "Inativo"), Aprovado em (dd/mm/aaaa hh:mm), Versão ("v3"), ações "Abrir PDF" e "Baixar PDF". Paginação com
  total ("1–20 de 134"). Estados: carregando, vazio (as duas mensagens do FR-027), falha com código de referência,
  sessão expirada (o `AdminSessionGuard` existente).
- **PDF**: "Abrir" usa `openPdf` (blob + URL de objeto, já permitido pela CSP) com a rota `/pdf`; "Baixar" busca a
  rota `/pdf/download` e salva pelo nome sugerido. Os dois mostram estado de carregamento por item.
- **Acessibilidade e identidade**: tokens do design system, tabela com cabeçalhos e legenda, rótulos visíveis nos
  filtros, foco gerenciado ao trocar de página, anúncio do total em região viva, alvos de toque ≥ 44 px; e2e com axe em
  claro e escuro, 390 e 1440 px (SC-009), na régua da área administrativa (sem violação séria ou crítica).

Detalhe em [contracts/portal-screens.md](./contracts/portal-screens.md).

**Alternativas**: tela por aluno (`/admin/students/{id}/history`) sem lista da escola — não atende ao filtro só por
professor ou período (FR-011).

## R-09 — Telegram: nenhum fluxo novo

**Decisão**: o pedido do `/speckit-plan` cita "o fluxo do bot com botões", mas o `/speckit-clarify` fechou a feature
como **somente Web** (Princípio I). Não há fluxo, comando, botão nem texto novo no bot. Garantias: (a) teste ArchUnit
**novo**: nenhuma classe de `inbound.telegram` depende de `QuerySchoolHistoryUseCase` ou de
`GetSchoolReportDocumentUseCase`; (b) o teste de acesso cruzado gerado da matriz cobre `teacherTelegram = DENY` nas três
operações; (c) os testes existentes do histórico do professor no bot (`TelegramHistoryFlow`) continuam verdes (SC-006,
SC-007).

**Alternativas**: paginação por botões no bot para o administrador — exigiria emenda da constituição e login do
administrador no Telegram; descartado pelo dono do produto.

## R-10 — Ajustes na spec feitos no plano

- **FR-015**: a busca de aluno pelo nome é a da 005 (termo em `?search=`, nunca com forma de CPF), como já faz a área de
  alunos; só os filtros da lista ficam restritos a identificadores. A versão anterior proibia o nome em qualquer
  parâmetro e contrariava a busca da 005 que esta feature reaproveita.
- **FR-021**: retirada a negativa "sem sessão válida que identifique um administrador": a 005 decidiu que expiração de
  sessão não gera registro, e uma sessão inválida não identifica ator.

## R-11 — Emendas às specs anteriores (no PR de implementação)

No formato `> **Nota (feature 006)**` de specs/CLAUDE.md: spec 001, FR-048 (o PDF oficial também é entregue ao
administrador, pela rota administrativa); spec 005, linha "Relatórios de aula e semestrais" da Matriz de permissões e a
premissa sobre aluno inativo no histórico; `contracts/permission-matrix.json` da 005 ganha as três operações (a classe
`PermissionMatrix` e o teste de deriva acompanham).
