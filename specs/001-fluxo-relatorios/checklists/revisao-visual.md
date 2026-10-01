# Revisão visual — 001-fluxo-relatorios (Princípio IX)

**Data**: 2026-10-01 · **Referências**: `portal-do-professor-design-system/project/Portal do Professor.dc.html`
(galeria em `Telas.dc.html`) e `Modelo PDF.dc.html`.

**Método**: capturas das telas reais na pilha de `infra/compose.test.yaml` (IA simulada), a 390 px e
1440 px, comparadas lado a lado com as capturas dos quadros do protótipo, renderizados no mesmo
Chromium. Os PDFs gerados pela pilha (aula oficial, semestral em conferência e oficial) foram
rasterizados e comparados com o modelo. A conformidade de acessibilidade (axe, teclado, sem rolagem
horizontal, temas claro e escuro) é coberta por `frontend/tests/e2e/acessibilidade.spec.ts`.

Legenda: ✅ igual ao protótipo · 🔧 divergência corrigida nesta revisão · 📝 divergência consciente

## Portal do Professor

| Tela | 390 px | 1440 px | Observações |
|---|---|---|---|
| 01 Entrar (e 01b–01d) | ✅ | ✅ | |
| 02 Painel | ✅ | ✅ | 🔧 Iniciais do avatar usavam as duas primeiras palavras ("Miguel de Castro Torrente" → "MD"); agora primeiro e último nome ("MT"). |
| 03 Alunos (e 03b–03c) | ✅ | ✅ | |
| 04 Relatório em aberto | ✅ | ✅ | |
| 05 Gravação | ✅ | ✅ | |
| 06 Processando | — | — | Tela transitória (a IA simulada responde em menos de 1 s), não capturada; estados cobertos pelos testes de componente (etapas, "demorando", falha). |
| 07 Perguntas | ✅ | ✅ | |
| 08 Conferência | ✅ | ✅ | 📝 "Professor" em vez de "Professora": o cadastro não tem gênero; o rótulo é neutro. |
| 09 Aprovado | 🔧 | 🔧 | O sobretítulo mostrava "· versão N"; o protótipo mostra a data da aula (no semestral, o período). Corrigido. |
| 10 Semestral (10b, 10c) | ✅ | ✅ | |
| 11 Conferência semestral | ✅ | 🔧 📝 | 🔧 A contagem de aulas consideradas virou o bloco de destaque do protótipo (número grande em serifa + texto). 📝 O protótipo mostra um índice das 10 seções à esquerda e não mostra o PDF; a spec exige o PDF na conferência (US4, cenário 2), então o app mantém texto + PDF lado a lado, como na conferência de aula, e as seções seguem numeradas no texto. |
| 12 Histórico (12b, 12c) | ✅ | ✅ | |
| Navegação | 🔧 | 🔧 | O protótipo destaca "Alunos" nas telas de fluxo (04 a 08, 10, 11, 12); o app não destacava nenhum item. Corrigido. |

Observações de método: o quadro do PDF aparece em branco nas capturas porque o Chromium sem
interface não exibe PDF embutido; no navegador comum o PDF é exibido (e "Abrir PDF" foi validado
pelo teste ponta a ponta do histórico).

## PDFs

| Documento | Situação | Observações |
|---|---|---|
| Relatório de aula (oficial) | ✅ | Cabeçalho com a marca, tabela Aluno/Professor/Data, cinco seções, linha de aprovação, filete e paginação no rodapé, como no modelo. |
| Relatório de aula (conferência) | ✅ | Faixa "Documento em conferência — não oficial". |
| Relatório semestral | 🔧 | Ajustes feitos: (1) célula "Período" com o intervalo de datas, como no modelo (antes: "2º semestre 2026"); (2) cabeçalho corrido a partir da página 2 (marca + "Relatório Semestral · aluno · semestre"); (3) a aprovação aparece só no bloco de assinaturas (antes havia também a linha "Aprovado por…"); (4) rodapé "documento gerado a partir dos relatórios de aula do período."; (5) "Parecer Final" e assinaturas ficam juntos, sem página só com as assinaturas. |
| Relatório semestral — Informações Gerais | 📝 | O modelo mostra um parágrafo; o app mostra os campos rotulados (nome, idade, instrumento, professor, período, aulas consideradas), conforme o modelo de dados (`InformacoesGerais`). |
| Relatório semestral — bloco de assinaturas | 📝 | O modelo mostra "Versão 1 · Portal do Professor"; o app mostra "Versão N" e a hora da aprovação. O canal de aprovação não chega ao gerador de PDF (`GeracaoPdfPorta.Aprovacao`). |

## Conclusão

Telas e PDFs conferidos. Divergências corrigidas: iniciais, item ativo da navegação, sobretítulo da
tela 09, bloco de contagem da conferência semestral e cinco ajustes no PDF semestral. As divergências conscientes
acima decorrem da spec ou do modelo de dados.
