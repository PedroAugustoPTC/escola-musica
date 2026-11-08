# Contrato: telas do portal (Princípio IX)

Continua a numeração da 005 (A1–A12) e da 006 (A13). Todas com tokens do design system, mobile-first a partir de
390 px (tabelas viram cartões abaixo de 640 px), WCAG 2.1 AA nos dois temas (axe no e2e a 390 e 1440 px), foco
gerenciado em paginação e diálogos, textos do catálogo `frontend/src/messages`. Nenhuma tela mostra CPF; nomes só onde
indicado, resolvidos pela API na exibição.

## Área administrativa (somente Web)

| Tela | Rota SPA | API | Conteúdo |
|---|---|---|---|
| **A14 Trilha de auditoria** | `/admin/audit` | `GET /admin/audit-trail`, `/integrity` | Filtros: período (obrigatório, padrão últimos 7 dias, máx. 90), ator, papel, canal, tipo de evento (lista agrupada como em [audit-events.md](./audit-events.md)), resultado, registro-alvo (busca de aluno/professor que devolve id). Tabela: momento, ator, canal, evento (rótulo em português), alvo, resultado. Selo de integridade da cadeia (Íntegra / Quebrada, com data). Atalho "Quem acessou os relatórios deste aluno" a partir do detalhe do aluno (A5) preenche aluno + leituras + 30 dias (SC-007: ≤ 3 interações) |
| **A15 Eliminação** | `/admin/students/:id/erasure` | `POST .../erasure-requests`, `/preview`, `/confirmation` | Passo 1: formulário do pedido (FR-020). Passo 2: alcance em números, professores afetados, aviso irreversível, contato do responsável pela LGPD, campo "digite ELIMINAR", contagem regressiva de 10 min. Passo 3: resultado (`IN_PROGRESS` com "concluindo remoção de arquivos" e botão de repetir, ou `COMPLETED`). Acesso pelo botão "Eliminar dados" no detalhe do aluno (A5), separado de "Inativar" |
| **A15b Comprovante** | `/admin/erasure-requests/:id/receipt` | `GET .../receipt` | Página imprimível (CSS `@media print`, logotipo da escola): protocolo, datas, id curto, alcance, contato do responsável. Sem nome, CPF ou dado do aluno |
| A15c Pedidos | `/admin/erasure-requests` | `GET /admin/erasure-requests` | Lista por situação, destaque para `IN_PROGRESS` |
| **A16 Consentimentos** | seção no detalhe do aluno (A5) | `GET/POST /admin/students/:id/consents`, `POST /admin/consents/:id/revocation` | Situação atual (Válido / Revogado / Termo superado / Ausente, com ícone e texto), histórico, "Registrar consentimento", "Registrar revogação" (diálogo de confirmação) |
| A16b Formulário de aluno (A4) | `/admin/students/new`, `/:id/edit` | contrato da 005 + `ConsentInput` | Seção "Consentimento do responsável" aparece e fica obrigatória quando a data de nascimento indica menor de 18; mostra a versão vigente do termo com link para o texto |
| A16c Pendências | `/admin/consents/pending` | `GET /admin/consents/pending` | Lista de menores sem consentimento válido e motivo; vazio = "Nenhuma pendência" |
| A16d Termos | `/admin/consent-terms` | `GET/POST /admin/consent-terms` | Versões, texto, "exige novo consentimento"; publicar nova versão com confirmação que informa quantos alunos ficarão pendentes |
| **A17 Custos** | `/admin/operations/costs` | `GET /admin/operations/costs` | Seletor de mês; cartões de total do mês (IA + infraestrutura, USD) e médios por áudio, aula, semestral; tabelas por canal, por provedor e por aluno (nome resolvido, ordenável) |
| **A18 Alertas** | `/admin/operations/alerts` | `GET /admin/operations/alerts`, `POST .../acknowledgement` | Ativos no topo (tipo em português, componente, início, última ocorrência, ocorrências), histórico abaixo; prazos e limites efetivos (padrão/configurado); "Marcar como apurado" só em quebra da trilha |
| Indicador | cabeçalho de toda a área administrativa | `GET /admin/operations/alerts/summary` | Ícone + contagem + texto "N alerta(s) ativo(s)" (não só cor), link para A18; consulta ao navegar e a cada 60 s com a aba visível; `aria-live="polite"` |

Menu da área administrativa ganha o grupo "Privacidade e operação": Trilha, Pedidos de eliminação, Pendências de
consentimento, Termos, Custos, Alertas.

## Portal do professor (Web, paridade com o Telegram)

| Tela | Mudança |
|---|---|
| `MyStudentFormScreen` | Seção "Consentimento do responsável" obrigatória para menor (mesmos campos e regras de [telegram-consent.md](./telegram-consent.md)); mostra a versão vigente do termo (`GET /my-students/consent-term`) e o contato do responsável pela LGPD |
| `MyStudentDetailScreen` | Situação do consentimento (só "Registrado" / "Pendente", sem o nome de quem consentiu); botão "Registrar consentimento" quando pendente |
| `RecordingScreen`, `QuestionsScreen`, `ReviewScreen`, `SemesterScreen` | Erro `CONSENT_REQUIRED` mostra o aviso de bloqueio (mesmo texto do bot) com botão "Registrar consentimento" e orientação de procurar a secretaria; a gravação não é enviada |
