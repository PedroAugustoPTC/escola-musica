# Revisão de segurança e privacidade — 001-fluxo-relatorios

**Data**: 2026-10-01 · **Escopo**: autenticação e sessão, autorização, minimização de dados enviados
à IA, retenção de áudio, logs, borda HTTPS (`infra/caddy/Caddyfile`), segredos e imagens.
**Princípio IV** da constituição; FR-004 a FR-011, FR-018, FR-044, FR-048, FR-055; SC-008.

Legenda: ✅ conforme · ⚠️ conforme com ressalva (risco residual aceito ou ação futura)

## 1. Autenticação e sessão

| # | Item | Situação | Evidência |
|---|---|---|---|
| 1.1 | Código de acesso só em claro na saída da carga; no banco apenas HMAC-SHA256 com segredo próprio | ✅ | `ProtecaoDadosAdaptador.hashCodigoAcesso`; coluna `professor.codigo_acesso_hash` |
| 1.2 | Código vence após 7 dias sem uso; bloqueio após 5 erros por 15 min, por canal e origem (HMAC da origem) | ✅ | `AutenticacaoCodigoServico`, `tentativa_acesso`; testes de acesso do núcleo e `VinculacaoConversaIT` |
| 1.3 | Cookie `sessao` opaco (32 bytes aleatórios), `HttpOnly; Secure; SameSite=Strict; Path=/`; no banco só o SHA-256 do token | ✅ | `CookieSessao`, `sessao_web.token_hash` |
| 1.4 | Expiração da sessão (12 h) e encerramento no servidor; requisição seguinte → 401 | ✅ | `SessaoWebCasosDeUsoTeste`; e2e `fluxo-aula.spec.ts` ("encerrar sessão…") |
| 1.5 | CSRF com cookie `XSRF-TOKEN` (`SameSite=Strict`) e cabeçalho em toda mutação; só o webhook do Telegram fica fora | ✅ | `SegurancaConfiguracao` |
| 1.6 | Webhook do Telegram exige `X-Telegram-Bot-Api-Secret-Token`, comparado em tempo constante; não é exposto pelo Caddy (só a rede interna o alcança) | ✅ | `TelegramWebhookControlador` (`MessageDigest.isEqual`); `Caddyfile` só faz proxy de `/api/*` |
| 1.7 | Tudo que não é `/api/**`, o webhook, `/error` ou `actuator/health|prometheus` é negado | ✅ | `anyRequest().denyAll()` |
| 1.8 | `actuator/prometheus` é público no backend | ⚠️ | Não é roteado pelo Caddy (inacessível de fora). Se um dia for exposto, exigir autenticação. |

## 2. Autorização (SC-008)

| # | Item | Situação | Evidência |
|---|---|---|---|
| 2.1 | Toda rota de aluno verifica a associação professor–aluno → 403 `ALUNO_NAO_ASSOCIADO` | ✅ | `AcessoCruzadoIT` (7 rotas) |
| 2.2 | Relatórios, versões, PDFs e trabalhos de outro professor → 404, sem distinguir "não existe" de "não é seu" | ✅ | `AcessoCruzadoIT` (16 rotas); `HistoricoContratoIT` |
| 2.3 | O bot não encontra aluno de outro professor e ignora ações sobre relatórios alheios; nenhum PDF é enviado | ✅ | `AcessoCruzadoIT.botNaoEncontraOAlunoNemAgeSobreRelatoriosDeBruno` |
| 2.4 | Respostas de erro sem mensagem interna, pilha ou exceção | ✅ | `server.error.include-*: never/false`; `TratadorErrosControlador` usa só códigos do catálogo |
| 2.5 | PDFs entregues só por rota autenticada, com `Cache-Control: no-store, private`; nome sem identificadores internos | ✅ | `DocumentoControlador.pdf` |

## 3. Minimização de dados enviados à IA (FR-044)

| # | Item | Situação | Evidência |
|---|---|---|---|
| 3.1 | Estruturação recebe só a transcrição; incorporação e revisão, só o conteúdo do relatório e o pedido | ✅ | `EntradaTarefa` (records sem nome, CPF, nascimento ou idade) |
| 3.2 | Consolidação semestral recebe só datas e conteúdos das aulas; "Informações Gerais" (nome, idade, professor, período) são preenchidas pelo sistema | ✅ | `EntradaConsolidacaoSemestral`; `SecoesRelatorioSemestral` |
| 3.3 | `toString` das entradas não expõe conteúdo (evita vazamento em logs/exceções) | ✅ | `EntradaTarefa.*.toString()` |
| 3.4 | A transcrição pode conter o primeiro nome do aluno falado pelo professor na gravação | ⚠️ | Risco residual inerente ao áudio; não há envio estruturado de dados pessoais. Orientar os professores a evitar sobrenome e dados pessoais na fala. |
| 3.5 | Provedores configurados por variável de ambiente; a chave nunca é registrada em log | ✅ | `ProvedoresIaConfiguracao`; `LogSemDadosSensiveisIT` |

## 4. Áudio, dados pessoais e logs

| # | Item | Situação | Evidência |
|---|---|---|---|
| 4.1 | Áudio nunca persistido: temporário em `tmpfs`, apagado após sucesso e falha; arquivo do Telegram apagado após a cópia; limpeza periódica de órfãos (> 30 min) | ✅ | `NaoRetencaoAudioIT`; `ArquivoTelegramAdaptador`; `LimpezaAudiosTemporariosTarefa` |
| 4.2 | Nenhuma coluna binária além de `aluno.cpf_cifrado`; nenhum conteúdo de áudio em colunas de texto | ✅ | `NaoRetencaoAudioIT.bancoNaoGuardaBytesDeAudio` |
| 4.3 | CPF cifrado com AES-256-GCM (IV aleatório) e HMAC para unicidade; nunca exibido | ✅ | `ProtecaoDadosAdaptador`; carga mostra só o resumo |
| 4.4 | Logs sem transcrição, conteúdo, respostas, nome de aluno, CPF, código, token de sessão ou do bot — sucesso e erro, nos dois canais | ✅ | `LogSemDadosSensiveisIT` |
| 4.5 | Trabalho com erro inesperado (inclusive `Error`) é marcado como falho e registrado sem dados do relatório | ✅ | Corrigido nesta revisão: antes, um `Error` deixava o trabalho preso em execução e sem log (`TrabalhoWorker`, `ProcessadorTrabalhoServico`) |

## 5. Borda HTTPS e cabeçalhos (`infra/caddy/Caddyfile`)

| # | Item | Situação | Evidência |
|---|---|---|---|
| 5.1 | Único ponto de entrada; em produção só 80 e 443 publicados; banco, backend e `telegram-bot-api` na rede interna | ✅ | `compose.prod.yaml`; conferência com `ss -tlnp` em `infra/README.md` |
| 5.2 | HSTS (1 ano, subdomínios), CSP restritiva (`default-src 'self'`, `frame-ancestors 'none'`, `object-src`/`frame-src` só `self` e `blob:`), `nosniff`, `Referrer-Policy`, `Permissions-Policy` (microfone só no próprio site) | ✅ | `Caddyfile` |
| 5.3 | `style-src 'unsafe-inline'` | ⚠️ | Necessário para estilos inline do React (larguras dinâmicas). Sem impacto em scripts (`script-src 'self'`). |
| 5.4 | Corpo de `/api/*` limitado a 55 MB na borda; limite de negócio de 50 MB no backend (413 `AUDIO_MUITO_GRANDE`) | ✅ | `Caddyfile`; `RecepcaoAudioWeb` |
| 5.5 | `admin off` e cabeçalho `Server` removido | ✅ | `Caddyfile` |

## 6. Segredos e imagens

| # | Item | Situação | Evidência |
|---|---|---|---|
| 6.1 | Segredos só por variáveis de ambiente (`infra/.env`, fora do Git); `.env.example` sem valores | ✅ | `.gitignore`; `infra/.env.example` |
| 6.2 | Nenhum segredo nas imagens: o contexto do `web` (raiz) exclui `infra/.env`, `infra/carga` e `assets`; o contexto do backend (`backend/`) não contém segredos | ✅ | `.dockerignore`, `backend/.dockerignore` |
| 6.3 | Backend roda como usuário sem privilégios (`relatorios`) | ✅ | `backend/aplicacao/Dockerfile` |
| 6.4 | Segredos de testes (chave de CPF, HMAC) existem só em arquivos de teste e no `compose.test.yaml` | ✅ | São valores fictícios, sem uso fora da pilha de testes |
| 6.5 | Implantação por workflow manual com ambiente protegido por aprovação; chave SSH dedicada e `known_hosts` fixo | ✅ | `.github/workflows/implantar.yml` |
| 6.6 | Arquivos de carga reais contêm CPF | ⚠️ | Fora do Git por regra do `.gitignore`; `infra/README.md` orienta montar só durante o comando e apagar em seguida. |

## Conclusão

Nenhuma pendência bloqueante. Correções feitas durante a revisão: escopo do PDFBox (o JAR de
produção não gerava PDFs), healthcheck do backend e tratamento de `Error` no processamento de
trabalhos. Ressalvas 1.8, 3.4, 5.3 e 6.6 aceitas como risco residual documentado.
