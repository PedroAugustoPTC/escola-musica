# Inventário de tratamento de dados

Instituto Musical Felipe Blaft — Sistema de Relatórios Pedagógicos por Voz.

> **Aviso**: este documento é um inventário técnico mantido junto do código. **Não é parecer jurídico.** As bases legais
> abaixo são propostas a validar com o responsável pela LGPD da escola e com assessoria jurídica.

Revisão obrigatória: todo PR que muda o que vai a um provedor (`backend/application/src/main/java/.../outbound/ai/**` ou
`backend/application/src/main/resources/prompts/**`) revisa este arquivo (FR-071). O CI avisa quando isso não acontece.

Contato do responsável pela LGPD: **a preencher pela escola** (nome e e-mail) antes da implantação em produção.

<!-- never-sent: name cpf birth-date guardian -->
<!-- llm-inputs: LessonStructuring AnswerIncorporation LessonRevision SemesterConsolidationInput SemesterRevisionInput -->
<!-- retention-days: transcription=30 unapproved-pdf=30 cancelled-report=90 audit-trail=1826 -->

## Dados que nunca vão a provedor de IA

Nome, CPF, data de nascimento e dados do responsável do aluno **não existem** nos tipos de entrada das portas de IA, então
não há como enviá-los. O teste `StudentDataNotSentToAiIT` confere isso com um aluno canário.

## TranscriptionPort (transcrição de áudio)

| Item | Valor |
|---|---|
| Provedor padrão | Groq, API compatível com OpenAI (`TRANSCRIPTION_URL`, modelo `TRANSCRIPTION_MODEL`); trocável por configuração |
| Dado enviado | o áudio da aula, normalizado (FFmpeg); nenhum identificador do aluno ou do professor |
| Finalidade | converter a fala do professor em texto para estruturar o relatório |
| Base legal proposta (a validar) | execução da atividade pedagógica contratada, com consentimento do responsável quando exigido |
| Local e região | conforme o contrato com o provedor (a confirmar) |
| Prazo no provedor | conforme os termos do provedor (a confirmar) |
| Uso para treinamento | a confirmar nos termos do provedor; contratar sem treinamento com os dados |
| Retenção no sistema | o áudio original **não é retido** após o processamento; a transcrição segue o prazo de transcrição abaixo |

## LlmPort (estruturação e revisão de relatórios)

| Item | Valor |
|---|---|
| Provedor padrão | Groq, API compatível com OpenAI (`LLM_URL`, modelo `LLM_MODEL`); trocável por configuração |
| Dado enviado | só o texto pedagógico: transcrição, conteúdo atual do relatório, perguntas pendentes, resposta e pedido de alteração do professor; consolidação e revisão semestral usam os relatórios aprovados das aulas |
| Entradas aceitas pela porta | `LessonStructuring`, `AnswerIncorporation`, `LessonRevision`, `SemesterConsolidationInput`, `SemesterRevisionInput` |
| Finalidade | estruturar e revisar o relatório; a saída só vira oficial com aprovação do professor |
| Base legal proposta (a validar) | execução da atividade pedagógica contratada |
| Local e região | conforme o contrato com o provedor (a confirmar) |
| Prazo no provedor | conforme os termos do provedor (a confirmar) |
| Uso para treinamento | a confirmar nos termos do provedor; contratar sem treinamento com os dados |
| Prompts | `backend/application/src/main/resources/prompts/*.txt`, versionados |

## ChannelOrchestratorPort (mensageria — Telegram via n8n)

| Item | Valor |
|---|---|
| Provedor | Telegram (API de bots própria, `telegram-bot-api`) e n8n auto-hospedado, que só transporta |
| Dado enviado | mensagens ao professor (relatórios e avisos) e o PDF aprovado; o n8n não guarda execuções |
| Finalidade | canal de uso do professor, em paridade com a Web |
| Base legal proposta (a validar) | execução da atividade pedagógica contratada |
| Local e região | n8n e API de bots na VPS da escola; o Telegram opera conforme os próprios termos |
| Prazo | mensagens ficam no chat do professor; o sistema apaga a mensagem com CPF digitado (`DELETE_MESSAGE`) |
| Uso para treinamento | não aplicável ao n8n; Telegram conforme os próprios termos |

## DocumentStoragePort (PDFs)

| Item | Valor |
|---|---|
| Provedor | disco da própria VPS (volume `documents`); nenhum terceiro |
| Dado guardado | PDFs de relatórios (aula e semestral) |
| Finalidade | entregar o relatório aprovado ao professor e ao histórico da escola |
| Base legal proposta (a validar) | execução da atividade pedagógica contratada |
| Retenção | PDF não aprovado segue o prazo abaixo; aprovado fica até a eliminação a pedido |

## Dados guardados pelo sistema e prazos padrão

Os prazos são configuráveis (`RETENTION_*_DAYS`); os valores abaixo são os padrões de `RetentionPolicy`.

| Dado | Prazo padrão |
|---|---|
| Transcrição | 30 dias |
| PDF não aprovado | 30 dias |
| Relatório cancelado | 90 dias |
| Trilha de auditoria | 1826 dias (cerca de 5 anos) |
| Áudio original | não retido |
| CPF do aluno | cifrado em repouso; nunca em log, resposta de API ou mensagem de erro |

Eliminação a pedido: o administrador apaga os dados de um aluno e recebe comprovante, sem dado pessoal.
