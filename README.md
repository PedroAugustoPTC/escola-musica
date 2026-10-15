# escola-musica

Sistema de Relatórios Pedagógicos por Voz do Instituto Musical Felipe Blaft: o professor registra a aula
por áudio (Portal ou Telegram), a IA estrutura o relatório, o professor confere e aprova, e o sistema gera
o PDF oficial (aula e semestral).

## Estrutura

| Pasta | O que é |
|---|---|
| `backend/` | API em Java/Spring: `core/` (regra de negócio) e `application/` (adaptadores, migrações, `Dockerfile`) |
| `frontend/` | Portal do Professor (SPA React + TypeScript) |
| `infra/` | Contêineres, ambiente local, proxy HTTPS e implantação (`seed/` guarda as cargas de cadastro fictícias) |
| `specs/` | Specs, planos, contratos e tarefas de cada feature (Spec Kit) |
| `.specify/` | Constituição (`memory/constitution.md`), glossário do guardião de idioma e templates |
| `.github/` | CI (`ci.yml`) e implantação (`deploy.yml`) |

Cada pasta tem o seu `CLAUDE.md` com as regras locais. As regras invioláveis estão na constituição.

## Rodar localmente

Só é preciso Docker (Engine 27+ com Compose v2). Na raiz do repositório, no Git Bash se estiver no Windows:

```bash
cp infra/.env.example infra/.env
```

Preencha os segredos do `infra/.env` (cada variável está comentada no arquivo) e suba o sistema com dados de
demonstração:

```bash
sh infra/scripts/local.sh up --demo-data=true
```

Os testes também rodam em contêiner:

```bash
sh infra/scripts/local.sh tests all
```

Passo a passo completo em [`infra/scripts/README.md`](infra/scripts/README.md) e operação/implantação em
[`infra/README.md`](infra/README.md).

> **Já tinha o ambiente antes da feature 003 (migração do código para inglês)?** As chaves do `infra/.env`, o
> projeto do compose, o banco e os volumes mudaram de nome. Regere o `infra/.env` a partir do `infra/.env.example`
> (levando os segredos para as chaves novas) e siga a seção "Se você tem um ambiente anterior à feature 003" de
> [`infra/README.md`](infra/README.md) para não perder os dados.
