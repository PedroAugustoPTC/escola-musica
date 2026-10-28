# Evidência T222 — e2e com axe nas 15 telas (SC-012), SC-001 e SC-002

Execução: `docker compose -p reports-x2 -f infra/compose.test.yaml run --rm --no-deps tests-e2e` (o equivalente de
`sh infra/scripts/local.sh tests e2e` sem o `down -v` e sem o projeto fixo), com a pilha `postgres-e2e`, `backend-e2e` e
`web-e2e` construída da branch `feat/005-s5-x2`. Chromium (Playwright 1.63), axe com as marcas `wcag2a`, `wcag2aa`,
`wcag21a`, `wcag21aa` e `wcag22aa`; cada tela é conferida em **claro e escuro** e em **390 e 1440 px**, com falha em
violação **séria ou crítica** e em rolagem horizontal.

## Resultado

| Item | Resultado |
|---|---|
| Violações sérias/críticas de axe (A01 a A12, P01 a P03, claro/escuro, 390/1440 px) | **Nenhuma** achada |
| SC-001 (administrador cadastra professor, copia o código e cadastra aluno associado) | **6,2 s e 18 interações** (limites: 180 s e 25), digitação a 40 ms por tecla |
| SC-002 (professora cadastra o primeiro aluno, do acesso ao detalhe) | **2,4 s** (limite: 180 s), CPF fora da URL e do armazenamento |
| Corrida completa, como está commitado | 56 passaram e 32 falharam; **todas as 32 falhas são rolagem horizontal a 390 px** (ver abaixo), nenhuma de axe |
| Mesmas telas com o recorte temporário do glifo (ver abaixo) | todas passam: 69 testes novos (rodadas por arquivo, após as correções de teste) e as telas do professor |

## Defeito aberto: o subconjunto da fonte de ícones não tem os ícones da feature 005

`frontend/src/styles/fonts/material-symbols-outlined.woff2` é um **subconjunto** (lista em `included-icons.txt`). A
feature 005 usa 14 ícones que não estão nele; sem o glifo, a ligadura não acontece e o **nome do ícone aparece como
texto** (`SHIELD_PERSON`, `SCHOOL`, `ADMIN_PANEL_SETTINGS`…), o que alarga a página e gera rolagem horizontal a 390 px em
toda tela administrativa. Ícones que faltam: `admin_panel_settings`, `content_copy`, `group_off`, `key`, `link`,
`link_off`, `person_add`, `person_check`, `person_off`, `person_remove`, `school`, `shield_person`, `timer` e
`verified_user`.

Correção (não feita aqui): acrescentar esses nomes a `included-icons.txt` e gerar de novo o `woff2` com o parâmetro
`icon_names` da API do Google Fonts (peso 300), como diz o cabeçalho de `fonts.css`. Isso exige baixar o arquivo da
Google Fonts, o que não foi feito sem autorização. Para provar que o resto está limpo, a pilha foi reconstruída com um
recorte temporário (`.icon { width: 1.2em; overflow: hidden; }`, **não commitado**), e as telas passaram.

## Defeitos reais achados e corrigidos

- `FirstAdministratorBootstrap` tinha dois construtores e nenhum `@Autowired`: o backend **não subia** fora dos perfis
  `seed` e `test` (`No default constructor found`). Corrigido com `@Autowired` no construtor público.
- `StudentAssociationsSection`: ao fechar (Esc ou Voltar) o diálogo de retirada, o foco **não voltava** ao botão
  "Retirar" (o botão fica desabilitado durante a prévia e perde o foco). Corrigido guardando o botão e devolvendo o foco.

## Infra do e2e

O administrador de teste é semeado por `frontend/tests/e2e/support.ts` (`seed()`): já ativado, com hash Argon2id de uma
senha aleatória gerada a cada execução (ou `E2E_ADMIN_PASSWORD`, se definida). Nenhuma senha ou hash em arquivo
versionado. A semeadura também limpa `authorship_record` (somente de acréscimo) suspendendo os gatilhos de usuário.
