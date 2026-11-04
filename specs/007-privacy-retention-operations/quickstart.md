# Quickstart: validar a feature 007 (privacidade, retenção e operação)

Roteiro de validação ponta a ponta, por história. Detalhes de rotas e campos nos [contratos](./contracts/); tabelas em
[data-model.md](./data-model.md). Tudo roda em contêiner; a máquina só precisa de Docker.

## Pré-requisitos

1. Branch `feat/007-privacy-retention-operations` com a `V17` aplicada.
2. `.env` local com as variáveis novas ([configuration.md](./contracts/configuration.md)): `DB_APP_PASSWORD` (qualquer
   valor local), `PRIVACY_OFFICER_NAME`, `PRIVACY_OFFICER_CONTACT` e, para acelerar o ensaio,
   `RETENTION_CRON=0 */2 * * * *` (a cada 2 min).
3. Subir o ambiente:

```bash
sh infra/scripts/local.sh up
```

O passo `db-roles` do `local.sh` deve imprimir que o papel `reports_app` está com `LOGIN`. O backend sobe com o pool em
`reports_app` e o Flyway em `reports`.

4. Massa de demonstração (fictícia) e um aluno menor com consentimento:

```bash
sh infra/scripts/local.sh demo-data
```

## Suítes automatizadas

```bash
sh infra/scripts/local.sh tests backend
```

```bash
sh infra/scripts/local.sh tests frontend
```

```bash
sh infra/scripts/local.sh tests e2e
```

Esperado: tudo verde, cobertura do núcleo ≥ 90% (linha e branch). Testes que provam os critérios (research R-13):
`PrivacyCanaryIT` (SC-001), `AuditEventCoverageIT` (SC-002), `RetentionIT` (SC-003), `ErasureIT` (SC-004),
guardas de consentimento nos testes de paridade (SC-005), `OperationalAlertIT` (SC-006), `CostReportIT` (SC-008),
acesso cruzado gerado da matriz (SC-009), `AuditTrailTamperIT` (proteção da trilha).

## Roteiro manual

### US1 — Trilha (P1)

1. Entrar como professor no Telegram (simulador local) e baixar o PDF de um relatório aprovado.
2. Na Web, como administrador, abrir **A14** (`/admin/audit`), filtrar pelo aluno: aparece `REPORT_DOCUMENT_SENT` com
   canal Telegram, versão e o professor como ator; nenhum nome de aluno gravado (o rótulo vem da exibição).
3. Errar o código de acesso uma vez → filtrar "falha de entrada": evento com canal e resultado, sem o código.
4. A própria consulta aparece como `AUDIT_TRAIL_QUERY`.
5. Proteção: no contêiner do banco, como `reports_app`, tentar
   `UPDATE authorship_record SET result = 'SUCCESS' WHERE false;` → **permissão negada**. Como `reports` (dono),
   alterar uma linha de teste e abrir A14 → "Verificar agora": selo **Quebrada** e alerta `AUDIT_CHAIN_BROKEN` em A18.
   (Só em ambiente local; recrie o banco depois com `local.sh clean`.)
6. Telegram: digitar "auditoria" → negativa neutra.

### US2 — Consentimento (P1)

1. Web do professor: cadastrar aluno de 12 anos sem a seção de consentimento → recusado (`VALIDATION_FAILED` nos campos
   `consent.*`). Repetir no Telegram (cadastro guiado): após a data de nascimento aparece o passo C1; responder "não"
   → cadastro não concluído.
2. Cadastrar com consentimento nos dois canais → aceito; evento `CONSENT_RECORD` com o canal certo.
3. Administrador revoga o consentimento (A16). Professor envia áudio pela Web e pelo Telegram → aviso de bloqueio igual
   nos dois; nos logs do provedor simulado, **nenhuma** chamada; evento `CONSENT_BLOCKED_AI`.
4. Professor registra novo consentimento pelo botão do aviso → reenviar o áudio segue o fluxo normal.
5. Publicar versão nova do termo com "exige novo consentimento" (A16d) → o aluno aparece em **A16c** com motivo
   `SUPERSEDED`.
6. Aluno adulto: registrar aula sem consentimento → sem bloqueio.
7. Carga da implantação: gerar um CSV fictício fora do repositório com ids de A16c e rodar

```bash
sh infra/scripts/local.sh consent-load /tmp/consentimentos-ficticios.csv
```

   → resumo com aceitas/recusadas (só número de linha e código) e "0 alunos menores pendentes".

### US3 — Retenção (P2)

Validação principal é o `RetentionIT` (relógio avançado). Ensaio local com prazos curtos:

1. `RETENTION_TRANSCRIPTION_DAYS=1`, `RETENTION_UNAPPROVED_PDF_DAYS=1`, `RETENTION_CANCELLED_REPORT_DAYS=1` e a massa de
   demonstração com datas antigas (o `demo-data` cria relatórios aprovados há 40 dias, com versões 1–3, e cancelados há
   100 dias).
2. Aguardar a execução (cron de 2 min) e abrir A14 filtrando `RETENTION_RUN`: quantidades por tipo.
3. O relatório aprovado continua com conteúdo e PDF oficial no histórico (Web e Telegram); os PDFs das versões 1 e 2
   não existem mais no volume de armazenamento; o cancelado mostra só datas, situação e autoria.
4. Prazo `0` ou `abc` em qualquer variável → subida normal com `WARN` citando a variável; A18 mostra o valor efetivo
   como "padrão".
5. Parar o contêiner de armazenamento durante uma execução (ou usar o dublê de falha no IT) → `RETENTION_FAILED`
   ativo em A18; a execução seguinte retoma e normaliza.

### US4 — Eliminação (P2)

1. Aluno fictício com relatórios aprovados, cancelados, um em aberto e um semestral, associado a dois professores.
2. A5 → "Eliminar dados" → registrar pedido (A15) → prévia com o alcance → digitar `ELIMINAR` → concluído; comprovante
   imprimível (A15b) sem dado do aluno.
3. Buscar pelo nome e pelo CPF antigos (Web do administrador, Web dos dois professores, Telegram) → nada encontrado;
   cadastrar novo aluno com o mesmo CPF → aceito.
4. `ErasureIT` cobre a varredura do banco, do armazenamento e dos logs (SC-004); a trilha mostra os eventos antigos
   com "Aluno removido · #XXXXXX".
5. Telegram: "apagar aluno" → negativa neutra.

### US5 — Custo e alertas (P3)

1. Processar aulas e um semestral com o provedor simulado (custo conhecido, pela Web e pelo Telegram).
2. A17 do mês corrente: totais e médias por áudio, aula, aluno, semestral, canal (WEB/TELEGRAM) e provedor; total =
   soma das chamadas + `COSTS_INFRASTRUCTURE_MONTHLY_USD`.
3. Configurar o provedor simulado para falhar 3 vezes → em até 2–5 min, alerta `AI_PROVIDER_FAILING` em A18 e o
   indicador no cabeçalho; nova falha atualiza o mesmo alerta (ocorrências +1); voltar ao normal → "normalizado".
4. Parar o n8n e gerar mais de 50 ações (ou esperar 5 min com ações pendentes) → `CHANNEL_QUEUE_BACKLOG`; religar →
   normaliza.

### US6 — Inventário (P3)

1. Ler `privacy/processing-inventory.md` e conferir, provedor a provedor, contra os adaptadores em
   `backend/application/.../outbound/ai/` e os testes de contrato de IA: nada enviado que não esteja listado; nome,
   CPF, data de nascimento e dados do responsável declarados como **não** enviados.
2. Abrir um PR de teste que altera um prompt: o modelo de PR mostra o item do inventário e o `privacy-auditor` acusa
   a falta de atualização.

## Produção (implantação)

1. Definir `DB_APP_PASSWORD` e as variáveis de privacidade no ambiente da VPS.
2. `deploy.sh` aplica a imagem, roda o Flyway com `reports` e o passo `db-roles` antes de subir o backend com
   `reports_app`.
3. Carga de consentimentos (FR-036) e conferência de A16c **vazia** antes de liberar o uso com dados reais.
4. Conferir em A18 que não há alerta ativo e os prazos efetivos esperados.
