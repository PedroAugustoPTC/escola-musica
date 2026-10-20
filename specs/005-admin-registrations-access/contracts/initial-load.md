# Contrato: carga inicial e comandos operacionais depois da feature 005

**Feature**: `005-admin-registrations-access` | Referências: FR-034, FR-034a, FR-034b, FR-044, FR-020;
`specs/001-fluxo-relatorios/contracts/carga-inicial.md` (**histórico**; esta é a versão vigente do que mudou).

A carga inicial **deixa de ter regra própria**: cada linha do arquivo vira uma chamada aos mesmos casos de uso da
administração, com o ator "sistema" de origem `INITIAL_LOAD`. Nenhum papel e nenhuma credencial novos existem para ela.

## 1. Comandos (perfil `seed`, dentro do contêiner do backend)

| Comando | Antes | Agora |
|---|---|---|
| `seed --file <arquivo> [--simulate]` | regra própria em `LoadInitialRegistryUseCase` | orquestra `RegisterTeacherUseCase`, `UpdateTeacherUseCase`, `RegisterStudentUseCase`, `UpdateStudentUseCase` e `AssignTeacherToStudentUseCase` |
| `issue-code --email <email>` | `EmitAccessCodeUseCase` (não encerrava sessões) | `ReissueAccessCodeUseCase` (encerra as sessões Web; **não** encerra o vínculo do Telegram); fica como **emergência**, não é o caminho normal (FR-044) |
| `demo-data` | cria massa demonstrativa | sem mudança de contrato; passa a usar os mesmos casos de uso |
| `rotate-cpf-key` (novo) | — | rotação das chaves do CPF (seção 4) |

Para `seed`, o ator é `Actor.system(INITIAL_LOAD)`: o autor do registro de autoria é "sistema" e o canal
"carga inicial". `Actor.system` só pode ser criado por `inbound.seed` e pelo bootstrap (regra ArchUnit).

## 2. Formato do arquivo (acrescenta dois campos opcionais ao aluno)

```json
{
  "teachers": [{ "email": "clara@example.test", "name": "Clara Souza", "active": true }],
  "students": [
    {
      "cpf": "000.000.000-00",
      "name": "Matteo Mafra",
      "birthDate": "2014-05-10",
      "guardianName": "Responsável Fictício",
      "guardianPhone": "(11) 91234-5678",
      "guardianEmail": "responsavel@example.test",
      "instrument": "Piano",
      "teachers": ["clara@example.test"]
    }
  ]
}
```

`guardianPhone` e `guardianEmail` são **obrigatórios com `guardianName` para menor de 18 anos** (FR-017); a validação é a
mesma da Web (domínio), sem cópia. Os arquivos `infra/seed/example.json` e `infra/seed/demo.json` são atualizados.

## 3. Semântica preservada

- **Tudo ou nada**: o arquivo inteiro é validado e gravado numa transação; qualquer erro, nada é gravado. Os erros
  continuam listados por seção, índice e campo, **nunca pelo valor do CPF** (os códigos de campo são os do
  `VALIDATION_FAILED`: `INVALID_EMAIL`, `NAME_REQUIRED`/`INVALID_NAME`, `INVALID_CPF`, `INVALID_BIRTH_DATE`,
  `GUARDIAN_REQUIRED`, `INVALID_PHONE`, `TEACHER_NOT_FOUND`, `DUPLICATE_*_IN_FILE`).
- **Idempotente**: professor por e-mail, aluno por resumo do CPF; reexecutar não duplica e só atualiza o que mudou. Nunca apaga.
- **`--simulate`**: executa os mesmos casos de uso dentro de uma transação que é **desfeita** ao final
  (`TransactionPort.runAndRollBack`), de modo que contagens e erros são exatamente os da execução real, inclusive os de
  unicidade do banco. Nada fica gravado, nem registro de autoria.
- Professor novo recebe código de acesso (impresso uma única vez no terminal, como hoje); o aluno do arquivo é
  associado aos professores listados.

## 4. Primeiro administrador (bootstrap, FR-034)

Variáveis de ambiente (nomes no plano, FR-034): `ADMIN_BOOTSTRAP_NAME`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`
(mapeadas em `reports.administrator.bootstrap.*`). `.env.example` traz as três **vazias**; o `.env` não é versionado.

| Situação na subida | Resultado |
|---|---|
| nenhum administrador e variáveis válidas | cria o primeiro: ativo, hash Argon2id, senha **provisória**; autoria: autor "sistema", canal "inicialização do sistema" |
| já existe algum administrador (qualquer situação) | ignora as variáveis; nada é criado nem alterado |
| nenhum administrador e variáveis ausentes | sobe e registra aviso em português; nenhum administrador existe |
| nenhum administrador e senha fora do FR-004 / e-mail inválido | **recusa subir**, com mensagem em português que cita a variável, nunca o valor |

A criação é atômica (`insert … where not exists`): duas instâncias subindo juntas criam no máximo um.

**Recuperação (FR-034b)**: se nenhum administrador consegue entrar, o mantenedor remove as linhas de `administrator`
no banco (as sessões saem por cascata; **`authorship_record` é preservado**, sem chave estrangeira), preenche o `.env`
e reinicia. Não há variável, rota nem comando que redefina a senha de um administrador existente. O procedimento
passo a passo fica em `quickstart.md`.

## 5. Rotação das chaves do CPF (FR-020)

`rotate-cpf-key --old-encryption-key … --new-encryption-key … --old-hmac-secret … --new-hmac-secret … [--simulate]`
(chaves por variáveis de ambiente, não por argumento, para não aparecerem no histórico do shell):

1. exige o serviço parado para escrita (janela de manutenção) e uma cópia de segurança recente (que **não** inclui as chaves);
2. em lotes e numa transação, decifra cada `cpf_encrypted` com a chave antiga **em memória**, cifra com a nova e
   recalcula `cpf_hash` com o segredo novo; confere que não surgiram colisões;
3. nunca registra, imprime ou grava o CPF em claro; só contagens;
4. cada chave é opcional e independente. Trocar **só a chave de cifra** reescreve `cpf_encrypted` e não tem efeito
   colateral. Trocar o **segredo do resumo** (`HMAC_SECRET`) também recalcula `cpf_hash`, mas esse segredo protege ainda
   os códigos de acesso do professor: os vigentes deixam de valer (o administrador os reemite). Os códigos de ativação e
   as origens de tentativas usam o `AUTH_HMAC_SECRET` e não são afetados. O comando imprime esse aviso
   e exige a confirmação `--confirm-invalidate-codes` quando o segredo do resumo muda.
