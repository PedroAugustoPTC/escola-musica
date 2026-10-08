# backend/nucleo

Núcleo da aplicação. Aqui mora **toda** a regra de negócio. Não declara dependência de Spring,
JPA, HTTP nem SDK de fornecedor; nenhum artefato cita fornecedor ou modelo de IA específico.

Pacotes em `src/main/java/br/com/felipeblaft/relatorios/`:

| Pacote | Conteúdo |
|---|---|
| `dominio/` | Entidades e regras: `acesso`, `cadastro`, `canal`, `comum`, `relatorio`, `semestral`, `telegram`, `trabalho` |
| `casodeuso/` | Um caso de uso por ação de negócio (`*CasoDeUso`), agrupados por contexto (`aula`, `semestral`, `historico`, `canal`, `acesso`, `carga`, `documento`, `inicio`, `trabalho`) |
| `porta/` | Portas de saída: `*Porta` (provedores externos) e `*Repositorio` (persistência) |
| `servico/` | `*Servico`: apoiam casos de uso sem substituí-los |

Regras:

- `dominio` não depende de `casodeuso`, `porta` nem de nada externo; `casodeuso` não depende de adaptadores.
- Caso de uso não conhece HTTP, Telegram nem persistência; recebe tudo por portas.
- Autorização por professor (só os próprios alunos) é decidida aqui, em todo caso de uso que lê ou altera aluno.
- Relatório gerado por IA só vira oficial com aprovação explícita da versão vigente; toda alteração cria nova versão.
- Testes unitários cobrem sucesso, erro e borda, com dublês no lugar de provedores; mantenha ≥ 90% de cobertura.
- Nomes seguem os sufixos padronizados da constituição (Princípio VI).
