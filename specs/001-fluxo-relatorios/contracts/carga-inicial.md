# Contrato: Carga inicial de cadastros e emissão de códigos

**Feature**: `001-fluxo-relatorios` | Referências: FR-011, FR-004, FR-005; [research.md](../research.md) R-14.

Procedimento operacional usado até existir a feature da secretaria. Roda **dentro do contêiner do
backend** e passa pelos casos de uso do núcleo (Princípio II). Nenhum dado pessoal real entra no
repositório: o arquivo de carga fica fora do Git (`infra/carga/*.json` está no `.gitignore`). Só o
exemplo fictício `infra/carga/exemplo.json` é versionado.

## Comandos

```text
docker compose run --rm backend seed --file /carga/<arquivo>.json [--simulate]
docker compose run --rm backend issue-code --email <email-do-professor>
```

| Comando | Efeito | Saída no terminal |
|---|---|---|
| `seed` | cria ou atualiza professores (por e-mail), alunos (por hash do CPF) e associações. Professor novo recebe código. Nunca apaga nada. | resumo (`criados`, `atualizados`, `inalterados`, `erros` por linha, sem mostrar CPF) e, para cada professor novo, `email → CÓDIGO (válido até dd/mm/aaaa hh:mm)` |
| `seed --simulate` | valida tudo e mostra o resumo, sem gravar | resumo |
| `issue-code` | gera um novo código para o professor, substitui o anterior e reinicia a validade de 7 dias. Não encerra sessões nem vínculos (revogação fica para a feature da secretaria). | `email → CÓDIGO (válido até …)` |

O código em claro **só** aparece nessa saída. No banco fica apenas o HMAC (R-12). Quem opera
repassa o código ao professor fora do sistema.

## Formato do arquivo (`carga.json`)

```json
{
  "teachers": [
    { "email": "clara@exemplo.test", "name": "Clara Souza", "active": true }
  ],
  "students": [
    {
      "cpf": "000.000.000-00",
      "name": "Matteo Mafra",
      "birthDate": "2014-05-10",
      "guardianName": "Responsável Fictício",
      "instrument": "Piano",
      "teachers": ["clara@exemplo.test"]
    }
  ]
}
```

## Validações (todas no núcleo)

| Regra | Erro |
|---|---|
| e-mail obrigatório, formato válido, único | `EMAIL_INVALIDO`, `EMAIL_DUPLICADO_NO_ARQUIVO` |
| nome obrigatório (professor e aluno) | `NOME_OBRIGATORIO` |
| CPF com 11 dígitos e dígitos verificadores válidos | `CPF_INVALIDO` |
| CPF repetido no arquivo | `CPF_DUPLICADO_NO_ARQUIVO` |
| data de nascimento válida e não futura (AAAA-MM-DD) | `DATA_NASCIMENTO_INVALIDA` |
| menor de 18 anos exige `guardianName` | `RESPONSAVEL_OBRIGATORIO` |
| professor da associação existe (no arquivo ou no banco) | `PROFESSOR_INEXISTENTE` |

Com qualquer erro, **nada** é gravado: a carga é tudo ou nada, numa única transação. As linhas com
erro são listadas pelo índice e pelo campo, nunca pelo valor do CPF.
