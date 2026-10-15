# Contrato: Rotas e telas do Portal do Professor (Web)

**Feature**: `001-fluxo-relatorios` | Referências: FR-050 a FR-053; protótipos em
`portal-do-professor-design-system/project/Portal do Professor.dc.html` (telas 01 a 12 e variações
listadas em `Telas.dc.html`).

Cada rota corresponde a uma tela do protótipo. As variações de estado (vazio, carregando, erro,
modo escuro) são estados da mesma rota, não rotas novas. A API usada por cada tela está em
[api-portal.openapi.yaml](./api-portal.openapi.yaml).

| Rota | Tela (protótipo) | Estados/variações | API |
|---|---|---|---|
| `/sign-in` | 01 Entrar | 01b código inválido · 01c bloqueio (tempo restante) · 01d sem conexão | `POST /session` |
| `/` | 02 Painel | 02b vazio (`NO_STUDENTS`) · 02c menu (tema claro/escuro, encerrar sessão) · 02d modo escuro · relatórios em aberto com "Continuar" | `GET /home` |
| `/students?search=` | 03 Alunos | 03b busca sem resultado · 03c carregando · marca "Em aberto" | `GET /students` |
| `/students/:studentId/open-report` | 04 Relatório em aberto | 04b confirmação de descarte | `GET /students/{id}/open-report`, `POST …/cancellation` ou `POST /jobs/{id}/discard` |
| `/students/:studentId/new-lesson` | 05 Gravação | 05b gravando · 05c gravação concluída (ouvir, regravar, enviar) · 05d só envio de arquivo (`RECORDING_UNAVAILABLE`) · 05e modo escuro | `POST /students/{id}/lesson-reports` |
| `/jobs/:jobId` | 06 Processando | 06b enviando · 06c falha · aviso `PROCESSING_DELAYED` após 3 min | `GET /jobs/{id}` (polling a cada 2 s, para ao chegar em estado final) |
| `/lesson-reports/:reportId/questions` | 07 Perguntas pendentes | resposta por texto **ou** gravador/arquivo (componente `AudioRecorder` reaproveitado) | `POST …/answers` |
| `/lesson-reports/:reportId` | 08 Conferência | 08b PDF sendo gerado · 08c aplicando alteração · 08d versão desatualizada · texto entendido após pedido por áudio · abas "Texto"/"Ver PDF" (390 px) ou lado a lado (1440 px) · diálogos de aprovar e cancelar | `GET …`, `POST …/changes`, `…/approval`, `…/cancellation`, `GET …/versions/{n}/pdf` |
| `/lesson-reports/:reportId/approved` | 09 Relatório aprovado | 09b modo escuro | — |
| `/students/:studentId/semester` | 10 Semestral · período | 10b sem aulas aprovadas · 10c nenhum semestre · aviso de relatórios em aberto ("Gerar mesmo assim"/"Voltar") · semestral já aprovado · semestral em aberto | `GET …/semesters`, `GET …/preview`, `POST …/semester-reports` |
| `/semester-reports/:reportId` | 11 Conferência semestral | mesmos estados da 08 + "N relatórios de aula foram considerados…" + aviso de em aberto | endpoints de `semester-reports` |
| `/semester-reports/:reportId/approved` | 09 (variação semestral) | | — |
| `/students/:studentId/history` | 12 Histórico | 12b vazio · 12c carregando · "Abrir PDF" (fetch autenticado → blob → nova aba; se bloqueada, download) | `GET …/history`, `GET …/official-pdf` |

## Regras de cliente

- **Guarda de sessão**: qualquer 401 leva imediatamente a `/sign-in`, guardando a rota de destino
  (caso de borda "sessão expirada").
- **Nenhuma ação dá falso sucesso** (FR-053): a navegação depois de aprovar, cancelar ou descartar
  só acontece com a resposta 2xx. Um erro mostra a mensagem do catálogo e mantém a tela.
- **Polling** (R-06): intervalo de 2 s, parado quando a aba fica oculta e retomado quando volta, com
  limite de 10 min. Depois disso, mostra "está demorando" e oferece voltar ao Painel; o trabalho
  continua no servidor.
- **Versão apresentada**: a conferência guarda a `currentVersion` recebida e a envia em
  `baseVersion`/`confirmedVersion`. Com 409 `STALE_VERSION`, recarrega e mostra o estado 08d.
- **Gravador** (`AudioRecorder`): MediaRecorder com `audio/webm;codecs=opus` (ou `audio/mp4` no
  Safari), com ouvir, regravar e enviar. O envio só é habilitado com gravação concluída ou arquivo
  selecionado (US1, cenário 2). O limite de tamanho é conferido antes do envio.
- **Tema**: claro ou escuro pelo menu, guardado em `localStorage` (com `try/catch`); na primeira
  visita, segue `prefers-color-scheme` (FR-052).
- **Acessibilidade**: contraste AA nos dois temas (o dourado `--gd` não é usado em texto pequeno
  sobre fundo claro; em texto, usar `--gs #7A5C30`), foco visível, `aria-live="polite"` nas etapas
  de processamento, diálogos com foco preso e `Esc`, alvos de toque de 44 px ou mais, rótulos em
  todos os campos (FR-052, SC-011).
- **Datas**: `AAAA-MM-DD` formatadas sem conversão de fuso (lição 3 do MVP). Carimbos de tempo
  mostrados no fuso da escola.
