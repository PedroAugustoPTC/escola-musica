package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.CancelarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso.VisaoRelatorioAula;
import br.com.felipeblaft.relatorios.casodeuso.aula.ResponderPerguntasCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.SolicitarAlteracaoRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.TentarNovamenteRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.ConsultarTrabalhoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.EtapaFluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.RelatorioFoco;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.MensagemEtapa;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.MensagemTelegram;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Relatório de aula em foco (estados #3 a #6 da tabela, sequência 5.2, passos 5 a 8): resposta às
 * perguntas e pedido de alteração por texto ou áudio sem confirmação (FR-027b), aprovação e
 * cancelamento com confirmação, nova tentativa e o botão "Continuar no chat".
 */
@Component
@Order(10)
public class FluxoRelatorioAulaTelegram implements FluxoTelegram {

    static final String CONFIRMACAO_APROVAR = "aprovar:";
    static final String CONFIRMACAO_CANCELAR = "cancelar";

    private final ApresentacaoRelatorioAulaTelegram apresentacao;
    private final ApresentadorTelegram apresentador;
    private final ResponderPerguntasCasoDeUso responder;
    private final SolicitarAlteracaoRelatorioAulaCasoDeUso alterar;
    private final TentarNovamenteRelatorioAulaCasoDeUso tentar;
    private final AprovarRelatorioAulaCasoDeUso aprovar;
    private final CancelarRelatorioAulaCasoDeUso cancelar;
    private final ConsultarTrabalhoCasoDeUso consultarTrabalho;
    private final BuscarAlunosCasoDeUso buscarAlunos;
    private final InterpretadorConversaTelegram interpretador;

    @SuppressWarnings("java:S107")
    public FluxoRelatorioAulaTelegram(
            ApresentacaoRelatorioAulaTelegram apresentacao,
            ApresentadorTelegram apresentador,
            ResponderPerguntasCasoDeUso responder,
            SolicitarAlteracaoRelatorioAulaCasoDeUso alterar,
            TentarNovamenteRelatorioAulaCasoDeUso tentar,
            AprovarRelatorioAulaCasoDeUso aprovar,
            CancelarRelatorioAulaCasoDeUso cancelar,
            ConsultarTrabalhoCasoDeUso consultarTrabalho,
            BuscarAlunosCasoDeUso buscarAlunos,
            InterpretadorConversaTelegram interpretador) {
        this.apresentacao = apresentacao;
        this.apresentador = apresentador;
        this.responder = responder;
        this.alterar = alterar;
        this.tentar = tentar;
        this.aprovar = aprovar;
        this.cancelar = cancelar;
        this.consultarTrabalho = consultarTrabalho;
        this.buscarAlunos = buscarAlunos;
        this.interpretador = interpretador;
    }

    // ------------------------------------------------------------------ foco

    /** Relatório de aula em foco ainda aberto; um foco que não vale mais é limpo. */
    private Optional<VisaoRelatorioAula> foco(ContextoTelegram contexto) {
        ConversaTelegram conversa = contexto.conversa();
        Optional<RelatorioFoco> foco =
                conversa.relatorioFoco().filter(f -> f.tipo() == TipoRelatorio.AULA);
        if (foco.isEmpty()) {
            return Optional.empty();
        }
        try {
            VisaoRelatorioAula visao =
                    apresentacao.obter(contexto.professor().id(), foco.get().id());
            if (!visao.relatorio().status().emAberto()) {
                conversa.limparFoco();
                return Optional.empty();
            }
            return Optional.of(visao);
        } catch (ExcecaoDominio naoEncontrado) {
            conversa.limparFoco();
            return Optional.empty();
        }
    }

    private int versaoBase(ContextoTelegram contexto, RelatorioAula relatorio) {
        Integer apresentada = contexto.conversa().versaoApresentada();
        return apresentada == null ? relatorio.versaoVigente() : apresentada;
    }

    // ------------------------------------------------------------------ texto e áudio

    @Override
    public boolean aoTexto(ContextoTelegram contexto, String texto) {
        Optional<VisaoRelatorioAula> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        VisaoRelatorioAula visao = foco.get();
        RelatorioAula relatorio = visao.relatorio();
        if (ApresentacaoRelatorioAulaTelegram.emProcessamento(visao)) {
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(), apresentacao.processando(visao), Teclado.NENHUM));
            return true;
        }
        UUID professorId = contexto.professor().id();
        switch (relatorio.status()) {
            case AGUARDANDO_RESPOSTAS ->
                    etapa(
                            contexto,
                            null,
                            responder.responderTexto(
                                    professorId, relatorio.id(), texto, Canal.TELEGRAM));
            case AGUARDANDO_CONFERENCIA -> {
                Optional<AlunoResumo> outroAluno =
                        buscarAlunos
                                .correspondenciaExata(professorId, texto)
                                .filter(a -> !a.id().equals(relatorio.alunoId()));
                if (outroAluno.isPresent()) {
                    perguntarTrocaDeAluno(contexto, visao, outroAluno.get());
                    return true;
                }
                comVersaoVigente(
                        contexto,
                        visao,
                        () ->
                                etapa(
                                        contexto,
                                        null,
                                        alterar.alterarPorTexto(
                                                professorId,
                                                relatorio.id(),
                                                texto,
                                                versaoBase(contexto, relatorio),
                                                Canal.TELEGRAM)));
            }
            default -> contexto.responder(apresentacao.falha(contexto.chatId(), null));
        }
        return true;
    }

    @Override
    public boolean aoAudio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        Optional<VisaoRelatorioAula> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        VisaoRelatorioAula visao = foco.get();
        RelatorioAula relatorio = visao.relatorio();
        if (ApresentacaoRelatorioAulaTelegram.emProcessamento(visao)) {
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(), apresentacao.processando(visao), Teclado.NENHUM));
            return true;
        }
        UUID professorId = contexto.professor().id();
        ReferenciaArquivoCanal audio = FluxoRegistroAulaTelegram.referencia(mensagem, arquivo);
        switch (relatorio.status()) {
            case AGUARDANDO_RESPOSTAS ->
                    etapa(
                            contexto,
                            null,
                            responder.responderAudio(
                                    professorId, relatorio.id(), audio, Canal.TELEGRAM));
            case AGUARDANDO_CONFERENCIA ->
                    comVersaoVigente(
                            contexto,
                            visao,
                            () ->
                                    etapa(
                                            contexto,
                                            null,
                                            alterar.alterarPorAudio(
                                                    professorId,
                                                    relatorio.id(),
                                                    audio,
                                                    versaoBase(contexto, relatorio),
                                                    Canal.TELEGRAM)));
            default -> contexto.responder(apresentacao.falha(contexto.chatId(), null));
        }
        return true;
    }

    /** Nome exato de outro aluno durante a conferência: pergunta antes de trocar (estado #4). */
    private void perguntarTrocaDeAluno(
            ContextoTelegram contexto, VisaoRelatorioAula visao, AlunoResumo aluno) {
        ConversaTelegram conversa = contexto.conversa();
        conversa.guardar(InterpretadorConversaTelegram.OPCOES_ALUNOS, aluno.id().toString());
        conversa.definirEtapa(EtapaFluxoConversa.ESCOLHENDO_ALUNO);
        contexto.responder(
                new Mensagem(
                        contexto.chatId(),
                        apresentador.texto(
                                "TROCAR_ALUNO_DURANTE_CONFERENCIA",
                                Map.of("aluno", aluno.nome(), "alunoAtual", visao.alunoNome())),
                        Teclado.de(
                                new Botao(
                                        apresentador.rotulo(
                                                "BOTAO_CONFIRMAR_ALUNO",
                                                Map.of("aluno", aluno.nome())),
                                        DadosCallbackCodificador.codificar("aluno", "0")),
                                apresentador.botao(
                                        "BOTAO_VOLTAR",
                                        Map.of(),
                                        "foco",
                                        "a" + visao.relatorio().id()))));
    }

    /** Versão apresentada desatualizada: avisa e reapresenta a conferência vigente (FR-033). */
    private void comVersaoVigente(
            ContextoTelegram contexto, VisaoRelatorioAula visao, Runnable acao) {
        try {
            acao.run();
        } catch (ExcecaoDominio erro) {
            if (erro.codigo() != CodigoErro.VERSAO_DESATUALIZADA) {
                throw erro;
            }
            contexto.responder(
                    apresentador.erro(contexto.chatId(), erro.codigo(), erro.detalhes()));
            apresentar(contexto, visao.relatorio().id(), null);
        }
    }

    private void etapa(ContextoTelegram contexto, Long editar, TrabalhoProcessamento trabalho) {
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        editar,
                        textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }

    static String textoEtapa(ApresentadorTelegram apresentador, TrabalhoProcessamento trabalho) {
        return "<i>"
                + ApresentadorTelegram.escapar(
                        apresentador.formatacao().etapa(trabalho.etapa().name()))
                + "…</i>";
    }

    // ------------------------------------------------------------------ comandos e botões

    @Override
    public boolean aoComando(ContextoTelegram contexto, Comando comando) {
        if (comando != Comando.APROVAR
                && comando != Comando.CANCELAR
                && comando != Comando.TENTAR) {
            return false;
        }
        Optional<VisaoRelatorioAula> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        RelatorioAula relatorio = foco.get().relatorio();
        switch (comando) {
            case APROVAR -> pedirAprovacao(contexto, foco.get(), versaoBase(contexto, relatorio));
            case CANCELAR -> pedirCancelamento(contexto);
            default -> tentarNovamente(contexto, relatorio.id());
        }
        return true;
    }

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        switch (acao) {
            case "foco" -> {
                return continuarNoChat(contexto, argumento);
            }
            case "aprov", "aprovok", "alt", "canc", "cancok", "pdf", "tent" -> {
                Optional<VisaoRelatorioAula> foco = foco(contexto);
                if (foco.isEmpty()) {
                    return false;
                }
                return acaoNoFoco(contexto, foco.get(), acao, argumento);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean acaoNoFoco(
            ContextoTelegram contexto, VisaoRelatorioAula visao, String acao, String argumento) {
        ConversaTelegram conversa = contexto.conversa();
        RelatorioAula relatorio = visao.relatorio();
        switch (acao) {
            case "aprov" -> {
                Integer versao = numero(argumento);
                if (versao == null) {
                    return false;
                }
                conversa.limparConfirmacao();
                pedirAprovacao(contexto, visao, versao);
            }
            case "aprovok" -> {
                Integer versao = numero(argumento);
                if (versao == null
                        || !conversa.confirmacaoPendente()
                                .filter((CONFIRMACAO_APROVAR + versao)::equals)
                                .isPresent()) {
                    return false;
                }
                conversa.limparConfirmacao();
                aprovarVersao(contexto, relatorio.id(), versao);
            }
            case "cancok" -> {
                if (!conversa.confirmacaoPendente()
                        .filter(CONFIRMACAO_CANCELAR::equals)
                        .isPresent()) {
                    return false;
                }
                conversa.limparConfirmacao();
                cancelarRelatorio(contexto, relatorio.id());
            }
            case "canc" -> {
                conversa.limparConfirmacao();
                pedirCancelamento(contexto);
            }
            case "alt" -> {
                conversa.limparConfirmacao();
                if (relatorio.status() != StatusRelatorio.AGUARDANDO_CONFERENCIA) {
                    return false;
                }
                contexto.responder(
                        apresentador.simples(contexto.chatId(), "PEDIDO_ALTERACAO_ROTULO"));
            }
            case "pdf" -> {
                if (relatorio.status() != StatusRelatorio.AGUARDANDO_CONFERENCIA) {
                    return false;
                }
                apresentacao
                        .documentoConferencia(
                                contexto.chatId(), contexto.professor().id(), relatorio)
                        .ifPresentOrElse(
                                contexto::responder,
                                () ->
                                        contexto.responder(
                                                apresentador.simples(
                                                        contexto.chatId(),
                                                        "CONFERENCIA_PDF_GERANDO")));
            }
            default -> {
                conversa.limparConfirmacao();
                tentarNovamente(contexto, relatorio.id());
            }
        }
        return true;
    }

    @Override
    public boolean aoConfirmar(ContextoTelegram contexto, String confirmacao) {
        if (!confirmacao.startsWith(CONFIRMACAO_APROVAR)
                && !confirmacao.equals(CONFIRMACAO_CANCELAR)) {
            return false;
        }
        Optional<VisaoRelatorioAula> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        UUID relatorioId = foco.get().relatorio().id();
        if (confirmacao.equals(CONFIRMACAO_CANCELAR)) {
            cancelarRelatorio(contexto, relatorioId);
            return true;
        }
        Integer versao = numero(confirmacao.substring(CONFIRMACAO_APROVAR.length()));
        if (versao == null) {
            return false;
        }
        aprovarVersao(contexto, relatorioId, versao);
        return true;
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return foco(contexto)
                .map(
                        visao ->
                                visao.relatorio().status() == StatusRelatorio.AGUARDANDO_RESPOSTAS
                                        ? apresentador.rotulo("RECEBER_RESPOSTA")
                                        : apresentador.rotulo("RECEBER_ALTERACAO"))
                .orElse(null);
    }

    // ------------------------------------------------------------------ ações

    private void pedirAprovacao(ContextoTelegram contexto, VisaoRelatorioAula visao, int versao) {
        RelatorioAula relatorio = visao.relatorio();
        if (relatorio.status() != StatusRelatorio.AGUARDANDO_CONFERENCIA
                || ApresentacaoRelatorioAulaTelegram.emProcessamento(visao)) {
            throw new ExcecaoDominio(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        if (versao != relatorio.versaoVigente()) {
            contexto.responder(
                    apresentador.erro(
                            contexto.chatId(),
                            CodigoErro.VERSAO_DESATUALIZADA,
                            Map.of("versaoVigente", relatorio.versaoVigente())));
            apresentar(contexto, relatorio.id(), null);
            return;
        }
        contexto.conversa().pedirConfirmacao(CONFIRMACAO_APROVAR + versao);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_APROVACAO", Map.of("versao", versao)),
                        apresentador.botao(
                                "BOTAO_CONFIRMAR_APROVACAO",
                                Map.of("versao", versao),
                                "aprovok",
                                Integer.toString(versao))));
    }

    private void aprovarVersao(ContextoTelegram contexto, UUID relatorioId, int versao) {
        AprovarRelatorioAulaCasoDeUso.Aprovacao aprovacao;
        try {
            aprovacao =
                    aprovar.executar(
                            contexto.professor().id(), relatorioId, versao, Canal.TELEGRAM);
        } catch (ExcecaoDominio erro) {
            if (erro.codigo() != CodigoErro.VERSAO_DESATUALIZADA) {
                throw erro;
            }
            contexto.responder(
                    apresentador.erro(contexto.chatId(), erro.codigo(), erro.detalhes()));
            apresentar(contexto, relatorioId, null);
            return;
        }
        ConversaTelegram conversa = contexto.conversa();
        conversa.limparFoco();
        conversa.limparSelecao();
        conversa.encerrarFluxo();
        contexto.responder(
                new Mensagem(
                        contexto.chatId(),
                        apresentador.texto(
                                "RELATORIO_APROVADO",
                                Map.of(
                                        "data",
                                                apresentador
                                                        .formatacao()
                                                        .data(aprovacao.aprovadoEm()),
                                        "hora",
                                                apresentador
                                                        .formatacao()
                                                        .hora(aprovacao.aprovadoEm()))),
                        Teclado.de(
                                apresentador.botao("BOTAO_REGISTRAR_OUTRA_AULA", "reg"),
                                apresentador.botao("BOTAO_HISTORICO", "hist"))));
    }

    private void pedirCancelamento(ContextoTelegram contexto) {
        contexto.conversa().pedirConfirmacao(CONFIRMACAO_CANCELAR);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_CANCELAMENTO"),
                        apresentador.botao("BOTAO_CONFIRMAR_CANCELAMENTO", "cancok")));
    }

    private void cancelarRelatorio(ContextoTelegram contexto, UUID relatorioId) {
        cancelar.cancelar(contexto.professor().id(), relatorioId);
        ConversaTelegram conversa = contexto.conversa();
        conversa.limparFoco();
        conversa.limparSelecao();
        conversa.encerrarFluxo();
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        interpretador.mostrarInicio(contexto);
    }

    private void tentarNovamente(ContextoTelegram contexto, UUID relatorioId) {
        TrabalhoProcessamento trabalho =
                tentar.executar(contexto.professor().id(), relatorioId, Canal.TELEGRAM);
        etapa(contexto, contexto.mensagemCallbackId(), trabalho);
    }

    /** Botão "Continuar no chat" / "Continuar: {aluno}": revalida e põe o relatório em foco. */
    private boolean continuarNoChat(ContextoTelegram contexto, String argumento) {
        if (argumento == null || argumento.length() < 2) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(argumento.substring(1));
        } catch (IllegalArgumentException invalido) {
            return false;
        }
        contexto.conversa().limparConfirmacao();
        switch (argumento.charAt(0)) {
            case 'a' -> {
                contexto.conversa().descartarAudio();
                apresentar(contexto, id, null);
                return true;
            }
            case 't' -> {
                ConsultarTrabalhoCasoDeUso.VisaoTrabalho trabalho =
                        consultarTrabalho.executar(contexto.professor().id(), id);
                if (trabalho.trabalho().relatorioId() != null
                        && trabalho.trabalho().relatorioTipo() == TipoRelatorio.AULA) {
                    apresentar(contexto, trabalho.trabalho().relatorioId(), null);
                } else if (trabalho.trabalho().status().emCurso()) {
                    contexto.responder(
                            apresentador.simples(
                                    contexto.chatId(),
                                    "PROCESSAMENTO_EM_ANDAMENTO",
                                    Map.of(
                                            "etapa",
                                            apresentador
                                                    .formatacao()
                                                    .etapa(trabalho.trabalho().etapa().name()))));
                } else {
                    return false;
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void apresentar(ContextoTelegram contexto, UUID relatorioId, String prefixo) {
        List<SaidaTelegram> saidas =
                apresentacao.apresentar(
                        contexto.chatId(),
                        contexto.professor().id(),
                        relatorioId,
                        contexto.conversa(),
                        prefixo);
        saidas.forEach(contexto::responder);
    }

    private static Integer numero(String argumento) {
        try {
            return argumento == null ? null : Integer.valueOf(argumento);
        } catch (NumberFormatException invalido) {
            return null;
        }
    }
}
