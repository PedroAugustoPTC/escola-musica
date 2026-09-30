# Contrato: Portas de saída do núcleo e saídas da IA

**Feature**: `001-fluxo-relatorios` | Referências: Princípio I (portas sem nome de fornecedor), FR-017 a
FR-023, FR-025, FR-027a, FR-043, FR-044, FR-049, FR-056; [research.md](../research.md) R-07 a R-11.

As portas ficam em `nucleo` (pacote `...porta`) e são implementadas por `*Adaptador` em `aplicacao`.
As assinaturas abaixo são o contrato semântico. Os tipos são objetos do núcleo, nunca DTOs de
fornecedor. Todas as portas são substituíveis por dublês nos testes unitários (Princípio V).

## 1. `TranscricaoPorta`

```java
ResultadoTranscricao transcrever(AudioNormalizado audio);
// AudioNormalizado: caminho temporário (Ogg/Opus mono 16 kHz), duracaoSegundos, idioma = "pt"
// ResultadoTranscricao: texto, duracaoSegundos, custoEstimado, provedorModelo (opaco para o núcleo)
```

| Situação | Comportamento |
|---|---|
| Texto vazio ou só espaços | lança `AudioNaoProcessavelExcecao(SEM_FALA)` |
| Provedor recusa o arquivo (4xx de conteúdo) | `AudioNaoProcessavelExcecao(FORMATO)` |
| 429, 5xx, timeout ou rede | `ProvedorIndisponivelExcecao`; o worker tenta de novo 1 vez (R-08); depois, `AUDIO_NAO_PROCESSAVEL` para registro de aula e `FALHA_ALTERACAO` para pedido por áudio |

A porta **não** guarda o áudio. Quem apaga o arquivo temporário é o `AudioTemporarioServico`, num
`finally` (FR-018).

## 2. `NormalizacaoAudioPorta`

```java
AudioNormalizado normalizar(ArquivoTemporario original, long limiteBytes);
```

Extrai a trilha de áudio (inclusive de MP4), converte para Ogg/Opus mono 16 kHz 24 kbit/s e mede a
duração. Erros: `FormatoNaoSuportadoExcecao` quando não há trilha de áudio ou o contêiner é
desconhecido; `AudioNaoProcessavelExcecao(VAZIO)` quando a duração é menor que 1 s. Implementação:
FFmpeg (`FfmpegNormalizacaoAudioAdaptador`).

## 3. `LlmPorta`

```java
<T> RespostaLlm<T> gerarJson(TarefaLlm tarefa, EntradaTarefa entrada, Class<T> tipoSaida);
// TarefaLlm: ESTRUTURACAO_AULA, INCORPORACAO_RESPOSTAS, REVISAO_AULA, CONSOLIDACAO_SEMESTRAL, REVISAO_SEMESTRAL
// RespostaLlm: saida (já validada contra o schema), versaoPrompt, tokensEntrada, tokensSaida, custoEstimado
```

- O adaptador escolhe o prompt versionado da tarefa, chama o provedor em modo JSON, valida a saída
  contra o schema (seção 6) e, se ela for inválida, faz **uma** chamada de correção
  (`correcao-json.v1`). Se falhar de novo, lança `SaidaIaInvalidaExcecao`.
- Campos ausentes viram vazios e campos desconhecidos são descartados na desserialização (FR-020).
- **Minimização de dados** (FR-023, FR-044): `EntradaTarefa` só aceita os tipos abaixo. Nome, CPF,
  data de nascimento e nome do responsável do aluno não existem nesses tipos, então não há como
  enviá-los.

| Tarefa | Entrada permitida |
|---|---|
| `ESTRUTURACAO_AULA` | transcrição |
| `INCORPORACAO_RESPOSTAS` | conteúdo atual (5 seções), perguntas pendentes, texto da resposta, rodada atual |
| `REVISAO_AULA` | conteúdo atual (5 seções), texto do pedido |
| `CONSOLIDACAO_SEMESTRAL` | lista de conteúdos aprovados (5 seções cada) com a **data da aula**; quantidade de aulas; intervalo do período |
| `REVISAO_SEMESTRAL` | conteúdo atual (seções 2 a 10), texto do pedido |

## 4. `GeracaoPdfPorta`

```java
DocumentoGerado gerarRelatorioAula(ModeloPdfRelatorioAula modelo);
DocumentoGerado gerarRelatorioSemestral(ModeloPdfRelatorioSemestral modelo);
// modelo: cabeçalho (aluno, professor, data da aula ou período), seções, versão,
//         situacao = CONFERENCIA | OFICIAL, aprovacao (professor, data e hora, versão) quando OFICIAL,
//         quantidadeAulasConsideradas (semestral)
// DocumentoGerado: bytes, sha256, tamanho
```

O layout segue `portal-do-professor-design-system/project/Modelo PDF.dc.html` (FR-049): logotipo e
filete, título "Relatório de Aula" / "Relatório Semestral", rótulos legíveis com acentuação, "—"
para seções vazias, rodapé institucional "Instituto Musical Felipe Blaft · Página X de Y". Em
`CONFERENCIA`, a faixa "Documento em conferência — não oficial"; em `OFICIAL`, "Aprovado por
{professor} em {data} às {hora} · versão {n}".

## 5. Outras portas

| Porta | Operações | Adaptador inicial |
|---|---|---|
| `ArmazenamentoDocumentoPorta` | `salvar(chave, bytes)`, `abrir(chave) → InputStream`, `existe(chave)` | sistema de arquivos (volume `documentos`) |
| `RelogioPorta` | `agora()`, `hojeNoFusoDaEscola()` | relógio do sistema + `ESCOLA_FUSO_HORARIO` |
| `GeradorCodigoAcessoPorta` | `gerar() → CodigoAcesso` | `SecureRandom` |
| `ProtecaoDadosPorta` | `cifrarCpf`, `hashCpf`, `hashCodigoAcesso`, `hashOrigem` | AES-256-GCM / HMAC-SHA-256 |
| `NotificacaoProfessorPorta` | `notificarEtapa(trabalho, etapa)`, `notificarResultado(trabalho, resultado)`, `avisarResultadoEmOutroCanal(professor, resumo)` | `NotificacaoTelegramAdaptador` (a Web consulta por polling e não precisa de adaptador de envio) |
| `ArquivoCanalPorta` | `obterArquivo(referenciaArquivoCanal) → ArquivoTemporario` | `ArquivoTelegramAdaptador` (`getFile` no servidor local + cópia para o `tmpfs` + remoção do original) |
| `FilaTrabalhoPorta` | `enfileirar(trabalho)`, `reservarProximo()`, `concluir`, `falhar` | tabela `trabalho_processamento` (R-06) |
| `RegistroCustoPorta` | `registrar(ChamadaIa)` | tabela `chamada_ia` |
| Repositórios (`*Repositorio`) | um por agregado: `ProfessorRepositorio`, `AlunoRepositorio`, `AssociacaoRepositorio`, `SessaoWebRepositorio`, `VinculoTelegramRepositorio`, `TentativaAcessoRepositorio`, `RelatorioAulaRepositorio`, `RelatorioSemestralRepositorio`, `VersaoRelatorioRepositorio`, `InteracaoRelatorioRepositorio`, `DocumentoPdfRepositorio` | JPA/PostgreSQL |

## 6. Schemas de saída da IA (JSON Schema, draft 2020-12, resumidos)

### 6.1 `ESTRUTURACAO_AULA`, `INCORPORACAO_RESPOSTAS` e `REVISAO_AULA`

```json
{
  "type": "object",
  "required": ["conteudosTrabalhados", "evolucao", "dificuldades", "atividadesPropostas", "observacoes", "perguntasPendentes"],
  "properties": {
    "conteudosTrabalhados": { "type": "array", "items": { "type": "string", "maxLength": 500 }, "maxItems": 30 },
    "evolucao":             { "type": "string", "maxLength": 4000 },
    "dificuldades":         { "type": "array", "items": { "type": "string", "maxLength": 500 }, "maxItems": 30 },
    "atividadesPropostas":  { "type": "array", "items": { "type": "string", "maxLength": 500 }, "maxItems": 30 },
    "observacoes":          { "type": "string", "maxLength": 4000 },
    "perguntasPendentes":   { "type": "array", "items": { "type": "string", "maxLength": 300 }, "maxItems": 5 },
    "semAlteracaoDeConteudo": { "type": "boolean", "description": "só REVISAO_AULA" },
    "pedidoDeAcao":           { "enum": ["NENHUM", "APROVAR", "CANCELAR"], "description": "só REVISAO_AULA" }
  }
}
```

Regras do núcleo sobre a saída:

- `perguntasPendentes` só é considerada em `ESTRUTURACAO_AULA` e `INCORPORACAO_RESPOSTAS`, e só se
  `rodadas_perguntas < 2` (FR-025). Na 2ª rodada, as perguntas que sobrarem são ignoradas e o
  relatório segue para conferência.
- Em `REVISAO_AULA`, as perguntas são ignoradas. Se `pedidoDeAcao ≠ NENHUM` e
  `semAlteracaoDeConteudo = true`, o resultado é `PEDIDO_DE_ACAO_IGNORADO`, sem nova versão. Se
  `semAlteracaoDeConteudo = true` com `NENHUM`, o resultado é `SEM_ALTERACAO_DE_CONTEUDO`. O núcleo
  também compara o conteúdo: se ele for idêntico ao da versão vigente, nenhuma versão é criada.
  **Nenhum** valor de `pedidoDeAcao` aprova ou cancela (FR-027a).

### 6.2 `CONSOLIDACAO_SEMESTRAL` e `REVISAO_SEMESTRAL`

Objeto com as chaves `frequenciaEEstudo`, `tecnica`, `musicalidade`, `leituraEMemorizacao`,
`pontosDeAtencao`, `estrategiasPedagogicas`, `acompanhamentoFamiliar`,
`planejamentoProximoSemestre` (subcampos como em [data-model.md](../data-model.md), seção 9) e
`parecerFinal` (string, até 6000 caracteres), mais `semAlteracaoDeConteudo`/`pedidoDeAcao` na
revisão. **Sem** `informacoesGerais`: se vier, a chave é descartada e o sistema preenche essa seção
(FR-044).

## 7. Prompts versionados

| Arquivo | Tarefa | Pontos obrigatórios do texto |
|---|---|---|
| `prompts/estruturacao-aula.v1.txt` | estruturar a transcrição | português com acentuação; tom pedagógico e institucional; **não inventar**; o que faltar fica vazio ou vira pergunta objetiva; no máximo 5 perguntas |
| `prompts/incorporacao-respostas.v1.txt` | incorporar respostas | distribuir cada resposta nos campos certos (não só em observações); manter o que já estava correto; gerar novas perguntas só para lacunas relevantes que continuam abertas |
| `prompts/revisao-aula.v1.txt` | aplicar pedido | alterar só o pedido; preservar o resto; sinalizar `semAlteracaoDeConteudo` e `pedidoDeAcao` |
| `prompts/consolidacao-semestral.v1.txt` | consolidar | basear-se **exclusivamente** nos relatórios fornecidos; usar as datas para frequência; seções sem base ficam vazias |
| `prompts/revisao-semestral.v1.txt` | aplicar pedido no semestral | como a revisão de aula; nunca produzir Informações Gerais |
| `prompts/correcao-json.v1.txt` | corrigir JSON | devolver apenas JSON válido no mesmo schema |

Mudar o texto de um prompt exige um novo arquivo (`.v2`) e testes de regressão com as gravações
fictícias de `assets/` (suíte `ia-real`).
