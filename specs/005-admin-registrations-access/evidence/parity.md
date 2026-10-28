# Evidência de paridade — "Meus alunos" (T140): não se aplica

O `ParityEvidenceGuard` (`backend/application/src/test/java/br/com/felipeblaft/reports/architecture/`) só confere o
arquivo fixo `specs/004-n8n-telegram-transport/evidence/parity.md`, a trava da remoção do transporte antigo do
Telegram (feature 004, FR-029). Ele **não exige** evidência para um roteiro de paridade novo: nenhum guardião lê
este arquivo nem lista os `*ParityScriptIT`.

Por isso o roteiro `StudentRegistryParityScriptIT` (cadastrar, listar, buscar, abrir, editar, inativar, reativar,
aluno compartilhado negado, CPF indisponível e bloqueio por sondagem) não ganha arquivo de evidência no formato
de `backend/application/src/test/resources/parity-evidence/`: a prova da paridade é o próprio teste, que roda na
suíte do backend e reprova o build se os dois canais divergirem.

Este registro fica aqui para que a ausência de evidência seja uma decisão documentada, e não um esquecimento.
Se o `ParityEvidenceGuard` passar a cobrir roteiros novos, este arquivo deve ser substituído pela evidência no
formato do guardião.
