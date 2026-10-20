# Contrato: Catálogo de mensagens ao professor

**Feature**: `001-fluxo-relatorios` | Referências: FR-003, FR-039, FR-053, FR-054; [research.md](../research.md) R-18.

- Fonte única dos textos mostrados ao professor nos **dois canais**. O núcleo devolve só o
  **código**; cada canal busca o texto aqui e adapta apenas a forma (componente visual na Web,
  mensagem com botões no Telegram).
- Implementação: `aplicacao/src/main/resources/mensagens/mensagens_pt_BR.properties`. O build do
  frontend gera `mensagens.json` a partir desse arquivo. Um teste falha se um código usado no
  núcleo, na API ou no adaptador não existir no catálogo.
- Placeholders entre chaves. Datas no fuso da escola: `dd/mm/aaaa`, horas `hh:mm`.
- Onde há texto do protótipo, ele prevalece. Os textos marcados com † vieram do MVP (seção 11.2 do
  handoff) e foram revisados quanto a acentuação e tom.
- Os textos finais passam por revisão de UX (tom institucional) na implementação, **sem mudar os
  códigos**.

## Acesso

| Código | HTTP | Texto |
|---|---|---|
| `CODE_REQUIRED` | — | Olá! Para usar o bot, envie o código de acesso que você recebeu da secretaria da escola. † |
| `INVALID_CODE` | 401 | Código inválido. Confira o código e tente de novo, ou peça um novo à secretaria da escola. |
| `EXPIRED_CODE` | 401 | Este código venceu após 7 dias sem uso. Peça um novo código à secretaria da escola. |
| `TEMPORARILY_BLOCKED` | 429 | Acesso bloqueado temporariamente por muitas tentativas incorretas. Tente novamente em {minutes} minuto(s). |
| `LINK_CREATED` | — | Conta vinculada com sucesso, {name}! † |
| `TELEGRAM_ALREADY_LINKED` | — | Esta conta do Telegram já está vinculada a outro professor. Procure a secretaria da escola. |
| `TEACHER_ALREADY_LINKED` | — | Seu cadastro já está vinculado a outra conta do Telegram. Procure a secretaria da escola. |
| `SESSION_EXPIRED` | 401 | Sua sessão expirou. Entre novamente com o seu código. |
| `NOT_AUTHENTICATED` | 401 | Entre com o seu código de acesso para continuar. |
| `SESSION_ENDED` | — | Sessão encerrada. |
| `CONFIRM_SIGN_OUT` | — | Deseja sair? Para usar o bot de novo, será preciso enviar o código de acesso. |
| `LINK_ENDED` | — | Você saiu. Para voltar, envie o seu código de acesso. |
| `NO_CONNECTION` | — | Não foi possível entrar agora. Verifique sua conexão e tente novamente. (somente Web) |

## Início e alunos

| Código | HTTP | Texto |
|---|---|---|
| `GREETING` | — | Olá, {name} |
| `HOME_SUBTITLE` | — | Registre uma aula por áudio ou gere o relatório semestral de um aluno. |
| `NO_STUDENTS` | — | Você ainda não tem alunos associados. Fale com a secretaria da escola. |
| `NO_STUDENT_FOUND` | — | Nenhum aluno encontrado para "{search}". |
| `SEVERAL_STUDENTS_FOUND` | — | Encontrei mais de um aluno. Qual deles? |
| `RESOURCE_NOT_FOUND` | 404 | Não encontramos o que você procurou. (também para aluno não associado ao professor; o código `STUDENT_NOT_ASSIGNED` foi removido na feature 005, FR-050) |
| `CHOOSE_STUDENT` | — | Para qual aluno? |
| `SWITCH_STUDENT_DURING_REVIEW` | — | Você quer trocar para {student}? O relatório de {currentStudent} continua em aberto. |

## Registro de aula e processamento

| Código | HTTP | Texto |
|---|---|---|
| `SELECTED_STUDENT` | — | Aula de hoje, {data}, com {student}. Envie o áudio da aula (mensagem de voz ou arquivo, até {limitMb} MB). |
| `RECEIVED_AUDIO_CONFIRM_STUDENT` | — | Áudio recebido. Este áudio é da aula de {student}? |
| `RECEIVED_AUDIO_CHOOSE_STUDENT` | — | Áudio recebido. De qual aluno é esta aula? |
| `PENDING_AUDIO_EXPIRED` | — | O áudio enviado não foi confirmado a tempo e foi descartado. Envie o áudio de novo. |
| `RECORDING_UNAVAILABLE` | — | Gravação indisponível neste navegador ou microfone bloqueado. Você pode enviar um arquivo de áudio. |
| `FORMATS_AND_LIMIT` | — | MP3, M4A, OGG, WAV, WebM ou vídeo MP4 · até {limitMb} MB |
| `STEP_UPLOADING` | — | Enviando o áudio |
| `STEP_TRANSCRIBING` | — | Transcrevendo |
| `STEP_STRUCTURING` | — | Estruturando o relatório |
| `STEP_READY` | — | Pronto para conferência |
| `STEP_TRANSCRIBING_REQUEST` | — | Transcrevendo o pedido |
| `STEP_APPLYING_CHANGE` | — | Aplicando a alteração |
| `STEP_INCORPORATING_ANSWERS` | — | Incorporando suas respostas |
| `STEP_CONSOLIDATING` | — | Consolidando as aulas do semestre |
| `PROCESSING_DELAYED` | — | Está demorando mais que o normal. O resultado será entregue assim que ficar pronto; você pode sair e voltar depois. |
| `PROCESSING_IN_PROGRESS` | 409 | Este relatório ainda está sendo processado ({step}). Aguarde a conclusão. |
| `AUDIO_TOO_LARGE` | 413 | O áudio enviado é maior que o limite permitido. Grave um trecho mais curto. |
| `UNSUPPORTED_FORMAT` | 415 | Este formato de arquivo não é aceito. Envie MP3, M4A, OGG, WAV, WebM ou vídeo MP4. |
| `AUDIO_NOT_PROCESSABLE` | 422 | Não conseguimos entender este áudio (vazio, sem fala, corrompido ou em formato inválido). Grave ou envie de novo. |
| `PROCESSING_FAILURE_OPTIONS` | — | Não foi possível concluir o relatório. Seu áudio foi recebido e não foi perdido. Tente processar novamente ou cancele. |
| `UNSUPPORTED_TYPE` | — | Não consigo usar este tipo de mensagem aqui. {whatICanReceive} |

## Perguntas, conferência e alteração

| Código | HTTP | Texto |
|---|---|---|
| `PENDING_QUESTIONS` | — | Faltam algumas informações. Responda numa única mensagem, digitando ou gravando: {numberedQuestions} |
| `FOCUS_REMINDER` | — | Texto ou áudio enviado aqui será aplicado a este relatório. Para registrar outra aula, use "Registrar aula". |
| `EMPTY_ANSWER` | 400 | Escreva ou grave a sua resposta antes de enviar. |
| `ANSWER_INCORPORATED` | — | Respostas incorporadas ao relatório. Entendi: “{text}” |
| `REVIEW_TITLE` | — | Confira o relatório antes de aprovar |
| `REVIEW_VERSION` | — | Versão {version} |
| `REVIEW_PDF_GENERATING` | — | O PDF ainda está sendo gerado. |
| `PDF_UNAVAILABLE_NOTICE` | — | Não consegui enviar o PDF agora. Toque no botão abaixo para pedir de novo. |
| `CHANGE_REQUEST_LABEL` | — | Precisa ajustar algo? Descreva a alteração com suas palavras, digitando ou gravando. |
| `EMPTY_CHANGE` | 400 | Descreva a alteração antes de enviar. |
| `CHANGE_BEING_APPLIED` | — | Aplicando a alteração… |
| `CHANGE_APPLIED` | — | Alteração aplicada. Confira a versão {version}. |
| `UNDERSTOOD_TEXT` | — | Entendi: “{text}” |
| `NO_CONTENT_CHANGE` | — | Não identifiquei nenhuma alteração de conteúdo no pedido. O relatório continua na versão {version}. |
| `ACTION_REQUEST_IGNORED` | — | Para aprovar ou cancelar, use os botões próprios; eles pedem confirmação. O relatório continua na versão {version}. |
| `CHANGE_FAILED` | — | Não foi possível aplicar a alteração agora. O relatório continua na versão {version}. Tente de novo. |
| `STALE_VERSION` | 409 | Esta versão está desatualizada: o relatório foi alterado e agora está na versão {currentVersion}. Confira a versão atual antes de continuar. |

## Aprovação, cancelamento e retomada

| Código | HTTP | Texto |
|---|---|---|
| `CONFIRM_APPROVAL` | — | Aprovar a versão {version}? O relatório entrará no histórico do aluno e não poderá mais ser alterado. |
| `REPORT_APPROVED` | — | Relatório aprovado em {data} às {hora}. Ele já consta no histórico do aluno. |
| `REPORT_ALREADY_APPROVED` | 409 | Este relatório já foi aprovado e não pode mais ser alterado. |
| `CONFIRM_CANCELLATION` | — | Cancelar este relatório? Ele deixará de aparecer para você e não poderá ser recuperado. |
| `REPORT_CANCELLED` | — | Relatório cancelado. |
| `REPORT_CANCELLED_ERROR` | 409 | Este relatório foi cancelado e não pode mais ser alterado. |
| `ACTION_NOT_ALLOWED_AT_STEP` | 409 | Esta ação não está disponível nesta etapa do relatório. |
| `EXPIRED_ACTION` | — | Esta opção não vale mais. Veja abaixo a etapa atual. |
| `REPORT_ALREADY_OPEN` | 409 | Você tem um relatório em aberto. Este aluno já tem um relatório iniciado {pelo Telegram / pelo portal} e ainda não concluído (última alteração em {data} às {hora}). |
| `CONFIRM_DISCARD` | — | Descartar o relatório em aberto? Ele deixará de aparecer para você e não poderá ser recuperado. |

## Semestral

| Código | HTTP | Texto |
|---|---|---|
| `NO_SEMESTERS` | — | Nenhum semestre disponível para este aluno ainda. |
| `SEMESTER_IN_PROGRESS` | — | em andamento |
| `NO_APPROVED_LESSONS_IN_PERIOD` | 409 | Nenhum relatório de aula aprovado neste período. Registre as aulas do semestre antes de gerar o relatório. |
| `OPEN_REPORTS_EXIST` | 409 | Há {quantity} relatório(s) de aula em aberto neste período; eles não serão considerados. |
| `SEMESTER_REPORT_ALREADY_APPROVED` | 409 | O relatório semestral deste período já foi aprovado. |
| `CONSIDERED_LESSONS` | — | {quantity} relatório(s) de aula considerado(s) nesta consolidação. |
| `CONSOLIDATION_FAILED` | — | Não foi possível gerar o relatório semestral agora. Nada foi criado. Tente novamente. |

## Histórico e documentos

| Código | HTTP | Texto |
|---|---|---|
| `EMPTY_HISTORY` | — | Este aluno ainda não tem relatórios aprovados. |
| `LESSON_LABEL` | — | Aula de {data} |
| `SEMESTER_LABEL` | — | Semestral ({start} a {end}) |
| `APPROVED_AT` | — | Aprovado em {data} às {hora} |
| `PDF_BLOCKED_BY_BROWSER` | — | O navegador impediu a abertura do PDF; o arquivo foi baixado. (somente Web) |

## Avisos entre canais (bot)

| Código | Texto |
|---|---|
| `WEB_RESULT_NOTICE_READY` | O relatório de {student} está pronto para conferência. |
| `WEB_RESULT_NOTICE_QUESTIONS` | O relatório de {student} tem perguntas pendentes. |
| `WEB_RESULT_NOTICE_NEW_VERSION` | O relatório de {student} tem uma nova versão ({version}). |
| `WEB_RESULT_NOTICE_FAILURE` | O relatório de {student} não pôde ser processado. |
| `CONTINUE_IN_CHAT` | Continuar no chat |

## Genéricos

| Código | HTTP | Texto |
|---|---|---|
| `INVALID_DATA` | 400 | Confira as informações enviadas e tente de novo. |
| `INTERNAL_ERROR` | 500 | Ocorreu um erro inesperado. Tente novamente mais tarde. |
| `SERVICE_UNAVAILABLE` | 503 | O serviço está temporariamente indisponível. Tente novamente em alguns minutos. |
