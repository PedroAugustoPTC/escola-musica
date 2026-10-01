package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.OrigemVersao;
import br.com.felipeblaft.relatorios.dominio.relatorio.ResultadoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TarefaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.EntradaConsolidacaoSemestral;
import br.com.felipeblaft.relatorios.porta.EntradaConsolidacaoSemestral.AulaConsolidada;
import br.com.felipeblaft.relatorios.porta.EntradaRevisaoSemestral;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.InteracaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio.AulaAprovada;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.RespostaLlm;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioSemestralIa;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import br.com.felipeblaft.relatorios.porta.TransacaoPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.ProcessadorTrabalhoServico.ApoioProcessamento;
import br.com.felipeblaft.relatorios.servico.ProcessadorTrabalhoServico.ProcessadorComplementar;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Consolidação e alteração do relatório semestral (FR-042 a FR-045). A consolidação usa só os
 * relatórios de aula aprovados do próprio professor no período, com as datas, e nunca envia dados
 * pessoais à IA; as Informações Gerais são preenchidas pelo sistema. Se a consolidação falhar, nada
 * é criado.
 */
public class ProcessadorSemestralServico implements ProcessadorComplementar {

    private final RelatorioSemestralRepositorio semestrais;
    private final RelatorioAulaRepositorio aulas;
    private final AlunoRepositorio alunos;
    private final ProfessorRepositorio professores;
    private final VersaoRelatorioRepositorio versoes;
    private final InteracaoRelatorioRepositorio interacoes;
    private final GeracaoDocumentoServico documentos;
    private final FilaTrabalhoPorta fila;
    private final TransacaoPorta transacao;
    private final RelogioPorta relogio;

    @SuppressWarnings("java:S107")
    public ProcessadorSemestralServico(
            RelatorioSemestralRepositorio semestrais,
            RelatorioAulaRepositorio aulas,
            AlunoRepositorio alunos,
            ProfessorRepositorio professores,
            VersaoRelatorioRepositorio versoes,
            InteracaoRelatorioRepositorio interacoes,
            GeracaoDocumentoServico documentos,
            FilaTrabalhoPorta fila,
            TransacaoPorta transacao,
            RelogioPorta relogio) {
        this.semestrais = Objects.requireNonNull(semestrais);
        this.aulas = Objects.requireNonNull(aulas);
        this.alunos = Objects.requireNonNull(alunos);
        this.professores = Objects.requireNonNull(professores);
        this.versoes = Objects.requireNonNull(versoes);
        this.interacoes = Objects.requireNonNull(interacoes);
        this.documentos = Objects.requireNonNull(documentos);
        this.fila = Objects.requireNonNull(fila);
        this.transacao = Objects.requireNonNull(transacao);
        this.relogio = Objects.requireNonNull(relogio);
    }

    @Override
    public boolean atende(TrabalhoProcessamento trabalho) {
        return trabalho.tipo() == TipoTrabalho.CONSOLIDACAO_SEMESTRAL
                || (trabalho.tipo() == TipoTrabalho.ALTERACAO
                        && trabalho.relatorioTipo() == TipoRelatorio.SEMESTRAL);
    }

    @Override
    public void processar(TrabalhoProcessamento trabalho, ApoioProcessamento apoio) {
        if (trabalho.tipo() == TipoTrabalho.CONSOLIDACAO_SEMESTRAL) {
            consolidar(trabalho, apoio);
        } else {
            alterar(trabalho, apoio);
        }
    }

    // ------------------------------------------------------------ consolidação

    private void consolidar(TrabalhoProcessamento trabalho, ApoioProcessamento apoio) {
        PeriodoSemestral periodo =
                PeriodoSemestral.de(
                        trabalho.parametro(TrabalhoProcessamento.PERIODO_CHAVE).orElseThrow());
        List<AulaAprovada> aprovadas =
                aulas.listarAprovadosNoPeriodo(
                        trabalho.professorId(),
                        trabalho.alunoId(),
                        periodo.inicio(),
                        periodo.fim());
        if (aprovadas.isEmpty()) {
            apoio.concluir(trabalho, ResultadoTrabalho.FALHA_CONSOLIDACAO);
            return;
        }
        int emAberto =
                aulas.contarEmAbertoNoPeriodo(
                        trabalho.professorId(),
                        trabalho.alunoId(),
                        periodo.inicio(),
                        periodo.fim());
        apoio.avancar(trabalho, EtapaTrabalho.CONSOLIDANDO);
        EntradaConsolidacaoSemestral entrada =
                new EntradaConsolidacaoSemestral(
                        aprovadas.stream()
                                .map(a -> new AulaConsolidada(a.dataAula(), a.conteudo()))
                                .toList(),
                        aprovadas.size(),
                        periodo.inicio(),
                        periodo.fim());
        Optional<RespostaLlm<SaidaRelatorioSemestralIa>> resposta =
                apoio.chamarLlm(
                        trabalho,
                        TarefaLlm.CONSOLIDACAO_SEMESTRAL,
                        TarefaIa.CONSOLIDACAO,
                        entrada,
                        SaidaRelatorioSemestralIa.class);
        if (resposta.isEmpty()) {
            apoio.concluir(trabalho, ResultadoTrabalho.FALHA_CONSOLIDACAO);
            return;
        }
        if (apoio.descartado(trabalho)) {
            return;
        }
        apoio.avancar(trabalho, EtapaTrabalho.GERANDO_PDF);
        Instant agora = relogio.agora();
        boolean criado;
        try {
            criado =
                    transacao.executar(
                            () -> {
                                Aluno aluno = alunos.buscarPorId(trabalho.alunoId()).orElseThrow();
                                Professor professor =
                                        professores
                                                .buscarPorId(trabalho.professorId())
                                                .orElseThrow();
                                InformacoesGerais informacoes =
                                        InformacoesGerais.calcular(
                                                aluno.nome(),
                                                aluno.dataNascimento(),
                                                aluno.instrumento(),
                                                professor.nome(),
                                                periodo,
                                                aprovadas.size(),
                                                relogio.hojeNoFusoDaEscola());
                                RelatorioSemestral relatorio =
                                        RelatorioSemestral.criar(
                                                trabalho.professorId(),
                                                trabalho.alunoId(),
                                                periodo,
                                                aprovadas.size(),
                                                emAberto,
                                                informacoes,
                                                resposta.get().saida().conteudo(),
                                                trabalho.canalSolicitacao(),
                                                agora);
                                semestrais.salvar(relatorio);
                                trabalho.vincularRelatorio(TipoRelatorio.SEMESTRAL, relatorio.id());
                                criarVersao(
                                        relatorio,
                                        OrigemVersao.CONSOLIDACAO,
                                        null,
                                        trabalho,
                                        resposta.get().versaoPrompt());
                                fila.atualizar(trabalho);
                                return true;
                            });
        } catch (ConflitoConcorrenciaExcecao conflito) {
            // aprovado ou aberto em outro canal enquanto consolidava
            criado = false;
        }
        apoio.concluir(
                trabalho,
                criado
                        ? ResultadoTrabalho.PRONTO_PARA_CONFERENCIA
                        : ResultadoTrabalho.FALHA_CONSOLIDACAO);
    }

    // ------------------------------------------------------------ alteração

    private void alterar(TrabalhoProcessamento trabalho, ApoioProcessamento apoio) {
        Optional<String> texto = apoio.textoDoPedido(trabalho);
        if (texto.isEmpty()) {
            encerrar(trabalho, apoio, ResultadoTrabalho.FALHA_ALTERACAO, null, null);
            return;
        }
        RelatorioSemestral relatorio = semestrais.buscarPorId(trabalho.relatorioId()).orElseThrow();
        int versaoBase =
                trabalho.parametro(TrabalhoProcessamento.VERSAO_BASE)
                        .map(Integer::parseInt)
                        .orElse(relatorio.versaoVigente());
        if (relatorio.versaoVigente() != versaoBase) {
            encerrar(
                    trabalho,
                    apoio,
                    ResultadoTrabalho.VERSAO_DESATUALIZADA,
                    texto.get(),
                    ResultadoInteracao.RECUSADA_VERSAO_DESATUALIZADA);
            return;
        }
        if (!comRelatorio(
                trabalho,
                apoio,
                atual -> {
                    atual.iniciarEtapa(EtapaRelatorio.APLICANDO_ALTERACAO, relogio.agora());
                    return true;
                })) {
            return;
        }
        apoio.avancar(trabalho, EtapaTrabalho.APLICANDO_ALTERACAO);
        Optional<RespostaLlm<SaidaRelatorioSemestralIa>> resposta =
                apoio.chamarLlm(
                        trabalho,
                        TarefaLlm.REVISAO_SEMESTRAL,
                        TarefaIa.REVISAO,
                        new EntradaRevisaoSemestral(relatorio.conteudo(), texto.get()),
                        SaidaRelatorioSemestralIa.class);
        if (resposta.isEmpty()) {
            encerrar(
                    trabalho,
                    apoio,
                    ResultadoTrabalho.FALHA_ALTERACAO,
                    texto.get(),
                    ResultadoInteracao.FALHOU);
            return;
        }
        SaidaRelatorioSemestralIa saida = resposta.get().saida();
        final ResultadoTrabalho[] resultado = new ResultadoTrabalho[1];
        boolean gravou =
                comRelatorio(
                        trabalho,
                        apoio,
                        atual -> {
                            Instant agora = relogio.agora();
                            if (saida.semAlteracao() || saida.conteudo().equals(atual.conteudo())) {
                                ResultadoInteracao semMudanca =
                                        saida.semAlteracao() && saida.pedidoDeAcaoIndicado()
                                                ? ResultadoInteracao.PEDIDO_DE_ACAO_IGNORADO
                                                : ResultadoInteracao.SEM_ALTERACAO_DE_CONTEUDO;
                                registrarInteracao(atual, trabalho, texto.get(), semMudanca, null);
                                atual.concluirEtapa(agora);
                                resultado[0] =
                                        semMudanca == ResultadoInteracao.PEDIDO_DE_ACAO_IGNORADO
                                                ? ResultadoTrabalho.PEDIDO_DE_ACAO_IGNORADO
                                                : ResultadoTrabalho.SEM_ALTERACAO_DE_CONTEUDO;
                                return true;
                            }
                            int nova = atual.aplicarAlteracao(saida.conteudo(), agora);
                            InteracaoRelatorio interacao =
                                    registrarInteracao(
                                            atual,
                                            trabalho,
                                            texto.get(),
                                            ResultadoInteracao.APLICADA,
                                            nova);
                            criarVersao(
                                    atual,
                                    OrigemVersao.ALTERACAO,
                                    interacao,
                                    trabalho,
                                    resposta.get().versaoPrompt());
                            resultado[0] = ResultadoTrabalho.NOVA_VERSAO;
                            return true;
                        });
        if (gravou) {
            apoio.concluir(trabalho, resultado[0]);
        }
    }

    /** Encerra a alteração sem nova versão, registrando a interação quando houver texto. */
    private void encerrar(
            TrabalhoProcessamento trabalho,
            ApoioProcessamento apoio,
            ResultadoTrabalho resultado,
            String texto,
            ResultadoInteracao interacao) {
        boolean gravou =
                comRelatorio(
                        trabalho,
                        apoio,
                        atual -> {
                            if (texto != null) {
                                registrarInteracao(atual, trabalho, texto, interacao, null);
                            }
                            atual.concluirEtapa(relogio.agora());
                            return true;
                        });
        if (gravou) {
            apoio.concluir(trabalho, resultado);
        }
    }

    /**
     * Grava no semestral com a linha bloqueada; um semestral cancelado ou um trabalho descartado em
     * outro canal encerra sem resultado.
     */
    private boolean comRelatorio(
            TrabalhoProcessamento trabalho,
            ApoioProcessamento apoio,
            Predicate<RelatorioSemestral> operacao) {
        boolean[] descartado = {false};
        boolean aplicado =
                transacao.executar(
                        () -> {
                            RelatorioSemestral atual =
                                    semestrais
                                            .buscarParaAtualizar(trabalho.relatorioId())
                                            .orElseThrow();
                            if (!atual.status().emAberto()) {
                                descartado[0] = true;
                                return false;
                            }
                            boolean resultado = operacao.test(atual);
                            semestrais.salvar(atual);
                            return resultado;
                        });
        if (descartado[0]) {
            trabalho.descartar(relogio.agora());
            fila.atualizar(trabalho);
            return false;
        }
        return aplicado && !apoio.descartado(trabalho);
    }

    private InteracaoRelatorio registrarInteracao(
            RelatorioSemestral relatorio,
            TrabalhoProcessamento trabalho,
            String texto,
            ResultadoInteracao resultado,
            Integer versaoResultante) {
        int versaoBase =
                trabalho.parametro(TrabalhoProcessamento.VERSAO_BASE)
                        .map(Integer::parseInt)
                        .orElse(relatorio.versaoVigente());
        InteracaoRelatorio interacao =
                InteracaoRelatorio.nova(
                        TipoRelatorio.SEMESTRAL,
                        relatorio.id(),
                        TipoInteracao.ALTERACAO,
                        trabalho.canalSolicitacao(),
                        trabalho.meio(),
                        texto,
                        versaoBase,
                        null,
                        resultado,
                        versaoResultante,
                        relogio.agora());
        interacoes.salvar(interacao);
        return interacao;
    }

    private void criarVersao(
            RelatorioSemestral relatorio,
            OrigemVersao origem,
            InteracaoRelatorio interacao,
            TrabalhoProcessamento trabalho,
            String prompt) {
        VersaoRelatorio versao =
                VersaoRelatorio.nova(
                        TipoRelatorio.SEMESTRAL,
                        relatorio.id(),
                        relatorio.versaoVigente(),
                        relatorio.conteudo(),
                        origem,
                        interacao == null ? null : interacao.id(),
                        trabalho.canalSolicitacao(),
                        prompt,
                        relogio.agora());
        versoes.salvar(versao);
        documentos.gerarConferencia(relatorio, versao);
    }

    @Override
    public void falhou(TrabalhoProcessamento trabalho) {
        if (trabalho.relatorioId() == null || trabalho.relatorioTipo() != TipoRelatorio.SEMESTRAL) {
            return;
        }
        transacao.executar(
                () -> {
                    semestrais
                            .buscarParaAtualizar(trabalho.relatorioId())
                            .filter(r -> r.status().emAberto())
                            .ifPresent(
                                    r -> {
                                        r.concluirEtapa(relogio.agora());
                                        semestrais.salvar(r);
                                    });
                    return null;
                });
    }

    @Override
    public void registrarTempo(TrabalhoProcessamento trabalho) {
        if (trabalho.relatorioId() == null) {
            return;
        }
        semestrais
                .buscarPorId(trabalho.relatorioId())
                .ifPresent(
                        r -> {
                            r.somarTempoProcessamento(trabalho.duracaoMs());
                            semestrais.salvar(r);
                        });
    }
}
