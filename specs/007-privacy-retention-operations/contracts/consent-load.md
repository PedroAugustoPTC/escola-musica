# Contrato: carga dos consentimentos já coletados (FR-036)

Comando operacional `consent-load`, no perfil `seed` (mesmo mecanismo de `seed`, `issue-code` e `rotate-cpf-key`).
Não tem regra própria: para cada linha, chama `RecordConsentUseCase` com o ator `INITIAL_LOAD`.

```bash
sh infra/scripts/local.sh consent-load /caminho/fora/do/repositorio/consentimentos.csv
```

Em produção, o mesmo comando via `deploy.sh` com o arquivo copiado para a VPS por SSH e montado só leitura no
contêiner de uso único; o arquivo é apagado da VPS depois (passo documentado no `infra/README.md`).

## Formato

CSV UTF-8, separador `;`, cabeçalho obrigatório:

```text
student_id;consenting_name;relationship;consented_on;term_version;collection_method
```

| Coluna | Regra |
|---|---|
| `student_id` | uuid de aluno existente, não eliminado (a lista sai da tela de pendências ou da exportação de ids do administrador; **sem CPF nem nome do aluno no arquivo**) |
| `consenting_name` | `PersonName` |
| `relationship` | `MOTHER`, `FATHER`, `LEGAL_GUARDIAN`, `OTHER` |
| `consented_on` | `AAAA-MM-DD`, não futura |
| `term_version` | número de versão publicada e compatível |
| `collection_method` | `SIGNED_PAPER`, `DIGITAL`, `OTHER` |

## Comportamento

- Linha inválida não interrompe as demais; o resumo final (stderr, perfil `seed`, texto) mostra quantas linhas foram
  aceitas e, para as recusadas, **só o número da linha e o código do erro** (nunca o valor).
- Repetir a carga com a mesma linha cria outro registro (histórico); para evitar duplicata, o comando pula a linha
  quando já existe consentimento `VALID` idêntico (mesmo aluno, data, versão e relação) e conta como "já registrado".
- Cada linha aceita gera `CONSENT_RECORD` com `origin = INITIAL_LOAD`.
- O arquivo **não** entra no repositório: o comando recusa caminho dentro de `/work` (volume do repositório nos
  contêineres de teste) e o `.gitignore` já cobre `*.csv` fora de `infra/seed/` (conferir na implementação).
- Ao terminar, imprime quantos alunos menores ainda estão sem consentimento válido (deve ser 0 antes de liberar o uso
  com dados reais).
