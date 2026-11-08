# Contrato: configuração (FR-001 a FR-003, FR-052, FR-055)

Variáveis de ambiente lidas pelo backend em `application.yaml` (`reports.*`). Nomes em inglês; documentadas em
`infra/.env.example` (sem valor real) e em `infra/README.md`. O adaptador entrega os valores **brutos** ao núcleo
(`RetentionPolicy.from`, `AlertThresholds.from`, `PriceTable.from`), que decide o efetivo: ausente → padrão; zero,
negativo ou inválido → padrão + aviso `WARN` no log citando a **variável** (nunca recusa a subida).

## Retenção e guarda

| Variável | Propriedade | Padrão | Unidade |
|---|---|---|---|
| `RETENTION_TRANSCRIPTION_DAYS` | `reports.retention.transcription-days` | 30 | dias após aprovar ou cancelar |
| `RETENTION_UNAPPROVED_PDF_DAYS` | `reports.retention.unapproved-pdf-days` | 30 | dias após aprovar |
| `RETENTION_CANCELLED_REPORT_DAYS` | `reports.retention.cancelled-report-days` | 90 | dias após cancelar |
| `RETENTION_AUDIT_TRAIL_DAYS` | `reports.retention.audit-trail-days` | 1826 (5 anos) | dias; piso fixo de 365 no banco |
| `RETENTION_RUN_HISTORY_DAYS` | `reports.retention.run-history-days` | igual à trilha | dias |
| `RETENTION_CRON` | `reports.retention.cron` | `0 41 3 * * *` | cron Spring, fuso da escola |

## Responsável pela LGPD (D11)

| Variável | Propriedade | Padrão |
|---|---|---|
| `PRIVACY_OFFICER_NAME` | `reports.privacy.officer-name` | vazio → telas mostram "a definir pela escola" e a subida avisa |
| `PRIVACY_OFFICER_CONTACT` | `reports.privacy.officer-contact` | idem |

## Alertas

| Variável | Propriedade | Padrão |
|---|---|---|
| `ALERT_PROVIDER_CONSECUTIVE_FAILURES` | `reports.alerts.provider-consecutive-failures` | 3 |
| `ALERT_PROVIDER_FAILURE_RATE_PERCENT` | `reports.alerts.provider-failure-rate-percent` | 20 |
| `ALERT_PROVIDER_WINDOW_MINUTES` | `reports.alerts.provider-window-minutes` | 15 |
| `ALERT_QUEUE_MAX_AGE_MINUTES` | `reports.alerts.queue-max-age-minutes` | 5 |
| `ALERT_QUEUE_MAX_ITEMS` | `reports.alerts.queue-max-items` | 50 |
| `ALERT_STORAGE_QUIET_MINUTES` | `reports.alerts.storage-quiet-minutes` | 15 (normalização) |
| `ALERT_EVALUATION_SECONDS` | `reports.alerts.evaluation-seconds` | 60 |

## Custos

`reports.costs.prices` é uma lista (YAML ou variáveis indexadas `COSTS_PRICES_0_PROVIDER`, …):

| Campo | Exemplo | Regra |
|---|---|---|
| `provider` | identificador configurado do provedor (o mesmo gravado em `ai_call.provider_model`) | obrigatório |
| `unit` | `AUDIO_HOUR`, `INPUT_MILLION_TOKENS`, `OUTPUT_MILLION_TOKENS` | obrigatório |
| `value-usd` | `0.36` | > 0 |
| `effective-from` | `2026-11-01` | data; o preço vale da data até a próxima entrada do mesmo provedor e unidade |

Lista vazia → as propriedades atuais (`priceUsdPerHour`, `priceUsdPerMillionInput`, …) viram entradas com
`effective-from = 1970-01-01` (compatibilidade). `COSTS_INFRASTRUCTURE_MONTHLY_USD` (padrão 0) e
`COSTS_INFRASTRUCTURE_EFFECTIVE_FROM` dão o custo fixo mensal.

## Banco (R-03)

| Variável | Uso |
|---|---|
| `DB_USER` / `DB_PASSWORD` | passam a ser usadas **só** pelo Flyway (`spring.flyway.user/password`) |
| `DB_APP_USER` (padrão `reports_app`) / `DB_APP_PASSWORD` | pool da aplicação (`spring.datasource.*`); a senha é aplicada ao papel por `infra/scripts/lib/db-roles.sh` |

Segredos só no `.env` local (fora do Git) e no ambiente da VPS; nunca na imagem.

## Logs

`docker compose` aplica a todos os serviços (`x-logging` em `compose.yaml`): driver `json-file`, `max-size: 20m`,
`max-file: 5`. O backend inclui `correlationId` no MDC.
