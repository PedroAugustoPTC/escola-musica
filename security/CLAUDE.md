# security/

Avaliação de segurança do sistema: relatórios técnicos que servem de base para as correções.

| Caminho | O que é |
|---|---|
| `reports/AAAA-MM-DD-security-assessment.md` | Relatório técnico da avaliação (achados `SEC-NNN`, evidência reproduzível, correção recomendada) |
| `out/` | Saídas brutas das ferramentas; **ignorado pelo Git** (podem conter achados sensíveis) |

Como gerar as saídas: `sh infra/scripts/local.sh tests security` (tudo em contêiner; ferramental em
`infra/compose.security.yaml` e `infra/tests/security/`). O DAST e as sondas atacam só a pilha isolada de testes,
nunca o ambiente local de uso nem a VPS.

Regras:

- Relatório **sem segredo em claro**: segredo achado aparece só como arquivo, linha e tipo, nunca o valor.
- Todo achado de ferramenta é triado manualmente antes de entrar no relatório; falso positivo vai para a seção própria.
- O relatório descreve e recomenda; a correção é feita em PR à parte.
- Arquivos novos em inglês (Princípio VI); texto do relatório em português.
