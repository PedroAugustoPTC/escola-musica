# backend/

API do sistema em **Java 25 + Spring Boot 4**, projeto Maven multi-módulo (`pom.xml` agregador).

| Módulo | Papel | Regra de ouro |
|---|---|---|
| `core/` | Regra de negócio: domínio, casos de uso, portas | Sem dependência de Spring, banco ou fornecedor |
| `application/` | Adaptadores de entrada/saída, configuração, migrações, `Dockerfile` | Sem regra de negócio |

A dependência aponta sempre para dentro: `application` → `core`, nunca o contrário (constituição,
Princípio I). Testes ArchUnit em `*/src/test/.../architecture` verificam isso e quebram o build.

- Pacote raiz: `br.com.felipeblaft.reports`.
- Idioma (Princípio VI): identificadores, tabelas, colunas e rotas em **inglês**; mensagens ao usuário
  e de log em **português do Brasil acentuado**. Sufixos padronizados: `*UseCase`, `*Port`,
  `*Repository`, `*Service`, `*Adapter`, `*Controller`, `*Mapper`, `*Request`, `*Response`. O guardião
  de idioma (`LanguageGuardTest` e `ArtifactsLanguageGuardIT`, ver `.specify/CLAUDE.md`) reprova o build
  se aparecer palavra em português em nome de classe, método, campo, pacote, arquivo, tabela ou
  coluna.
- Cobertura mínima de 90% (linha e branch) na regra de negócio (`core`), medida pelo JaCoCo (Princípio V).
- Formatação: Spotless com google-java-format (estilo AOSP) configurado no `pom.xml`; rode
  `mvn spotless:apply` depois de renomear, porque nomes mais curtos mudam a quebra de linha.
- Testes rodam em contêiner: `sh infra/scripts/local.sh tests backend` (ver `infra/CLAUDE.md`).
- Só o backend escreve no banco; migrações Flyway ficam em `application/src/main/resources/db/migration`
  e **`V1` a `V8` são imutáveis** (a `V7` passa o esquema para inglês e a `V8` os valores de enumeração).

Detalhes de cada módulo: `core/CLAUDE.md` e `application/CLAUDE.md`.
