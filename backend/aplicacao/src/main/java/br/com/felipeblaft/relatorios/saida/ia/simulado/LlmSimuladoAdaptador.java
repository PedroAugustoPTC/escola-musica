package br.com.felipeblaft.relatorios.saida.ia.simulado;

import br.com.felipeblaft.relatorios.dominio.cadastro.BuscaAluno;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.EntradaConsolidacaoSemestral;
import br.com.felipeblaft.relatorios.porta.EntradaRevisaoSemestral;
import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.LlmPorta;
import br.com.felipeblaft.relatorios.porta.RespostaLlm;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioAulaIa;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioSemestralIa;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM simulado e determinístico ({@code PROVEDORES_IA=simulado}), usado nos testes ponta a ponta e
 * em desenvolvimento: a primeira estruturação traz perguntas; a incorporação não traz; a revisão
 * muda a observação conforme o pedido e sinaliza pedidos de aprovar ou cancelar.
 */
public class LlmSimuladoAdaptador implements LlmPorta {

    public static final String PROVEDOR = "simulado/llm";

    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /** Respostas de outras tarefas (ex.: semestral), registradas pela história correspondente. */
    private BiFunction<TarefaLlm, EntradaTarefa, Object> complementar;

    public void registrarComplementar(BiFunction<TarefaLlm, EntradaTarefa, Object> resposta) {
        this.complementar = resposta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RespostaLlm<T> gerarJson(
            TarefaLlm tarefa, EntradaTarefa entrada, Class<T> tipoSaida) {
        Object saida =
                switch (entrada) {
                    case EntradaTarefa.EstruturacaoAula e -> estruturacao();
                    case EntradaTarefa.IncorporacaoRespostas e -> incorporacao(e);
                    case EntradaTarefa.RevisaoAula e -> revisao(e);
                    case EntradaConsolidacaoSemestral e -> consolidacao();
                    case EntradaRevisaoSemestral e -> revisaoSemestral(e);
                    default ->
                            Objects.requireNonNull(complementar, "tarefa sem simulação")
                                    .apply(tarefa, entrada);
                };
        return new RespostaLlm<>(
                (T) saida,
                tarefa.name().toLowerCase().replace('_', '-') + ".v1",
                0,
                0,
                BigDecimal.ZERO,
                PROVEDOR,
                1);
    }

    private static SaidaRelatorioAulaIa estruturacao() {
        try (InputStream entrada =
                LlmSimuladoAdaptador.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/estruturacao-aula.json")) {
            return JSON.readValue(entrada, SaidaRelatorioAulaIa.class);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    private static SaidaRelatorioSemestralIa consolidacao() {
        try (InputStream entrada =
                LlmSimuladoAdaptador.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/consolidacao-semestral.json")) {
            return JSON.readValue(entrada, SaidaRelatorioSemestralIa.class);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    /** Revisão do semestral: o pedido vira o parecer final; aprovar/cancelar só são sinalizados. */
    private static SaidaRelatorioSemestralIa revisaoSemestral(EntradaRevisaoSemestral entrada) {
        String pedido = BuscaAluno.normalizar(entrada.pedido());
        ConteudoRelatorioSemestral atual = entrada.conteudoAtual();
        if (pedido.contains("aprovar") || pedido.contains("aprova")) {
            return SaidaRelatorioSemestralIa.de(atual, true, "APROVAR");
        }
        if (pedido.contains("cancelar") || pedido.contains("descarta")) {
            return SaidaRelatorioSemestralIa.de(atual, true, "CANCELAR");
        }
        ConteudoRelatorioSemestral alterado =
                new ConteudoRelatorioSemestral(
                        atual.frequenciaEEstudo(),
                        atual.tecnica(),
                        atual.musicalidade(),
                        atual.leituraEMemorizacao(),
                        atual.pontosDeAtencao(),
                        atual.estrategiasPedagogicas(),
                        atual.acompanhamentoFamiliar(),
                        atual.planejamentoProximoSemestre(),
                        entrada.pedido().strip());
        return SaidaRelatorioSemestralIa.de(alterado, false, SaidaRelatorioAulaIa.NENHUM);
    }

    private static SaidaRelatorioAulaIa incorporacao(EntradaTarefa.IncorporacaoRespostas entrada) {
        ConteudoRelatorioAula atual = entrada.conteudoAtual();
        String observacoes = juntar(atual.observacoes(), entrada.resposta());
        return saida(atual, observacoes, false, "NENHUM");
    }

    private static SaidaRelatorioAulaIa revisao(EntradaTarefa.RevisaoAula entrada) {
        String pedido = BuscaAluno.normalizar(entrada.pedido());
        ConteudoRelatorioAula atual = entrada.conteudoAtual();
        if (pedido.contains("aprovar") || pedido.contains("aprova")) {
            return saida(atual, atual.observacoes(), true, "APROVAR");
        }
        if (pedido.contains("cancelar") || pedido.contains("descarta")) {
            return saida(atual, atual.observacoes(), true, "CANCELAR");
        }
        return saida(atual, entrada.pedido().strip(), false, "NENHUM");
    }

    private static SaidaRelatorioAulaIa saida(
            ConteudoRelatorioAula base,
            String observacoes,
            boolean semAlteracao,
            String pedidoDeAcao) {
        return new SaidaRelatorioAulaIa(
                base.conteudosTrabalhados(),
                base.evolucao(),
                base.dificuldades(),
                base.atividadesPropostas(),
                observacoes,
                List.of(),
                semAlteracao,
                pedidoDeAcao);
    }

    private static String juntar(String atual, String acrescimo) {
        return atual == null || atual.isBlank()
                ? acrescimo.strip()
                : atual + " " + acrescimo.strip();
    }
}
