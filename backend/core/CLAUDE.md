# backend/core

Núcleo da aplicação. Aqui mora **toda** a regra de negócio. Não declara dependência de Spring,
JPA, HTTP nem SDK de fornecedor; nenhum artefato cita fornecedor ou modelo de IA específico.

Pacotes em `src/main/java/br/com/felipeblaft/reports/`:

| Pacote | Conteúdo |
|---|---|
| `domain/` | Entidades e regras: `access`, `channel`, `common`, `job`, `registry`, `report`, `semester`, `telegram` |
| `usecase/` | Um caso de uso por ação de negócio (`*UseCase`), agrupados por contexto (`lesson`, `semester`, `history`, `channel`, `access`, `seed`, `document`, `home`, `job`) |
| `port/` | Portas de saída: `*Port` (provedores externos) e `*Repository` (persistência) |
| `service/` | `*Service`: apoiam casos de uso sem substituí-los |

Regras:

- `domain` não depende de `usecase`, `port` nem de nada externo; `usecase` não depende de adaptadores.
- Caso de uso não conhece HTTP, Telegram nem persistência; recebe tudo por portas.
- Autorização por professor (só os próprios alunos) é decidida aqui, em todo caso de uso que lê ou altera aluno.
- Relatório gerado por IA só vira oficial com aprovação explícita da versão vigente; toda alteração cria nova versão.
- Testes unitários cobrem sucesso, erro e borda, com dublês (`src/test/.../doubles`) no lugar de provedores;
  mantenha ≥ 90% de cobertura. As regras de dependência entre camadas são testadas com ArchUnit em
  `src/test/.../architecture`.
- Nomes seguem os sufixos padronizados da constituição (Princípio VI), em inglês: `*UseCase`, `*Port`,
  `*Repository`, `*Service`. O guardião de idioma reprova nome em português (ver `.specify/CLAUDE.md`).
