# Contrato: tela "Histórico" da área administrativa

Complementa [`005-admin-registrations-access/contracts/portal-screens.md`](../../005-admin-registrations-access/contracts/portal-screens.md),
cujas regras de layout, identidade, estados e guardas valem aqui sem repetição. Somente Web; nenhuma tela, comando ou
botão no Telegram.

## 1. Rotas da SPA

| Rota | Tela | Guarda | API |
|---|---|---|---|
| `/admin/history` | A13 Histórico da escola (busca de aluno, filtros, lista paginada, PDF) | `AdminSessionGuard` | `GET /admin/school-history`, `GET /admin/students?search=&status=ALL`, `GET /admin/teachers?…`, rotas de PDF |

- **Parâmetros da rota** (só identificadores e filtros, FR-015): `studentId`, `teacherId`, `type` (`LESSON`/`SEMESTER`),
  `from`, `to` (`AAAA-MM-DD`), `page`. O termo de busca de aluno segue a regra da 005 (`?search=`, nunca com forma de
  CPF, `useSearchParamTerm`). Mudar filtro ou página atualiza a rota com `replace`; "Voltar" do navegador não refaz a
  busca de aluno.
- **Entradas**: item **Histórico** no `AdminNav` (depois de Alunos); ação **Ver histórico** na A12 (ficha do aluno),
  que abre `/admin/history?studentId=…`; o início (A04) ganha o atalho "Histórico".
- Sessão de professor em `/admin/history` → "Página não encontrada" (regra da 005).

## 2. A13 — composição

1. **Título** "Histórico" e subtítulo "Relatórios aprovados da escola".
2. **Aluno**: campo de busca (combobox acessível, `role="combobox"`, lista em `role="listbox"`) com resultados da busca
   de alunos da 005 em `status=ALL`; cada opção mostra nome, "Inativo" (texto + ícone) quando for o caso e os
   professores associados. Escolher fixa o filtro e mostra o cabeçalho do aluno (nome + situação) com o botão
   "Trocar aluno". Sem aluno escolhido, a lista é da escola inteira.
3. **Filtros** (recolhíveis abaixo de 640 px, com contador de filtros ativos): Professor (lista com ativos e inativos,
   inativos marcados), Tipo (Todos · Aula · Semestral), Período (De / Até, formato do portal). Botões "Aplicar" e
   "Limpar filtros". Erros vêm do servidor em `details.fields` e aparecem junto do campo (`aria-describedby`).
4. **Lista**: total anunciado em região viva ("134 relatórios aprovados"); abaixo de 640 px, cartões; acima, tabela com
   `<caption>` e cabeçalhos de coluna:

   | Coluna | Conteúdo |
   |---|---|
   | Relatório | "Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)" (rótulo vindo da API) |
   | Aluno | nome (+ "Inativo"); omitida quando há filtro de aluno |
   | Professor | nome (+ "Inativo") |
   | Aprovado em | dd/mm/aaaa hh:mm |
   | Versão | "v3" (`aria-label` "versão 3") |
   | PDF | "Abrir PDF" e "Baixar PDF", com o rótulo do relatório no nome acessível |

5. **Paginação**: "Anterior" / "Próxima" e "1–20 de 134"; ao trocar de página, o foco vai para o início da lista.
6. **Estados**:
   - carregando: `LoadingState`;
   - aluno sem aprovados: `EmptyState` "Nenhum relatório aprovado encontrado para este aluno.";
   - filtros sem resultado: `EmptyState` "Nenhum relatório aprovado encontrado para estes filtros." + "Limpar filtros";
   - busca de aluno sem resultado: mensagem da 005;
   - falha: `ErrorState` com "Tentar de novo" e o código de referência;
   - sessão expirada: `AdminSessionGuard` (leva à entrada e volta ao mesmo endereço).
7. **PDF**: "Abrir PDF" busca a rota `/pdf` como arquivo e abre por URL de objeto (`openPdf`); se o navegador bloquear,
   avisa com a mensagem já existente. "Baixar PDF" busca `/pdf/download` e salva com o nome sugerido. Cada botão mostra
   carregamento próprio; falha mostra aviso `role="alert"` sem sair da lista.

Nenhuma ação de escrita existe na tela: não há botão de aprovar, alterar, cancelar, gerar ou enviar (FR-004).

## 3. Textos novos (catálogo `messages_pt_BR.properties`; chaves em inglês)

| Chave | Texto |
|---|---|
| `SCHOOL_HISTORY_TITLE` | Histórico |
| `SCHOOL_HISTORY_SUBTITLE` | Relatórios aprovados da escola |
| `SCHOOL_HISTORY_NAV` | Histórico |
| `SCHOOL_HISTORY_STUDENT_SHORTCUT` | Ver histórico |
| `SCHOOL_HISTORY_STUDENT_SEARCH` | Procurar aluno |
| `SCHOOL_HISTORY_CHANGE_STUDENT` | Trocar aluno |
| `SCHOOL_HISTORY_FILTER_TEACHER` | Professor |
| `SCHOOL_HISTORY_FILTER_TYPE` | Tipo |
| `SCHOOL_HISTORY_TYPE_ALL` | Todos |
| `SCHOOL_HISTORY_TYPE_LESSON` | Aula |
| `SCHOOL_HISTORY_TYPE_SEMESTER` | Semestral |
| `SCHOOL_HISTORY_FILTER_FROM` | De |
| `SCHOOL_HISTORY_FILTER_TO` | Até |
| `SCHOOL_HISTORY_APPLY` | Aplicar |
| `SCHOOL_HISTORY_CLEAR` | Limpar filtros |
| `SCHOOL_HISTORY_TOTAL` | {0} relatórios aprovados |
| `SCHOOL_HISTORY_EMPTY_STUDENT` | Nenhum relatório aprovado encontrado para este aluno. |
| `SCHOOL_HISTORY_EMPTY_FILTERS` | Nenhum relatório aprovado encontrado para estes filtros. |
| `SCHOOL_HISTORY_COLUMN_REPORT` / `_STUDENT` / `_TEACHER` / `_APPROVED_AT` / `_VERSION` | Relatório / Aluno / Professor / Aprovado em / Versão |
| `SCHOOL_HISTORY_OPEN_PDF` | Abrir PDF |
| `SCHOOL_HISTORY_DOWNLOAD_PDF` | Baixar PDF |
| `SCHOOL_HISTORY_PDF_FAILED` | Não foi possível obter o PDF. Tente de novo. |
| `INVALID_DATE` | Data inválida. Use o formato dd/mm/aaaa. |
| `PERIOD_START_AFTER_END` | A data inicial deve ser igual ou anterior à data final. |
| `INVALID_REPORT_TYPE` | Tipo de relatório inválido. |

## 4. Acessibilidade e identidade verificadas (SC-009)

e2e `admin-school-history.a11y.spec.ts` com axe, claro e escuro, 390 e 1440 px, na régua da área administrativa
(sem violação séria ou crítica); navegação só por teclado do combobox, dos filtros, da paginação e dos botões de PDF;
contraste das marcas "Inativo"; nada transmitido só por cor. e2e `admin-school-history.spec.ts` cobre as 3 interações
do SC-001 e o fluxo de abrir e baixar.
