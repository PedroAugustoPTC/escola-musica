# Validação do quickstart (V1 a V16) — 001-fluxo-relatorios

**Data**: 2026-10-01 · **Ambiente**: pilha de `infra/compose.test.yaml` (PostgreSQL 18, backend com
FFmpeg, Caddy) com IA simulada; canal Telegram exercitado pelo webhook com a API de bots simulada
(WireMock), como em `contracts/telegram-conversa.md` §7.

Cada cenário foi executado nos dois canais por testes automatizados que seguem os mesmos passos do
quickstart e comparam o resultado (SC-006). O que depende de recursos externos não disponíveis neste
ambiente — bot real no @BotFather, servidor local da API de bots com `api_id`/`api_hash` e chaves dos
provedores de IA — está marcado como **pendente de execução manual** e deve ser feito na primeira
subida com credenciais (roteiro em `infra/README.md`).

Legenda: ✅ executado e aprovado · ⏳ pendente de execução manual com credenciais reais

| # | Cenário | Web | Telegram | Evidência (testes) | Observações |
|---|---|---|---|---|---|
| V1 | Acesso | ✅ | ✅ | `fluxo-aula.spec.ts` (painel), `EntrarPortalCasoDeUsoTeste`, `VinculacaoConversaIT`, `RoteiroParidadeAcessoIT` | "Olá, Clara", alunos e nenhum relatório em aberto; "Conta vinculada com sucesso, Clara!". |
| V2 | Bloqueio | ✅ | ✅ | `TelaEntrar.test.tsx` (01c), `TentativaAcessoIT`, `VinculacaoConversaIT` | 6ª tentativa bloqueada com o tempo restante. |
| V3 | Registro com perguntas e alteração (Web) | ✅ | — | `fluxo-aula.spec.ts`, `RelatorioAulaContratoIT`, `RoteiroParidadeRelatorioAulaIT` | Alteração por texto na e2e; por áudio coberta por `RelatorioAulaContratoIT`. Com a gravação real de `assets/`: ⏳ (`IaRealSuiteIT`). |
| V4 | Mesmo fluxo pelo Telegram | — | ✅ | `RegistroAulaConversaIT`, `ConferenciaConversaIT`, `RoteiroParidadeRelatorioAulaIT` | Arquivo, confirmação de aluno, resposta por voz, alteração por texto, aprovação com confirmação, PDF como documento. Com bot real: ⏳. |
| V5 | Vídeo grande pelo Telegram | — | ⏳ | `FfmpegNormalizacaoAudioAdaptadorIT` (extração de áudio de MP4), `NaoRetencaoAudioIT` | O limite acima de 20 MB depende do servidor local da API de bots, que exige credenciais reais. A suíte `IaRealSuiteIT` envia os MP4 de 24 e 31 MB pela Web. |
| V6 | Retomada entre canais | ✅ | ✅ | `retomada.spec.ts`, `RetomadaConversaIT`, `RetomadaContratoIT`, `RoteiroParidadeRetomadaIT` | Começar num canal e concluir no outro, nos dois sentidos; continuar e descartar. |
| V7 | Versão desatualizada | ✅ | ✅ | `RoteiroParidadeRetomadaIT.versaoDesatualizadaERecusadaNosDoisCanais`, `RelatorioAulaConcorrenciaIT` | Recusa com "Esta versão está desatualizada…" e a versão vigente. |
| V8 | Semestral | ✅ | ✅ | `fluxo-semestral.spec.ts`, `SemestralConversaIT`, `RelatorioSemestralContratoIT`, `RoteiroParidadeSemestralIT` | Aviso de relatórios em aberto com "Gerar mesmo assim"/"Voltar", conferência com a contagem e 10 seções, nova geração recusada. |
| V9 | Semestral sem aulas | ✅ | ✅ | `fluxo-semestral.spec.ts`, `RoteiroParidadeSemestralIT` | Nada criado e nenhuma chamada à IA. |
| V10 | Histórico | ✅ | ✅ | `historico.spec.ts`, `HistoricoContratoIT`, `HistoricoConversaIT`, `RoteiroParidadeHistoricoIT` | Listas idênticas (ordem, rótulos, datas) e PDF oficial igual byte a byte nos dois canais. |
| V11 | Acesso cruzado | ✅ | ✅ | `AcessoCruzadoIT`, `historico.spec.ts` | 403 `ALUNO_NAO_ASSOCIADO` e 404 `RECURSO_NAO_ENCONTRADO`; bot responde "Nenhum aluno encontrado…". |
| V12 | Não retenção de áudio | ✅ | ✅ | `NaoRetencaoAudioIT`, `LogSemDadosSensiveisIT` | Diretórios vazios após sucesso e falha; sem bytes de áudio no banco; logs sem dados sensíveis. Volume real do `telegram-bot-api`: ⏳. |
| V13 | Áudio inválido | ✅ | ✅ | `RelatorioAulaContratoIT`, `RegistroAulaConversaIT`, `NaoRetencaoAudioIT` | "Não conseguimos entender este áudio…"; nenhuma `aula` criada. |
| V14 | Aviso entre canais | — | ✅ | `NotificacaoTelegramAdaptadorIT`, `RetomadaConversaIT` | "O relatório de {aluno} está pronto para conferência." com "Continuar no chat". |
| V15 | Encerrar acesso | ✅ | ✅ | `fluxo-aula.spec.ts` (401 após encerrar), `SessaoWebCasosDeUsoTeste`, `VinculacaoConversaIT` (sair) | |
| V16 | Acessibilidade e responsividade | ✅ | — | `acessibilidade.spec.ts` | axe sem violações AA nas telas 01–12 (e variações) a 390 e 1440 px, temas claro e escuro, sem rolagem horizontal, acesso por teclado. Comparação com os protótipos em `revisao-visual.md`. |

## Execuções desta validação

| Suíte | Resultado |
|---|---|
| `docker compose -f infra/compose.test.yaml run --rm testes-backend` | BUILD SUCCESS — 211 testes unitários no `nucleo`, 7 unitários na `aplicacao` (arquitetura e catálogo), 145 de integração; JaCoCo do `nucleo`: 98,0% linhas e 90,8% branches (mínimo 90%) |
| Frontend (ESLint, TypeScript, Vitest) | 59 testes, sem apontamentos |
| `docker compose -f infra/compose.test.yaml run --rm testes-e2e` | 19 testes ponta a ponta e de acessibilidade aprovados |
| `testes-ia-real` (`IaRealSuiteIT`) | ⏳ ignorada sem `TRANSCRICAO_CHAVE` e `LLM_CHAVE`; o contexto sobe com provedores reais e FFmpeg real |

## Defeitos encontrados e corrigidos durante a validação

1. O JAR de produção não incluía o PDFBox (estava em escopo de teste): nenhum PDF era gerado fora
   dos testes e o trabalho ficava preso em "Incorporando suas respostas…".
2. Um `Error` no processamento deixava o trabalho em execução para sempre, sem log.
3. O healthcheck do backend usava `/dev/tcp` com `sh` (dash) e nunca ficava saudável.
4. O Caddy tentava certificado público para nomes internos na pilha de testes.

## Pendências manuais (primeira subida com credenciais)

V3–V5 com as gravações reais e IA real (`testes-ia-real`), V4/V5/V12/V14 com o bot de testes e o
servidor local da API de bots, e o envio do MP4 de 31 MB pelo Telegram.
