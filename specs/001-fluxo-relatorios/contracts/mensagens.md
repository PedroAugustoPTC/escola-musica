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
| `CODIGO_NECESSARIO` | — | Olá! Para usar o bot, envie o código de acesso que você recebeu da secretaria da escola. † |
| `CODIGO_INVALIDO` | 401 | Código inválido. Confira o código e tente de novo, ou peça um novo à secretaria da escola. |
| `CODIGO_EXPIRADO` | 401 | Este código venceu após 7 dias sem uso. Peça um novo código à secretaria da escola. |
| `BLOQUEADO_TEMPORARIAMENTE` | 429 | Acesso bloqueado temporariamente por muitas tentativas incorretas. Tente novamente em {minutos} minuto(s). |
| `VINCULO_CRIADO` | — | Conta vinculada com sucesso, {nome}! † |
| `TELEGRAM_JA_VINCULADO` | — | Esta conta do Telegram já está vinculada a outro professor. Procure a secretaria da escola. |
| `PROFESSOR_JA_VINCULADO` | — | Seu cadastro já está vinculado a outra conta do Telegram. Procure a secretaria da escola. |
| `SESSAO_EXPIRADA` | 401 | Sua sessão expirou. Entre novamente com o seu código. |
| `NAO_AUTENTICADO` | 401 | Entre com o seu código de acesso para continuar. |
| `SESSAO_ENCERRADA` | — | Sessão encerrada. |
| `CONFIRMAR_SAIR` | — | Deseja sair? Para usar o bot de novo, será preciso enviar o código de acesso. |
| `VINCULO_ENCERRADO` | — | Você saiu. Para voltar, envie o seu código de acesso. |
| `SEM_CONEXAO` | — | Não foi possível entrar agora. Verifique sua conexão e tente novamente. (somente Web) |

## Início e alunos

| Código | HTTP | Texto |
|---|---|---|
| `SAUDACAO` | — | Olá, {nome} |
| `INICIO_SUBTITULO` | — | Registre uma aula por áudio ou gere o relatório semestral de um aluno. |
| `SEM_ALUNOS` | — | Você ainda não tem alunos associados. Fale com a secretaria da escola. |
| `NENHUM_ALUNO_ENCONTRADO` | — | Nenhum aluno encontrado para "{busca}". |
| `VARIOS_ALUNOS_ENCONTRADOS` | — | Encontrei mais de um aluno. Qual deles? |
| `ALUNO_NAO_ASSOCIADO` | 403 | Este aluno não está associado a você. |
| `RECURSO_NAO_ENCONTRADO` | 404 | Não encontramos o que você procurou. |
| `ESCOLHER_ALUNO` | — | Para qual aluno? |
| `TROCAR_ALUNO_DURANTE_CONFERENCIA` | — | Você quer trocar para {aluno}? O relatório de {alunoAtual} continua em aberto. |

## Registro de aula e processamento

| Código | HTTP | Texto |
|---|---|---|
| `ALUNO_SELECIONADO` | — | Aula de hoje, {data}, com {aluno}. Envie o áudio da aula (mensagem de voz ou arquivo, até {limiteMb} MB). |
| `AUDIO_RECEBIDO_CONFIRMAR_ALUNO` | — | Áudio recebido. Este áudio é da aula de {aluno}? |
| `AUDIO_RECEBIDO_ESCOLHER_ALUNO` | — | Áudio recebido. De qual aluno é esta aula? |
| `AUDIO_PENDENTE_EXPIRADO` | — | O áudio enviado não foi confirmado a tempo e foi descartado. Envie o áudio de novo. |
| `GRAVACAO_INDISPONIVEL` | — | Gravação indisponível neste navegador ou microfone bloqueado. Você pode enviar um arquivo de áudio. |
| `FORMATOS_E_LIMITE` | — | MP3, M4A, OGG, WAV, WebM ou vídeo MP4 · até {limiteMb} MB |
| `ETAPA_ENVIANDO` | — | Enviando o áudio |
| `ETAPA_TRANSCREVENDO` | — | Transcrevendo |
| `ETAPA_ESTRUTURANDO` | — | Estruturando o relatório |
| `ETAPA_PRONTO` | — | Pronto para conferência |
| `ETAPA_TRANSCREVENDO_PEDIDO` | — | Transcrevendo o pedido |
| `ETAPA_APLICANDO_ALTERACAO` | — | Aplicando a alteração |
| `ETAPA_INCORPORANDO_RESPOSTAS` | — | Incorporando suas respostas |
| `ETAPA_CONSOLIDANDO` | — | Consolidando as aulas do semestre |
| `PROCESSAMENTO_DEMORADO` | — | Está demorando mais que o normal. O resultado será entregue assim que ficar pronto; você pode sair e voltar depois. |
| `PROCESSAMENTO_EM_ANDAMENTO` | 409 | Este relatório ainda está sendo processado ({etapa}). Aguarde a conclusão. |
| `AUDIO_MUITO_GRANDE` | 413 | O áudio enviado é maior que o limite permitido. Grave um trecho mais curto. |
| `FORMATO_NAO_SUPORTADO` | 415 | Este formato de arquivo não é aceito. Envie MP3, M4A, OGG, WAV, WebM ou vídeo MP4. |
| `AUDIO_NAO_PROCESSAVEL` | 422 | Não conseguimos entender este áudio (vazio, sem fala, corrompido ou em formato inválido). Grave ou envie de novo. |
| `FALHA_PROCESSAMENTO_OPCOES` | — | Não foi possível concluir o relatório. Seu áudio foi recebido e não foi perdido. Tente processar novamente ou cancele. |
| `TIPO_NAO_SUPORTADO` | — | Não consigo usar este tipo de mensagem aqui. {oQuePossoReceber} |

## Perguntas, conferência e alteração

| Código | HTTP | Texto |
|---|---|---|
| `PERGUNTAS_PENDENTES` | — | Faltam algumas informações. Responda numa única mensagem, digitando ou gravando: {perguntasNumeradas} |
| `LEMBRETE_FOCO` | — | Texto ou áudio enviado aqui será aplicado a este relatório. Para registrar outra aula, use "Registrar aula". |
| `RESPOSTA_VAZIA` | 400 | Escreva ou grave a sua resposta antes de enviar. |
| `RESPOSTA_INCORPORADA` | — | Respostas incorporadas ao relatório. Entendi: “{texto}” |
| `CONFERENCIA_TITULO` | — | Confira o relatório antes de aprovar |
| `CONFERENCIA_VERSAO` | — | Versão {versao} |
| `CONFERENCIA_PDF_GERANDO` | — | O PDF ainda está sendo gerado. |
| `PEDIDO_ALTERACAO_ROTULO` | — | Precisa ajustar algo? Descreva a alteração com suas palavras, digitando ou gravando. |
| `ALTERACAO_VAZIA` | 400 | Descreva a alteração antes de enviar. |
| `ALTERACAO_EM_APLICACAO` | — | Aplicando a alteração… |
| `ALTERACAO_APLICADA` | — | Alteração aplicada. Confira a versão {versao}. |
| `TEXTO_ENTENDIDO` | — | Entendi: “{texto}” |
| `SEM_ALTERACAO_DE_CONTEUDO` | — | Não identifiquei nenhuma alteração de conteúdo no pedido. O relatório continua na versão {versao}. |
| `PEDIDO_DE_ACAO_IGNORADO` | — | Para aprovar ou cancelar, use os botões próprios; eles pedem confirmação. O relatório continua na versão {versao}. |
| `FALHA_ALTERACAO` | — | Não foi possível aplicar a alteração agora. O relatório continua na versão {versao}. Tente de novo. |
| `VERSAO_DESATUALIZADA` | 409 | Esta versão está desatualizada: o relatório foi alterado e agora está na versão {versaoVigente}. Confira a versão atual antes de continuar. |

## Aprovação, cancelamento e retomada

| Código | HTTP | Texto |
|---|---|---|
| `CONFIRMAR_APROVACAO` | — | Aprovar a versão {versao}? O relatório entrará no histórico do aluno e não poderá mais ser alterado. |
| `RELATORIO_APROVADO` | — | Relatório aprovado em {data} às {hora}. Ele já consta no histórico do aluno. |
| `RELATORIO_JA_APROVADO` | 409 | Este relatório já foi aprovado e não pode mais ser alterado. |
| `CONFIRMAR_CANCELAMENTO` | — | Cancelar este relatório? Ele deixará de aparecer para você e não poderá ser recuperado. |
| `RELATORIO_CANCELADO` | — | Relatório cancelado. |
| `RELATORIO_CANCELADO_ERRO` | 409 | Este relatório foi cancelado e não pode mais ser alterado. |
| `ACAO_NAO_PERMITIDA_NA_ETAPA` | 409 | Esta ação não está disponível nesta etapa do relatório. |
| `ACAO_EXPIRADA` | — | Esta opção não vale mais. Veja abaixo a etapa atual. |
| `RELATORIO_EM_ABERTO` | 409 | Você tem um relatório em aberto. Este aluno já tem um relatório iniciado {pelo Telegram / pelo portal} e ainda não concluído (última alteração em {data} às {hora}). |
| `CONFIRMAR_DESCARTE` | — | Descartar o relatório em aberto? Ele deixará de aparecer para você e não poderá ser recuperado. |

## Semestral

| Código | HTTP | Texto |
|---|---|---|
| `SEM_SEMESTRES` | — | Nenhum semestre disponível para este aluno ainda. |
| `SEMESTRE_EM_ANDAMENTO` | — | em andamento |
| `SEM_AULAS_APROVADAS_NO_PERIODO` | 409 | Nenhum relatório de aula aprovado neste período. Registre as aulas do semestre antes de gerar o relatório. |
| `HA_RELATORIOS_EM_ABERTO` | 409 | Há {quantidade} relatório(s) de aula em aberto neste período; eles não serão considerados. |
| `SEMESTRAL_JA_APROVADO` | 409 | O relatório semestral deste período já foi aprovado. |
| `AULAS_CONSIDERADAS` | — | {quantidade} relatório(s) de aula considerado(s) nesta consolidação. |
| `FALHA_CONSOLIDACAO` | — | Não foi possível gerar o relatório semestral agora. Nada foi criado. Tente novamente. |

## Histórico e documentos

| Código | HTTP | Texto |
|---|---|---|
| `HISTORICO_VAZIO` | — | Este aluno ainda não tem relatórios aprovados. |
| `ROTULO_AULA` | — | Aula de {data} |
| `ROTULO_SEMESTRAL` | — | Semestral ({inicio} a {fim}) |
| `APROVADO_EM` | — | Aprovado em {data} às {hora} |
| `PDF_BLOQUEADO_NAVEGADOR` | — | O navegador impediu a abertura do PDF; o arquivo foi baixado. (somente Web) |

## Avisos entre canais (bot)

| Código | Texto |
|---|---|
| `AVISO_RESULTADO_WEB_PRONTO` | O relatório de {aluno} está pronto para conferência. |
| `AVISO_RESULTADO_WEB_PERGUNTAS` | O relatório de {aluno} tem perguntas pendentes. |
| `AVISO_RESULTADO_WEB_NOVA_VERSAO` | O relatório de {aluno} tem uma nova versão ({versao}). |
| `AVISO_RESULTADO_WEB_FALHA` | O relatório de {aluno} não pôde ser processado. |
| `CONTINUAR_NO_CHAT` | Continuar no chat |

## Genéricos

| Código | HTTP | Texto |
|---|---|---|
| `DADOS_INVALIDOS` | 400 | Confira as informações enviadas e tente de novo. |
| `ERRO_INTERNO` | 500 | Ocorreu um erro inesperado. Tente novamente mais tarde. |
| `SERVICO_INDISPONIVEL` | 503 | O serviço está temporariamente indisponível. Tente novamente em alguns minutos. |
