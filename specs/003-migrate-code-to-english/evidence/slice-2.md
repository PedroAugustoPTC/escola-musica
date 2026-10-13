# Evidência — Fatia 2: `nucleo` → `core`

Registro das tasks T024 a T050 (`tasks.md`). Nenhum valor de texto, CPF, nome de aluno, chave ou token é
registrado aqui; só contagens, nomes de arquivos e resultados.

## Linha de base (T024)

Árvore: commit da fatia 1 (`2243d8b`) mais o commit só de documentos `6307b11` (fase 8 do `tasks.md`).
Nenhum arquivo de código mudou desde a rodada de comparação da fatia 1 (T023), então a linha de base da
fatia 2 **é o resultado final da fatia 1** (`evidence/slice-1.md`, seção "Comparação"), sem rodar de novo as
suítes sobre uma árvore idêntica.

| Suíte | Testes | Resultado |
|---|---|---|
| `backend` — `nucleo` (Surefire) | 324 | verde |
| `backend` — `aplicacao` unitários | 79 | verde |
| `backend` — Failsafe padrão | 253 (1 ignorado) | verde |
| `backend` — Failsafe `transporte-interno` | 71 | verde |
| `backend` — total | 727 | verde |
| `frontend` | 77 (13 arquivos) | verde |
| `e2e` | 19 | verde |

Cobertura do `nucleo` na linha de base: linhas 3243/3304, branches 806/875 (`evidence/slice-1-jacoco.csv`).
Guardiões verdes, com 1.100 exclusões temporárias.

## Spike do OpenRewrite com Java 25 (T025)

Ambiente: worktree descartável (`git worktree add ../spike-rewrite HEAD`, já removido), contêiner
`maven:3.9-eclipse-temurin-25`, plugin `org.openrewrite.maven:rewrite-maven-plugin:6.46.1` passado por linha de
comando (nenhum `pom.xml` alterado), módulo `nucleo`, pacote `dominio.cadastro` (contém `record` e `enum`
aninhados). Uma rodada leva ~2 min.

| Operação | Receita | Resultado |
|---|---|---|
| Pacote (move arquivos e atualiza `package`/`import`) | `ChangePackage` | **passa** (42 arquivos, main e test) |
| Tipo, inclusive `record`/`enum` e uso em genéricos | `ChangeType` | **passa** |
| Tipo aninhado (`BuscaAluno.TipoResultado`) | `ChangeType` com nome binário `Externo$Aninhado` | **passa**, mas **só** com o nome binário; sem ele o `import` fica no nome antigo e a compilação quebra |
| Método declarado | `ChangeMethodName` | **passa** (declaração e chamadas) |
| Acessor implícito de `record` | `ChangeMethodName` | **não renomeia** (não há declaração) |
| Campo, parâmetro, variável local, componente de `record` | `ChangeFieldName` | **não existe como receita declarativa** (`Recipe class … cannot be found`) |
| Arquivo de teste ao renomear o pacote | — | o arquivo muda de pasta, mas a classe `*Teste` só muda de nome com `ChangeType` próprio |

Conclusão: o OpenRewrite **passa** para pacotes, tipos (inclusive aninhados e `sealed`) e métodos, e **falha**
para campos, parâmetros, variáveis locais e componentes de `record`. O plano previa, nesse caso, o fallback
da IDE pacote a pacote; esta sessão não tem IDE, então o desvio é este (a confirmar com o revisor):

- pacotes: receita declarativa `ChangePackage`, gerada da tabela por pacote inteiro (`tools/rewrite/slice-2.yml`);
- tipos, métodos, campos, parâmetros, variáveis e componentes de `record`: **uma receita Java própria** sobre a API
  do OpenRewrite, que só lê o mapa de identificadores e a lista de retenção (nenhuma regra nova de negócio),
  compilada em contêiner e instalada no cache do Maven (fica em `tools/rewrite/recipes/`, fora do build do
  produto, como evidência da migração). O `ChangeType` foi descartado (ver "Decisões", item 1).

O resultado passa pelo `rename-check` (T046).

## Decisões de projeto e desvios (para o revisor)

1. **Receita Java própria no lugar do `ChangeType` e do fallback da IDE.** O spike mostrou que o OpenRewrite não tem
   receita para campos, parâmetros, variáveis e componentes de `record`; uma segunda investigação mostrou que o
   `ChangeType` também falha em tipos aninhados (qualifica as referências, quebra o construtor compacto de `record` e
   não reescreve o `import` de tipo aninhado quando só o externo é renomeado). A receita
   `tools/rewrite/recipes/…/RenameMembersByMap.java` renomeia **tipos, métodos, campos, parâmetros, variáveis e
   componentes de `record`** do `core` e as referências a eles nos dois módulos, guiada pelo mapa
   (`tools/maps/slice-2.json`) e pela retenção (`tools/held-back/slice-2.json`); só o `ChangePackage` é declarativo.
   Não há IDE nesta sessão, então o "fallback da IDE" do plano não se aplica. Resultado e prova: `rename-check`.
2. **As três receitas (T036, T037, T038) rodam numa execução só** (`Slice2.All`): o plugin do OpenRewrite roda a receita
   uma vez, ao fim do reator, sobre os dois módulos (~10 min por execução). A separação em três commits internos não
   traz prova a mais, e `rename-check` + compilação + as três suítes validam o conjunto.
3. **Mapa de identificadores revisado à mão** (`tools/maps/slice-2.review.*.json`, 1.651 entradas): o gerador só
   sugere; nomes de teste em frase, plurais, `Por`/`Com` soltos e palavras reservadas do Java (`novo`→`new`,
   `padrao`→`default`) foram corrigidos item a item. Por papel (método × variável), `explicit-pairs.json` dá nomes
   diferentes ao mesmo nome antigo (`novo`: método `create`, variável `created`); por tipo dono, `qualifiedMethods`
   (`StatusRelatorio#emAberto` → `isOpen`). `entrada`/`saida` de dados viram `input`/`output`; `inbound`/`outbound`
   ficam para os pacotes de adaptadores (fatia 3).
4. **Constantes de enumeração (B1) e componentes de `record` lidos pelo Jackson (B2) ficam em português** nesta
   fatia: 27 enums / 147 constantes e 21 `record` / 94 componentes (`tools/held-back/slice-2.json`). Os 84 literais de
   uma palavra (B3) não são tocados: a receita nunca altera literais. O nome do PDF exibido (`relatorio-aula-…`) e a
   palavra-chave `sair` são exceções permanentes.
5. **Pacote `arquitetura`**: existe nos dois módulos (regras do `core` e guardiões da `aplicacao`); a tabela por
   pacote inteiro (`tools/rewrite/slice-2-packages.json`) o leva a `architecture` nos dois. Os demais pacotes da
   `aplicacao` só trocam a base (`relatorios` → `reports`).
6. **Árvore-base da fatia 2 = commit da fatia 1** (e não a `main`): a `aplicacao` ganhou os guardiões na fatia 1.
   `rename-check` e `gen-recipe` recebem essa árvore (`git archive HEAD backend`).
7. **Ordem da receita: tipos e membros primeiro, `ChangePackage` depois.** Depois do `ChangePackage` o OpenRewrite
   perde os supertipos e os métodos dos tipos analisados, e a receita deixava sem renomear o método de uma classe
   de fora do núcleo que implementa uma interface do núcleo, e o corpo do construtor compacto de `record`. Com a
   receita própria primeiro (tipos íntegros) e o `ChangePackage` por último, os nomes novos sobrevivem. A receita
   reconhece os pacotes do `core` por nome antigo **e** novo (`corePackages`).
8. **Edições que a receita não faz** (`tools/rewrite/post-edits.mjs`, dirigido por `explicit-pairs.json`, com
   arquivo, trecho e motivo; entrada que não casa é erro): 16 literais de pacote e de sufixo nas regras ArchUnit
   do `core` e da `aplicacao` e no decorador de transação (que casa casos de uso pelo nome da classe), a troca
   `startsWith("Entrada")` → `endsWith("Input")` (o prefixo virou sufixo, mesma exclusão), e 2 textos do
   `logback-spring.xml`. O `rename-check` aceita **só** essas edições (25 ocorrências).
9. **Colisões**: o verificador só acusa dois nomes antigos *declarados* com o mesmo papel (tipo, método ou
   variável) no mesmo arquivo. Duas eram reais e foram corrigidas no mapa (`aceite`/`aceito` e `aberto`/`emAberto`
   davam auto-sombreamento); três nomes de variável foram diferenciados (`aprovadas`, `programadas`, `vincular`).
   Restam 7 colisões revisadas e aceitas (escopos distintos, e a sobrecarga legal `data(String)`/`data()`).
10. **Exclusões do guardião**: o `gen-exclusions.mjs` ganhou `--remap` (arquivos que mudaram de caminho) e
    `--migrated-prefix` (na área migrada só sobram literais e nomes retidos; qualquer outro identificador é erro).
    Resultado: 838 arquivos (dona: fatias 3, 5 e 6) e 1.185 termos retidos no `core` (dona: fatia 4). O
    `backend/pom.xml` passou a ser da fatia 3 (`<module>aplicacao</module>` e o `**/*Teste.java` do Surefire).

## Entregas (T024 a T046)

| Tarefa | Entrega |
|---|---|
| T025 | spike do OpenRewrite (acima) |
| T026 | `tools/held-back/gen-slice-2.mjs` → `slice-2.json` (B1 27 enums/147 constantes; B2 21 `record`/94 componentes; B3 84 literais; 6 literais permanentes) |
| T027 a T031 | `tools/lib/{java-lexer,java-types,align,rename-map}.mjs`, `tools/rename-check.mjs` e `tools/test/rename-check.test.mjs` (15 testes; 27 com os do gerador, no `node:24-alpine`) |
| T032 | `tools/maps/slice-2.json` (1.652 entradas, revisadas à mão em `slice-2.review.*.json`) e `tools/explicit-pairs.json` |
| T033 | `tools/rewrite/{gen-recipe.mjs,run-recipe.sh,slice-2.yml}` e a receita Java em `tools/rewrite/recipes/` |
| T034, T035 | `git mv backend/nucleo backend/core`; `pom.xml` (grupo `…reports`, artefato `reports`/`core`, regra do enforcer, ids do JaCoCo), `Dockerfile` e caminho do JaCoCo no `ci.yml` (nome do artefato `jacoco-nucleo` fica para a fatia 6) |
| T036 a T038 | uma execução de `Slice2.All`: 44 pacotes, 214 tipos de primeiro nível mais os aninhados, 1.436 pares aplicados em 449 arquivos |
| T039, T041 | `post-edits.mjs` (decisão 8); grep do pacote antigo em código: zero (restam só os `CLAUDE.md`, da fatia 7) |
| T042 | `doc-identifiers.mjs` aplicado em `specs/001-fluxo-relatorios/contracts/portas-provedores.md` (82 trocas, só em código; prosa, constantes, chaves retidas e o volume `documentos` ficam) |
| T043 | `V1`–`V6` sem alteração (`git status` vazio em `db/migration`) |
| T044 | exclusões regeneradas (decisão 10) |
| T046 | `evidence/slice-2-rename-check.txt`: 0 pares fora do mapa, 0 literais de texto alterados, 0 arquivos sem par, 0 colisões |

## Comparação (T045, T047, T050)

| Suíte | Linha de base (fatia 1) | Fatia 2 | Veredito |
|---|---|---|---|
| `backend` — `core` (Surefire) | 324 | 324 | igual |
| `backend` — `aplicacao` unitários | 79 | 79 | igual |
| `backend` — Failsafe padrão | 253 (1 ignorado) | 253 (1 ignorado) | igual |
| `backend` — Failsafe `transporte-interno` | 71 | 71 | igual |
| `backend` — total | 727 | 727 | verde (`BUILD SUCCESS`, 11 min) |
| `frontend` | 77 (13 arquivos) | 77 (13 arquivos) | verde |
| `e2e` | 19 | 19 | verde (2,4 min) |

Nenhuma expectativa de texto de teste foi alterada (nenhum literal de texto mudou: `rename-check`).

**Cobertura do `core`** (`evidence/slice-2-jacoco.csv` contra a `main` medida em 2026-10-11):

| Métrica | `main` | Fatia 2 |
|---|---|---|
| instruções | 14.075/14.297 (98,45 %) | 14.075/14.297 (98,45 %) |
| branches | 806/875 (92,11 %) | 806/875 (92,11 %) |
| métodos | 745/766 (97,26 %) | 745/766 (97,26 %) |
| classes | 179 | 179 |
| linhas | 3.243/3.304 (98,15 %) | 3.176/3.237 (**98,12 %**) |

A cobertura de **comportamento** é idêntica (instruções, branches, métodos, classes e as mesmas 61 linhas não
cobertas). Só o percentual de **linhas físicas** caiu 0,03 ponto, porque o Spotless, com nomes mais curtos, juntou
linhas (o JaCoCo conta linhas de arquivo: 67 a menos nos dois lados da fração). É um efeito de formatação, não de
perda de cobertura; o piso de 90 % segue folgado. **Pedido de decisão**: a regra de comparação diz "linha ≥ linha de
base"; para as próximas fatias, sugiro comparar instruções, branches e **linhas não cobertas** (formatação não as
altera), o que preciso que o revisor confirme.

## Anotações (bug ou código morto: anotado, não corrigido)

- `RelatorioAula.criar`/`RelatorioSemestral.criar` e `GeradorId.novo` viram `create`/`GeneratorId.create` (o nome
  `novo` era a fábrica do identificador): leitura estranha em inglês, mas fiel ao original.
- Textos de `toString` dos tipos (`"RelatorioAula[id="`, `"CodigoAcesso[***]"`, …) citam o nome antigo da classe: são
  texto de depuração, não foram tocados (a receita nunca altera literais) e ficam como termos retidos (dono: fatia 4).
  O mesmo vale para ~80 literais de **dados de teste** (e-mails `…@exemplo.test`, JSON `{"texto":…}`, caminhos
  `relatorios/aula/…`, chaves `p:resultado:x`). **Decisão em aberto para a fatia 4/7**: migrá-los (muda dado de
  teste) ou ampliar o glossário com uma exceção documentada de "dado de teste", que precisa de categoria na emenda.
- `aceite()` e o componente `aceito` de `ResultadoEntrega` já eram quase sinônimos (`accept()` × `accepted`).
- `backend/core/CLAUDE.md` e `backend/CLAUDE.md` ainda citam `nucleo`/`relatorios` (revisão na fatia 7, como manda a T034).
- `infra/README.md` cita `nucleo` (fatia 7).
- O Docker Desktop caiu no meio da fatia por *sockets* de runtime velhos (`sailor-ingest.sock`,
  `docker-secrets-engine`); contornado renomeando as pastas `run` e `docker-secrets-engine` de
  `%LOCALAPPDATA%` (sufixo `.stale-20261012`, reversível). Nada do projeto foi afetado.

## Prova de que as regras do Princípio I seguem barrando (T048)

Branch descartável `scratch/arch-proof` (já removida), uma violação por vez, numa classe nova; cada execução rodou a
classe de regras do módulo (`ArchitectureRulesTest` no `core`; `RegrasAdaptadoresTeste` no `core,aplicacao`), não a
suíte inteira (11 min por execução seria 2 h; a regra que barra é a mesma). Sem violação (controle): verde.

| Violação provocada | Regra que reprovou |
|---|---|
| domínio → caso de uso / serviço / porta (3 casos) | `domainDoesNotDependOnUseCasesServicesNorPorts` |
| porta → caso de uso / serviço (2 casos) | `portsDoNotDependOnUseCasesNorServices` |
| serviço → caso de uso | `servicesDoNotDependOnUseCases` |
| classe de caso de uso sem o sufixo `UseCase` | `useCasesEndWithUseCase` |
| classe de serviço sem o sufixo `Service` | `servicesEndWithService` |
| interface de porta sem `Port`/`Repository` | `portsEndWithPortOrRepository` |
| classe do núcleo com nome de fornecedor (`Openai…`) | `coreDoesNotMentionVendors` |
| `@RestController` sem o sufixo `Controlador` (aplicacao) | `controladoresRestTerminamEmControlador` |
| entrada web acessando a saída diretamente (aplicacao) | `entradaNaoAcessaSaidaDiretamente` |

Não provocável: `coreDoesNotDependOnFrameworks` (o `core` não tem Spring, Jakarta nem Jackson no classpath, então não
há como compilar a violação; quem barra isso é a regra do enforcer `core-without-dependencies`, que continua ativa).
