# backend/

API do sistema em **Java 25 + Spring Boot 4**, projeto Maven multi-módulo (`pom.xml` agregador).

| Módulo | Papel | Regra de ouro |
|---|---|---|
| `nucleo/` | Regra de negócio: domínio, casos de uso, portas | Sem dependência de Spring, banco ou fornecedor |
| `aplicacao/` | Adaptadores de entrada/saída, configuração, migrações, `Dockerfile` | Sem regra de negócio |

A dependência aponta sempre para dentro: `aplicacao` → `nucleo`, nunca o contrário (constituição,
Princípio I). Testes ArchUnit em `*/src/test/.../arquitetura` verificam isso e quebram o build.

- Pacote raiz: `br.com.felipeblaft.relatorios`.
- Idioma (Princípio VI): identificadores, tabelas, colunas e rotas em **inglês**; mensagens ao usuário
  e de log em **português do Brasil acentuado**. Os nomes atuais (pacotes, classes, sufixos como
  `*CasoDeUso`, `*Controlador`, `*Repositorio`) ainda estão em português, dívida a ser quitada pela
  feature 003; até lá, não renomeie.
- Cobertura mínima de 90% (linha e branch) na regra de negócio, medida pelo JaCoCo (Princípio V).
- Formatação: Spotless com google-java-format (estilo AOSP) configurado no `pom.xml`.
- Testes rodam em contêiner: `sh infra/scripts/local.sh testes backend` (ver `infra/CLAUDE.md`).
- Só o backend escreve no banco; migrações Flyway ficam em `aplicacao/src/main/resources/db/migration`.

Detalhes de cada módulo: `nucleo/CLAUDE.md` e `aplicacao/CLAUDE.md`.
