package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso.Aprovacao;
import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.AprovarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.CancelarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.GerarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ListarSemestresDisponiveisCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ListarSemestresDisponiveisCasoDeUso.Semestre;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterPreviaSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterPreviaSemestralCasoDeUso.Previa;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterRelatorioSemestralCasoDeUso.VisaoRelatorioSemestral;
import br.com.felipeblaft.relatorios.casodeuso.semestral.SolicitarAlteracaoRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.cadastro.BuscaAluno;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.EtapaFluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.FluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.RelatorioFoco;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.MensagemEtapa;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.MensagemTelegram;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Relatório semestral pelo bot (sequência 5.4, estado #8): aluno, período, prévia, geração e a
 * conferência com alteração, aprovação e cancelamento — os mesmos casos de uso da Web.
 */
@Component
@Order(8)
public class FluxoSemestralTelegram implements FluxoTelegram {

    static final String ALUNO = "semAluno";
    static final String OPCOES = "semOpcoes";
    static final String PERIODO = "semPeriodo";

    private final ListarSemestresDisponiveisCasoDeUso listar;
    private final ObterPreviaSemestralCasoDeUso previa;
    private final GerarRelatorioSemestralCasoDeUso gerar;
    private final SolicitarAlteracaoRelatorioSemestralCasoDeUso alterar;
    private final AprovarRelatorioSemestralCasoDeUso aprovar;
    private final CancelarRelatorioSemestralCasoDeUso cancelar;
    private final BuscarAlunosCasoDeUso buscarAlunos;
    private final ApresentacaoRelatorioSemestralTelegram apresentacao;
    private final ApresentadorTelegram apresentador;
    private final SecoesRelatorioSemestral secoes;
    private final InterpretadorConversaTelegram interpretador;

    @SuppressWarnings("java:S107")
    public FluxoSemestralTelegram(
            ListarSemestresDisponiveisCasoDeUso listar,
            ObterPreviaSemestralCasoDeUso previa,
            GerarRelatorioSemestralCasoDeUso gerar,
            SolicitarAlteracaoRelatorioSemestralCasoDeUso alterar,
            AprovarRelatorioSemestralCasoDeUso aprovar,
            CancelarRelatorioSemestralCasoDeUso cancelar,
            BuscarAlunosCasoDeUso buscarAlunos,
            ApresentacaoRelatorioSemestralTelegram apresentacao,
            ApresentadorTelegram apresentador,
            SecoesRelatorioSemestral secoes,
            InterpretadorConversaTelegram interpretador) {
        this.listar = listar;
        this.previa = previa;
        this.gerar = gerar;
        this.alterar = alterar;
        this.aprovar = aprovar;
        this.cancelar = cancelar;
        this.buscarAlunos = buscarAlunos;
        this.apresentacao = apresentacao;
        this.apresentador = apresentador;
        this.secoes = secoes;
        this.interpretador = interpretador;
    }

    // ------------------------------------------------------------------ escolha do período

    @Override
    public boolean aoEscolherAluno(ContextoTelegram contexto, AlunoResumo aluno) {
        if (contexto.conversa().fluxo() != FluxoConversa.SEMESTRAL) {
            return false;
        }
        oferecerPeriodos(contexto, aluno);
        return true;
    }

    private void oferecerPeriodos(ContextoTelegram contexto, AlunoResumo aluno) {
        ConversaTelegram conversa = contexto.conversa();
        conversa.limparFoco();
        conversa.iniciarFluxo(FluxoConversa.SEMESTRAL, EtapaFluxoConversa.ESCOLHENDO_SEMESTRE);
        conversa.guardar(ALUNO, aluno.id().toString());
        List<Semestre> semestres = listar.executar(contexto.professor().id(), aluno.id());
        if (semestres.isEmpty()) {
            contexto.responder(apresentador.simples(contexto.chatId(), "SEM_SEMESTRES"));
            return;
        }
        conversa.guardar(
                OPCOES,
                String.join(",", semestres.stream().map(s -> s.periodo().chave()).toList()));
        StringBuilder texto =
                new StringBuilder(
                        apresentador.texto("ESCOLHER_SEMESTRE", Map.of("aluno", aluno.nome())));
        List<List<Botao>> linhas = new ArrayList<>();
        for (int i = 0; i < semestres.size(); i++) {
            Semestre semestre = semestres.get(i);
            String rotulo = secoes.rotuloPeriodo(semestre.periodo().chave());
            texto.append('\n')
                    .append(
                            apresentador.texto(
                                    "SEMESTRE_INTERVALO",
                                    Map.of(
                                            "rotulo", rotulo,
                                            "inicio",
                                                    apresentador
                                                            .formatacao()
                                                            .data(semestre.periodo().inicio()),
                                            "fim",
                                                    apresentador
                                                            .formatacao()
                                                            .data(semestre.periodo().fim()))));
            String botao =
                    (semestre.preSelecionado() ? "★ " : "")
                            + rotulo
                            + (semestre.emAndamento()
                                    ? " · " + apresentador.rotulo("SEMESTRE_EM_ANDAMENTO")
                                    : "");
            linhas.add(
                    List.of(
                            new Botao(
                                    botao,
                                    DadosCallbackCodificador.codificar(
                                            "per", Integer.toString(i)))));
        }
        linhas.add(List.of(apresentador.botao("BOTAO_INICIO", "inicio")));
        contexto.responder(new Mensagem(contexto.chatId(), texto.toString(), new Teclado(linhas)));
    }

    private Optional<UUID> aluno(ContextoTelegram contexto) {
        return contexto.conversa().dado(ALUNO).map(UUID::fromString);
    }

    private Optional<AlunoResumo> resumo(ContextoTelegram contexto, UUID alunoId) {
        return buscarAlunos.executar(contexto.professor().id(), "").alunos().stream()
                .filter(a -> a.id().equals(alunoId))
                .findFirst();
    }

    private boolean escolherPeriodo(ContextoTelegram contexto, String chave) {
        Optional<UUID> alunoId = aluno(contexto);
        if (alunoId.isEmpty()) {
            return false;
        }
        ConversaTelegram conversa = contexto.conversa();
        conversa.guardar(PERIODO, chave);
        conversa.definirEtapa(EtapaFluxoConversa.CONFIRMANDO_GERACAO_SEMESTRAL);
        Previa p = previa.executar(contexto.professor().id(), alunoId.get(), chave);
        String rotulo = secoes.rotuloPeriodo(chave);
        long chat = contexto.chatId();
        Botao voltar = apresentador.botao("BOTAO_VOLTAR", "voltar");
        if (p.semestralAprovadoId().isPresent()) {
            contexto.responder(
                    new Mensagem(
                            chat, apresentador.texto("SEMESTRAL_JA_APROVADO"), Teclado.de(voltar)));
        } else if (p.semestralEmAberto().isPresent()) {
            conversa.definirEtapa(EtapaFluxoConversa.AVISO_RELATORIO_EM_ABERTO);
            contexto.responder(apresentador.avisoEmAberto(chat, p.semestralEmAberto().get()));
        } else if (p.aulasAprovadas() == 0) {
            contexto.responder(
                    new Mensagem(
                            chat,
                            apresentador.texto("SEM_AULAS_APROVADAS_NO_PERIODO"),
                            Teclado.de(voltar)));
        } else {
            String texto =
                    apresentador.texto(
                            "SEMESTRAL_PREVIA",
                            Map.of("rotulo", rotulo, "quantidade", p.aulasAprovadas()));
            if (p.relatoriosAulaEmAberto() > 0) {
                texto +=
                        "\n"
                                + apresentador.texto(
                                        "HA_RELATORIOS_EM_ABERTO",
                                        Map.of("quantidade", p.relatoriosAulaEmAberto()));
                contexto.responder(
                        new Mensagem(
                                chat,
                                texto,
                                Teclado.de(
                                        apresentador.botao("BOTAO_GERAR_MESMO_ASSIM", "gerarsim"),
                                        voltar)));
            } else {
                contexto.responder(
                        new Mensagem(
                                chat,
                                texto,
                                Teclado.de(
                                        apresentador.botao("BOTAO_GERAR_SEMESTRAL", "gerar"),
                                        voltar)));
            }
        }
        return true;
    }

    private void gerar(ContextoTelegram contexto, boolean mesmoComEmAberto) {
        UUID alunoId = aluno(contexto).orElseThrow();
        String chave = contexto.conversa().dado(PERIODO).orElseThrow();
        TrabalhoProcessamento trabalho =
                gerar.executar(
                        contexto.professor().id(),
                        alunoId,
                        chave,
                        mesmoComEmAberto,
                        Canal.TELEGRAM);
        contexto.conversa().encerrarFluxo();
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        contexto.mensagemCallbackId(),
                        FluxoRelatorioAulaTelegram.textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }

    // ------------------------------------------------------------------ semestral em foco

    private Optional<VisaoRelatorioSemestral> foco(ContextoTelegram contexto) {
        ConversaTelegram conversa = contexto.conversa();
        Optional<RelatorioFoco> foco =
                conversa.relatorioFoco().filter(f -> f.tipo() == TipoRelatorio.SEMESTRAL);
        if (foco.isEmpty()) {
            return Optional.empty();
        }
        try {
            VisaoRelatorioSemestral visao =
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

    private int versaoBase(ContextoTelegram contexto, RelatorioSemestral relatorio) {
        Integer apresentada = contexto.conversa().versaoApresentada();
        return apresentada == null ? relatorio.versaoVigente() : apresentada;
    }

    @Override
    public boolean aoTexto(ContextoTelegram contexto, String texto) {
        Optional<VisaoRelatorioSemestral> foco = foco(contexto);
        if (foco.isPresent()) {
            VisaoRelatorioSemestral visao = foco.get();
            if (ApresentacaoRelatorioSemestralTelegram.emProcessamento(visao)) {
                contexto.responder(
                        new Mensagem(
                                contexto.chatId(),
                                apresentacao.processando(visao),
                                Teclado.NENHUM));
                return true;
            }
            RelatorioSemestral relatorio = visao.relatorio();
            comVersaoVigente(
                    contexto,
                    relatorio.id(),
                    () ->
                            etapa(
                                    contexto,
                                    alterar.alterarPorTexto(
                                            contexto.professor().id(),
                                            relatorio.id(),
                                            texto,
                                            versaoBase(contexto, relatorio),
                                            Canal.TELEGRAM)));
            return true;
        }
        ConversaTelegram conversa = contexto.conversa();
        if (conversa.fluxo() == FluxoConversa.SEMESTRAL
                && conversa.etapa() == EtapaFluxoConversa.ESCOLHENDO_SEMESTRE) {
            String normalizado = BuscaAluno.normalizar(texto);
            for (String chave : opcoes(conversa)) {
                if (normalizado.equals(chave)
                        || normalizado.equals(BuscaAluno.normalizar(secoes.rotuloPeriodo(chave)))) {
                    return escolherPeriodo(contexto, chave);
                }
            }
        }
        return false;
    }

    @Override
    public boolean aoAudio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        Optional<VisaoRelatorioSemestral> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        VisaoRelatorioSemestral visao = foco.get();
        if (ApresentacaoRelatorioSemestralTelegram.emProcessamento(visao)) {
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(), apresentacao.processando(visao), Teclado.NENHUM));
            return true;
        }
        RelatorioSemestral relatorio = visao.relatorio();
        comVersaoVigente(
                contexto,
                relatorio.id(),
                () ->
                        etapa(
                                contexto,
                                alterar.alterarPorAudio(
                                        contexto.professor().id(),
                                        relatorio.id(),
                                        FluxoRegistroAulaTelegram.referencia(mensagem, arquivo),
                                        versaoBase(contexto, relatorio),
                                        Canal.TELEGRAM)));
        return true;
    }

    private void comVersaoVigente(ContextoTelegram contexto, UUID relatorioId, Runnable acao) {
        try {
            acao.run();
        } catch (ExcecaoDominio erro) {
            if (erro.codigo() != CodigoErro.VERSAO_DESATUALIZADA) {
                throw erro;
            }
            contexto.responder(
                    apresentador.erro(contexto.chatId(), erro.codigo(), erro.detalhes()));
            apresentar(contexto, relatorioId);
        }
    }

    private void etapa(ContextoTelegram contexto, TrabalhoProcessamento trabalho) {
        contexto.responder(
                new MensagemEtapa(
                        contexto.chatId(),
                        null,
                        FluxoRelatorioAulaTelegram.textoEtapa(apresentador, trabalho),
                        trabalho.id()));
    }

    private void apresentar(ContextoTelegram contexto, UUID relatorioId) {
        apresentacao
                .apresentar(
                        contexto.chatId(),
                        contexto.professor().id(),
                        relatorioId,
                        contexto.conversa(),
                        null)
                .forEach(contexto::responder);
    }

    private List<String> opcoes(ConversaTelegram conversa) {
        return conversa.dado(OPCOES).map(s -> List.of(s.split(","))).orElse(List.of());
    }

    // ------------------------------------------------------------------ botões e comandos

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        ConversaTelegram conversa = contexto.conversa();
        boolean noFluxo = conversa.fluxo() == FluxoConversa.SEMESTRAL;
        switch (acao) {
            case "per" -> {
                List<String> opcoes = opcoes(conversa);
                try {
                    int indice = Integer.parseInt(argumento);
                    if (!noFluxo || indice < 0 || indice >= opcoes.size()) {
                        return false;
                    }
                    conversa.limparConfirmacao();
                    return escolherPeriodo(contexto, opcoes.get(indice));
                } catch (NumberFormatException invalido) {
                    return false;
                }
            }
            case "gerar", "gerarsim" -> {
                if (!noFluxo
                        || conversa.etapa() != EtapaFluxoConversa.CONFIRMANDO_GERACAO_SEMESTRAL) {
                    return false;
                }
                gerar(contexto, acao.equals("gerarsim"));
                return true;
            }
            case "voltar" -> {
                Optional<AlunoResumo> aluno = aluno(contexto).flatMap(id -> resumo(contexto, id));
                if (!noFluxo || aluno.isEmpty()) {
                    return false;
                }
                oferecerPeriodos(contexto, aluno.get());
                return true;
            }
            case "cont" -> {
                return noFluxo
                        && conversa.etapa() == EtapaFluxoConversa.AVISO_RELATORIO_EM_ABERTO
                        && continuar(contexto);
            }
            case "desc" -> {
                if (!noFluxo || conversa.etapa() != EtapaFluxoConversa.AVISO_RELATORIO_EM_ABERTO) {
                    return false;
                }
                pedirDescarte(contexto);
                return true;
            }
            case "descok" -> {
                if (!noFluxo
                        || !conversa.confirmacaoPendente()
                                .filter(FluxoRetomadaTelegram.CONFIRMACAO_DESCARTE::equals)
                                .isPresent()) {
                    return false;
                }
                conversa.limparConfirmacao();
                return descartar(contexto);
            }
            case "foco" -> {
                return continuarNoChat(contexto, argumento);
            }
            case "aprov", "aprovok", "alt", "canc", "cancok", "pdf" -> {
                Optional<VisaoRelatorioSemestral> foco = foco(contexto);
                return foco.isPresent() && acaoNoFoco(contexto, foco.get(), acao, argumento);
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean aoComando(ContextoTelegram contexto, Comando comando) {
        ConversaTelegram conversa = contexto.conversa();
        if (conversa.fluxo() == FluxoConversa.SEMESTRAL
                && conversa.etapa() == EtapaFluxoConversa.AVISO_RELATORIO_EM_ABERTO) {
            if (comando == Comando.CONTINUAR) {
                return continuar(contexto);
            }
            if (comando == Comando.DESCARTAR) {
                pedirDescarte(contexto);
                return true;
            }
        }
        if (comando != Comando.APROVAR && comando != Comando.CANCELAR) {
            return false;
        }
        Optional<VisaoRelatorioSemestral> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        if (comando == Comando.APROVAR) {
            pedirAprovacao(contexto, foco.get(), versaoBase(contexto, foco.get().relatorio()));
        } else {
            pedirCancelamento(contexto);
        }
        return true;
    }

    @Override
    public boolean aoConfirmar(ContextoTelegram contexto, String confirmacao) {
        ConversaTelegram conversa = contexto.conversa();
        if (FluxoRetomadaTelegram.CONFIRMACAO_DESCARTE.equals(confirmacao)
                && conversa.fluxo() == FluxoConversa.SEMESTRAL) {
            return descartar(contexto);
        }
        Optional<VisaoRelatorioSemestral> foco = foco(contexto);
        if (foco.isEmpty()) {
            return false;
        }
        UUID id = foco.get().relatorio().id();
        if (FluxoRelatorioAulaTelegram.CONFIRMACAO_CANCELAR.equals(confirmacao)) {
            cancelarSemestral(contexto, id);
            return true;
        }
        if (confirmacao.startsWith(FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR)) {
            try {
                aprovarVersao(
                        contexto,
                        id,
                        Integer.parseInt(
                                confirmacao.substring(
                                        FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR.length())));
                return true;
            } catch (NumberFormatException invalido) {
                return false;
            }
        }
        return false;
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        if (foco(contexto).isPresent()) {
            return apresentador.rotulo("RECEBER_ALTERACAO");
        }
        return null;
    }

    private boolean acaoNoFoco(
            ContextoTelegram contexto,
            VisaoRelatorioSemestral visao,
            String acao,
            String argumento) {
        ConversaTelegram conversa = contexto.conversa();
        RelatorioSemestral relatorio = visao.relatorio();
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
                                .filter(
                                        (FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR + versao)
                                                ::equals)
                                .isPresent()) {
                    return false;
                }
                conversa.limparConfirmacao();
                aprovarVersao(contexto, relatorio.id(), versao);
            }
            case "cancok" -> {
                if (!conversa.confirmacaoPendente()
                        .filter(FluxoRelatorioAulaTelegram.CONFIRMACAO_CANCELAR::equals)
                        .isPresent()) {
                    return false;
                }
                conversa.limparConfirmacao();
                cancelarSemestral(contexto, relatorio.id());
            }
            case "canc" -> {
                conversa.limparConfirmacao();
                pedirCancelamento(contexto);
            }
            case "alt" ->
                    contexto.responder(
                            apresentador.simples(contexto.chatId(), "PEDIDO_ALTERACAO_ROTULO"));
            default ->
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
        return true;
    }

    private void pedirAprovacao(
            ContextoTelegram contexto, VisaoRelatorioSemestral visao, int versao) {
        RelatorioSemestral relatorio = visao.relatorio();
        if (ApresentacaoRelatorioSemestralTelegram.emProcessamento(visao)) {
            throw new ExcecaoDominio(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        if (versao != relatorio.versaoVigente()) {
            contexto.responder(
                    apresentador.erro(
                            contexto.chatId(),
                            CodigoErro.VERSAO_DESATUALIZADA,
                            Map.of("versaoVigente", relatorio.versaoVigente())));
            apresentar(contexto, relatorio.id());
            return;
        }
        contexto.conversa()
                .pedirConfirmacao(FluxoRelatorioAulaTelegram.CONFIRMACAO_APROVAR + versao);
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
        Aprovacao aprovacao;
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
            apresentar(contexto, relatorioId);
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
                                apresentador.botao("BOTAO_HISTORICO", "hist"),
                                apresentador.botao("BOTAO_INICIO", "inicio"))));
    }

    private void pedirCancelamento(ContextoTelegram contexto) {
        contexto.conversa().pedirConfirmacao(FluxoRelatorioAulaTelegram.CONFIRMACAO_CANCELAR);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_CANCELAMENTO"),
                        apresentador.botao("BOTAO_CONFIRMAR_CANCELAMENTO", "cancok")));
    }

    private void cancelarSemestral(ContextoTelegram contexto, UUID relatorioId) {
        cancelar.cancelar(contexto.professor().id(), relatorioId);
        ConversaTelegram conversa = contexto.conversa();
        conversa.limparFoco();
        conversa.limparSelecao();
        conversa.encerrarFluxo();
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        interpretador.mostrarInicio(contexto);
    }

    // ------------------------------------------------------------------ retomada do semestral

    private Optional<RelatorioEmAberto> emAberto(ContextoTelegram contexto) {
        Optional<UUID> alunoId = aluno(contexto);
        Optional<String> chave = contexto.conversa().dado(PERIODO);
        if (alunoId.isEmpty() || chave.isEmpty()) {
            return Optional.empty();
        }
        return previa.executar(contexto.professor().id(), alunoId.get(), chave.get())
                .semestralEmAberto();
    }

    private boolean continuar(ContextoTelegram contexto) {
        Optional<RelatorioEmAberto> aberto = emAberto(contexto);
        contexto.conversa().encerrarFluxo();
        if (aberto.isEmpty()) {
            interpretador.mostrarInicio(contexto);
            return true;
        }
        if (aberto.get().relatorioId() != null) {
            apresentar(contexto, aberto.get().relatorioId());
        } else {
            String etapa =
                    aberto.get().etapa() == null ? "CONSOLIDANDO" : aberto.get().etapa().name();
            contexto.responder(
                    apresentador.simples(
                            contexto.chatId(),
                            "PROCESSAMENTO_EM_ANDAMENTO",
                            Map.of("etapa", apresentador.formatacao().etapa(etapa))));
        }
        return true;
    }

    private void pedirDescarte(ContextoTelegram contexto) {
        contexto.conversa().pedirConfirmacao(FluxoRetomadaTelegram.CONFIRMACAO_DESCARTE);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_DESCARTE"),
                        apresentador.botao("BOTAO_CONFIRMAR_DESCARTE", "descok")));
    }

    private boolean descartar(ContextoTelegram contexto) {
        Optional<RelatorioEmAberto> aberto = emAberto(contexto);
        UUID professorId = contexto.professor().id();
        aberto.ifPresent(
                a -> {
                    if (a.relatorioId() != null) {
                        cancelar.cancelar(professorId, a.relatorioId());
                    } else if (a.trabalhoId() != null) {
                        cancelar.descartarConsolidacao(professorId, a.trabalhoId());
                    }
                });
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        Optional<String> chave = contexto.conversa().dado(PERIODO);
        return chave.map(c -> escolherPeriodo(contexto, c)).orElse(true);
    }

    /** Botão "Continuar: {aluno}" de um semestral (argumento {@code s<UUID>}). */
    private boolean continuarNoChat(ContextoTelegram contexto, String argumento) {
        if (argumento == null || argumento.length() < 2 || argumento.charAt(0) != 's') {
            return false;
        }
        try {
            UUID id = UUID.fromString(argumento.substring(1));
            contexto.conversa().limparConfirmacao();
            apresentar(contexto, id);
            return true;
        } catch (IllegalArgumentException invalido) {
            return false;
        }
    }

    private static Integer numero(String argumento) {
        try {
            return argumento == null ? null : Integer.valueOf(argumento);
        } catch (NumberFormatException invalido) {
            return null;
        }
    }
}
