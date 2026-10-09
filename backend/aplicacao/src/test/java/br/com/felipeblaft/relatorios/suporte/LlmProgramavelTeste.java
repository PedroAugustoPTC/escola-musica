package br.com.felipeblaft.relatorios.suporte;

import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.RespostaLlm;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioAulaIa;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Programa o {@code LlmPorta} simulado dos testes de integração (por {@code doAnswer} num spy do
 * bean): a IA simulada só pergunta na estruturação, e aqui cada incorporação de respostas devolve,
 * em ordem, as perguntas programadas. Tudo mais segue o comportamento simulado.
 */
public class LlmProgramavelTeste implements Answer<Object> {

    private final Deque<List<String>> perguntasDasIncorporacoes = new ArrayDeque<>();
    private final List<EntradaTarefa.IncorporacaoRespostas> incorporacoes = new ArrayList<>();

    /** Perguntas que as próximas incorporações devolvem, uma lista por chamada. */
    @SafeVarargs
    public final LlmProgramavelTeste perguntasNasIncorporacoes(List<String>... rodadas) {
        perguntasDasIncorporacoes.addAll(List.of(rodadas));
        return this;
    }

    /** Entradas recebidas pelas incorporações, na ordem. */
    public List<EntradaTarefa.IncorporacaoRespostas> incorporacoes() {
        return List.copyOf(incorporacoes);
    }

    @Override
    public Object answer(InvocationOnMock invocacao) throws Throwable {
        Object real = invocacao.callRealMethod();
        TarefaLlm tarefa = invocacao.getArgument(0);
        if (tarefa != TarefaLlm.INCORPORACAO_RESPOSTAS) {
            return real;
        }
        incorporacoes.add((EntradaTarefa.IncorporacaoRespostas) invocacao.getArgument(1));
        if (perguntasDasIncorporacoes.isEmpty()) {
            return real;
        }
        RespostaLlm<?> resposta = (RespostaLlm<?>) real;
        SaidaRelatorioAulaIa saida = (SaidaRelatorioAulaIa) resposta.saida();
        return new RespostaLlm<>(
                new SaidaRelatorioAulaIa(
                        saida.conteudosTrabalhados(),
                        saida.evolucao(),
                        saida.dificuldades(),
                        saida.atividadesPropostas(),
                        saida.observacoes(),
                        perguntasDasIncorporacoes.poll(),
                        saida.semAlteracaoDeConteudo(),
                        saida.pedidoDeAcao()),
                resposta.versaoPrompt(),
                resposta.tokensEntrada(),
                resposta.tokensSaida(),
                resposta.custoEstimado(),
                resposta.provedorModelo(),
                resposta.duracaoMs());
    }
}
