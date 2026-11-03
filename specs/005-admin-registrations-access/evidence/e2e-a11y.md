# Evidência T222 — e2e com axe nas 15 telas (SC-012), SC-001 e SC-002

Execução final (S5): `docker compose -p reports-s5 -f infra/compose.test.yaml build backend-e2e web-e2e` e depois
`run --rm tests-e2e` (o equivalente de `sh infra/scripts/local.sh tests e2e` sem o `down -v` e sem o projeto fixo), com
a pilha `postgres-e2e`, `backend-e2e` e `web-e2e` reconstruída da branch `feat/005-s5-closing` (já com a fonte de ícones
nova). Chromium (Playwright 1.63), axe com as marcas `wcag2a`, `wcag2aa`,
`wcag21a`, `wcag21aa` e `wcag22aa`; cada tela é conferida em **claro e escuro** e em **390 e 1440 px**, com falha em
violação **séria ou crítica** e em rolagem horizontal.

## Resultado

| Item | Resultado |
|---|---|
| Corrida completa (Chromium, 1 worker) | **90 passaram, 0 falharam**, em 7,1 min (88 testes anteriores + 2 de `icons-render.spec.ts`) |
| Violações sérias/críticas de axe (A01 a A12, P01 a P03, claro/escuro, 390/1440 px) | **Nenhuma** |
| Rolagem horizontal a 390 px | **Nenhuma** (as 32 falhas da corrida anterior sumiram com a fonte nova) |
| SC-001 (administrador cadastra professor, copia o código e cadastra aluno associado) | **6,4 s e 18 interações** (limites: 180 s e 25), digitação a 40 ms por tecla |
| SC-002 (professora cadastra o primeiro aluno, do acesso ao detalhe) | **2,5 s** (limite: 180 s), CPF fora da URL e do armazenamento |
| Glifos da fonte de ícones (`icons-render.spec.ts`) | Os 59 nomes de `included-icons.txt` têm largura de cerca de 1em com a fonte carregada; o controle negativo (nome sem glifo) é largo |

Ajustes da revisão pré-PR nesta corrida: `test.setTimeout` de 240 s em SC-001 e SC-002 (o limite padrão de 90 s matava o teste
antes da asserção de 180 s), datas de "menor de idade" relativas a hoje (`minorBirthDate()`) e foco do diálogo de retirada
devolvido sem temporizador. A régua do axe continua sendo "sem violação séria ou crítica" (SC-012), como diz o cabeçalho de
`support.ts`. Uma primeira corrida desta rodada teve 89 passes e 1 falha em `accessibility.spec.ts` (390 px, tema claro): o
job simulado ainda processava quando a espera de 20 s acabou, com o backend de testes rodando em paralelo na mesma máquina; a
segunda corrida completa passou inteira, sem alteração de código entre as duas.

## Defeito de ícones: corrigido

A corrida anterior (56 passaram e 32 falharam) falhava só por rolagem horizontal a 390 px: o subconjunto da fonte de
ícones não tinha 14 ícones da feature 005, e o nome do ícone aparecia como texto. O subconjunto foi gerado de novo com
59 ícones (`included-icons.txt`, peso 300). Na conferência de todos os nomes usados em `frontend/src` apareceu mais um
fora da fonte, `pause_circle` (selo "Inativo" de `SituationBadge`), trocado por `pause`. O teste
`frontend/tests/icons-included.test.ts` agora reprova o build se algum ícone usado não estiver na lista.

## Defeitos reais achados e corrigidos

- `FirstAdministratorBootstrap` tinha dois construtores e nenhum `@Autowired`: o backend **não subia** fora dos perfis
  `seed` e `test` (`No default constructor found`). Corrigido com `@Autowired` no construtor público.
- `StudentAssociationsSection`: ao fechar (Esc ou Voltar) o diálogo de retirada, o foco **não voltava** ao botão
  "Retirar" (o botão fica desabilitado durante a prévia e perde o foco). Corrigido guardando o botão e devolvendo o foco.

## Infra do e2e

O administrador de teste é semeado por `frontend/tests/e2e/support.ts` (`seed()`): já ativado, com hash Argon2id de uma
senha aleatória gerada a cada execução (ou `E2E_ADMIN_PASSWORD`, se definida). Nenhuma senha ou hash em arquivo
versionado. A semeadura também limpa `authorship_record` (somente de acréscimo) suspendendo os gatilhos de usuário.
