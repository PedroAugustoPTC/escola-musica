# Evidência — Fatia 3: `aplicacao` → `application` + `V7`

Registro das tasks T051 a T103 (`tasks.md`). Nenhum valor de texto, CPF, nome de aluno, chave ou token é
registrado aqui; só contagens, nomes de arquivos e resultados.

## Linha de base (T051)

Árvore: commit da fatia 2 (`7781be2`). Nenhum arquivo de código mudou entre a rodada de comparação da fatia 2
(T050) e este ponto de partida, então a linha de base da fatia 3 **é o resultado final da fatia 2**
(`evidence/slice-2.md`), sem rodar de novo suítes sobre uma árvore idêntica.

| Suíte | Testes | Resultado |
|---|---|---|
| `backend` — `core` (Surefire) | 324 | verde |
| `backend` — `aplicacao` unitários | 79 | verde |
| `backend` — Failsafe padrão | 253 (1 ignorado) | verde |
| `backend` — Failsafe `transporte-interno` | 71 | verde |
| `backend` — total | 727 | verde |
| `frontend` | 77 (13 arquivos) | verde |
| `e2e` | 19 | verde |

Cobertura do `core` (instruções / branches / métodos / linhas): 14.075/14.297 · 806/875 · 745/766 · 3.176/3.237
(`evidence/slice-2-jacoco.csv`). Guardiões verdes; exclusões: 838 arquivos e 1.185 termos retidos.

## Decisões tomadas durante a fatia (registro corrente)

1. **Retenção da fatia 3 (T052)** — `tools/held-back/slice-3.json`: 45 tipos de fio com 190 componentes/membros (DTOs
   `*Dto`/`*Requisicao`/`*Resposta` do Portal e do canal interno, mais `ErroResposta`, `AtualizacaoResposta`,
   `ConfirmacaoResposta`, `ErroCanalResposta`, `EstadoAcaoResposta`) e os parâmetros de rota com nome implícito dos
   controladores (`@PathVariable UUID alunoId` sem nome: trocar o nome trocaria a rota). A fatia 4 quita.
   **B1**: só `PropriedadesAplicacao.EntregaProativa` (`DIRETA`/`ORQUESTRADOR`): testes comparam o texto do nome
   (`.name()` igual a `"DIRETA"` e mensagem contendo `ORQUESTRADOR`), então trocar a constante alteraria expectativa de
   texto. **Divergência de `identifier-map.md` §6**, que previa `DIRECT`/`ORCHESTRATOR` já na fatia 3: o valor de
   configuração permanece em português (e entra na retenção, como a tarefa T066 prevê para o caso B1).
2. **Palavras reservadas do Java** (o mapa geral cairia em `private`, `continue`, `return`, `transient`, `protected`,
   `long`, `false`, `final`): nomes por papel em `explicit-pairs.json` (`privado`→`privateChat`, `continuar`→`proceed`,
   `voltar`→`back`, `transitoria`→`isTransient`, `protegido`→`guarded`, `longo`→`longText`, `falso`→`fakeFile`,
   `finais`→`finalActions`). O primeiro rodar da receita acusou `private` em `TelegramConversationInterpreter`; a
   árvore foi restaurada e a receita rodada de novo inteira (nada de edição manual de remendo).
3. **Revisões de nome no mapa** (antes da receita): `SessaoWeb*`→`WebSession*`, `UpdateTelegramProcessadoJpa`→
   `ProcessedTelegramUpdateJpa`, `CookieSessao`→`SessionCookie`, `AlteracaoTextoRequisicao`→`ChangeTextRequest` (nome do
   esquema do OpenAPI novo), `AtualizacaoResposta`/`ConfirmacaoResposta`→`…Response`, `EnvioTelegram`→`TelegramSender`,
   `OpenHtmlToPdfGeracaoPdfAdaptador`→`OpenHtmlToPdfGenerationAdapter`, `LimpezaAudiosTemporariosTarefa`→
   `TemporaryAudioCleanupTask`; chaves de `jsonb`: `semAlteracaoDeConteudo`→`noContentChange` e `estudosRealizados`→
   `completedStudies` (casam com o esquema de IA do contrato) e `semAluno`/`semOpcoes`/`semPeriodo`→`semester*`
   (o prefixo `sem` era "semestral", não "sem").
4. **Esquema de IA (T071)**: o contrato `ai-lesson-report.schema.json` traz `actionRequest` com `NONE|APPROVE|CANCEL`,
   mas o valor é B1/B3 (também está nos prompts e em `AiLessonReportOutput.NONE = "NENHUM"`): na fatia 3 só as **chaves**
   mudam; os valores `NENHUM|APROVAR|CANCELAR` ficam e passam na fatia 4 junto de prompts e código.
5. **Mapa de renomeação do banco como recurso de teste (T088)**: gerado como `db/rename-map.json` (JSON em vez de YAML:
   mesmo conteúdo, sem dependência de leitor YAML nos testes).
6. **`MigracaoV6IT`**: o teste migra "tudo" a partir da V5 e espera `migrationsExecuted == 1`; com a V7 isso viraria 2 e o
   SQL antigo deixaria de existir. Os pontos `flyway(null)` passam a `flyway("6")` (intenção original: a V6 é aditiva);
   o teste opera no esquema da V6 e **não** recebe a troca de nomes de tabela nos literais SQL. Não é renomeação e fica
   listado aqui para a sua revisão.
7. **CLI**: além de `carga`/`massa`/`emitir-codigo`/`--arquivo`/perfil, a opção `--simular` passa a `--simulate`
   (o mapa de §7 não a cita; é parte da interface, e o guardião varre literais de uma palavra). `CargaComandoIT`
   afirma `contains("Uso: carga")`: o texto de uso cita o comando e acompanha o nome novo (`Uso: seed`).
8. **Identificador dentro de texto**: mensagens que citam variável de ambiente ou comando (erros de configuração,
   texto de uso do CLI, ajuda do `local.sh`) trocam só o nome citado, em produção e na asserção do teste, na mesma
   mudança. Lista completa em `tools/rewrite/literal-edits-slice-3.json` (classe `env-text`/`manual`), para a sua revisão.
9. **Chaves do arquivo de carga e caminho do erro (T078)**: as chaves de `infra/carga/*.json` passaram a inglês
   (`professores→teachers`, `alunos→students`, `nome→name`, `dataNascimento→birthDate`, `nomeResponsavel→guardianName`…) porque
   os componentes dos `record` da carga foram renomeados. O caminho que o comando imprime nos erros (`SeedError`, no `core`)
   cita a chave do arquivo e acompanha: `alunos[1].cpf: CPF_INVALIDO — CPF inválido.` virou `students[1].cpf: …` em
   `SeedCommandIT` e na asserção `LoadInitialRegistryUseCaseTest`. **É uma expectativa de texto alterada** (só o nome citado;
   a mensagem em português fica idêntica). Mesma natureza da decisão 8; peço confirmação explícita no checkpoint.
10. **Fio do relatório semestral mantido em português**: o registro `SemesterReportContent` do `core` já tem componentes em
    inglês (são as chaves persistidas, que a `V7` renomeia) e o controlador o serializava direto no corpo da resposta, o que
    mudaria o contrato do Portal antes da fatia 4. Novo `inbound/web/SemesterReportContentDto` (nomes do contrato atual) +
    `SemesterReportContentDto.from(...)`; o relatório de aula já seguia esse padrão. Única alteração estrutural da fatia
    (autorizada em `explicit-pairs.json → structuralEdits`; coberta por `SemesterReportContractIT`, que valida a resposta contra o
    OpenAPI). A fatia 4 apaga o DTO ao migrar o contrato.
11. **Literais de teste que citam nomes renomeados** (listados em `explicit-pairs.json → literals` com motivo): JSON do
    stub da IA (`LlmOpenAiCompatibleAdapterIT`, chaves do esquema novo), JSON do arquivo de carga (`SeedCommandIT`),
    `toString` de `record` esperado em `AccessParityScriptIT` (`ResumoInicio[totalAlunos=…]`→`HomeSummary[totalStudents=…]`),
    consultas a `information_schema` (`RegistryPersistenceIT`, `AudioNonRetentionIT`), regex do YAML
    (`ReplyWithinFiveSecondsIT`: `dispatch-interval-ms`), chave citada na mensagem de `TelegramChannelConfig` e na asserção
    de `TelegramChannelConfigTest` (`lease-segundos`→`lease-seconds`) e a chave de coluna lida de `Map` em testes de conversa
    (`relatorio_foco_tipo`→`focused_report_type` etc.). **Nenhum texto em português para pessoas mudou.**
12. **Ferramentas** (para a revisão): `literal-classes.mjs` ganhou fragmentos de concatenação SQL, primeiro argumento de
    chamadas JDBC, nome de tabela passado a `count`/`statusOf`, `properties = {…}` em qualquer anotação, `${chave}` em
    qualquer atributo de anotação e `!perfil`; `rename-check` ganhou `structuralEdits` e `tokenEdits` para trecho opaco
    (CSS inline do modelo de PDF: `element(corrido)`→`element(running)`); `tools/schema-shape.mjs` (T098).

## Resultado das suítes (T097)

Rodada completa em banco novo (`down -v`), depois das últimas correções:

| Suíte | Testes | Resultado |
|---|---|---|
| `backend` — `core` (Surefire) | 324 | verde |
| `backend` — `application` unitários | 79 | verde |
| `backend` — Failsafe padrão | 264 (1 ignorado) | verde |
| `backend` — Failsafe `transporte-interno` | 71 | verde |
| `backend` — total | 738 | verde |
| `frontend` | 77 (13 arquivos) | verde |
| `e2e` | 19 | verde |

Contra a linha de base (727): **+11 testes**, todos novos da fatia: `MigrationV7StructureIT` (4), `MigrationV7ProtectionsIT` (3),
`MigrationV7JsonKeysIT` (1), `MigrationV7ReadableDataIT` (1), `PdfFileNamesIT` (2). Depois desta rodada só houve a troca da
chave citada na mensagem de `TelegramChannelConfig` (decisão 11), validada por rodada estreita (`TelegramChannelConfigTest` +
guardiões).

## `rename-check` e contratos (T098)

`evidence/slice-3-rename-check.txt`: 449 arquivos Java pareados e 56 arquivos de outros tipos; **0** pares fora do mapa, **0**
literais de texto alterados, **0** arquivos sem par, **0** colisões, **0** outras divergências. `--catalog`: 217 chaves na base e
217 no novo, 0 divergências (texto e chaves idênticos; mudou só o caminho). Esquemas de IA: forma idêntica à da base, só nomes
trocados pelo mapa (o `$id` acompanha o nome do arquivo). Os literais do nome de PDF exibido (`relatorio-aula-…pdf`) ficaram
idênticos (`PdfFileNamesIT` verde).

## Banco (V7)

`V7__rename_to_english.sql`: 17 tabelas, 168 colunas, 1 sequência, 201 constraints, 22 índices avulsos, 7 conjuntos de chaves
`jsonb` e recriação de `uk_processing_job_semester_consolidation_running` com `parameters ->> 'periodKey'`. `V1`–`V6` sem
alteração. `MigrationV7StructureIT` (catálogo antes/depois igual pelo mapa, nenhum nome em português no esquema),
`MigrationV7ProtectionsIT`, `MigrationV7JsonKeysIT` e `MigrationV7ReadableDataIT` (dados da V6 lidos pelos adaptadores reais após a V7).

## Cobertura do `core` (T099)

`evidence/slice-3-jacoco.csv` é **idêntico** ao da fatia 2: linhas 3.176/3.237 (98,12 %), branches 806/875 (92,11 %), métodos
745/766, 179 classes (a fatia 3 só tocou o `core` nos componentes B2 e nas chaves persistidas). Contra a `main`, vale a nota da
fatia 2 (linhas físicas por formatação; branches e métodos idênticos).

## Verificação com o LLM real (T101) — **pendente**

Exige as chaves reais em `infra/.env` (preenchidas pelo usuário) e `sh infra/scripts/local.sh testes ia-real`. Não foi executada:
sem as chaves, nenhuma chamada real foi feita. O risco que esta etapa cobre é a saída real da IA seguir os esquemas com chaves
em inglês e os prompts migrados; os 9 testes de `LlmOpenAiCompatibleAdapterIT` e a paridade com respostas simuladas passam, mas só
a rodada real prova o comportamento dos modelos. **Fica como pendência do usuário antes de mesclar.**

## Prova do `AdapterRulesTest` (T100)

Branch descartável `scratch/adapter-rules-proof` (removida), uma violação por regra, `AdapterRulesTest` sobre o commit da fatia:
controle verde; os sete casos ficaram **vermelhos** — `restControllersEndWithController`, `portImplementationsEndWithAdapter`,
`webBoundaryObjectsEndWithDtoRequestOrResponse`, `internalChannelControllersEndWithController`,
`internalChannelBoundaryObjectsEndWithDtoRequestOrResponse` e `inboundDoesNotAccessOutboundDirectly` (esta pega os dois casos
de dependência inbound→outbound; a regra `internalServiceAreaDoesNotDependOnOutbound` é subconjunto dela e não dispara
sozinha, o que já era assim na `main`).

## Comparação final (T103)

Testes 727 → 738 (+11, todos novos da fatia), zero teste removido, cobertura do `core` igual à da fatia 2, `rename-check` com 0
em cada contador, guardiões verdes, `V1`–`V6` sem alteração. Ressalva honesta: a rodada completa das três suítes foi feita
na árvore imediatamente anterior ao commit; a única mudança posterior foi a chave citada na mensagem de `TelegramChannelConfig`
(e na asserção), seguida de rodada estreita (`TelegramChannelConfigTest`, `LanguageGuardTest`, `ArtifactsLanguageGuardIT`)
com as exclusões regeneradas. Se preferir, uma rodada completa sobre o commit é só `sh infra/scripts/local.sh testes todos`.
