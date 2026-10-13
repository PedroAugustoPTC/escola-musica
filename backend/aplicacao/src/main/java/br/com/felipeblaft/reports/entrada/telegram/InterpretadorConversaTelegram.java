package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.StudentSearch.ResultType;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.entrada.telegram.DadosCallbackCodificador.DadosCallback;
import br.com.felipeblaft.reports.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram.CallbackTelegram;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram.MensagemTelegram;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.access.EndTelegramLinkUseCase;
import br.com.felipeblaft.reports.usecase.access.IdentifyTelegramTeacherUseCase;
import br.com.felipeblaft.reports.usecase.access.LinkTelegramUseCase;
import br.com.felipeblaft.reports.usecase.access.UpdateTelegramConversationUseCase;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Interpreta cada update a partir do estado persistido (FR-036) e traduz para os mesmos casos de
 * uso da Web (Princípio I). A ordem de decisão segue a tabela da seção 4 de
 * contracts/telegram-conversa.md: não vinculado (#1), confirmação pendente (#2), palavras-chave,
 * estados com relatório em foco ou áudio pendente (fluxos), e por fim a busca de aluno (#10).
 */
@Component
public class InterpretadorConversaTelegram {

    public static final String OPCOES_ALUNOS = "opcoes";
    public static final String CONFIRMACAO_SAIR = "sair";

    private final IdentifyTelegramTeacherUseCase identificar;
    private final LinkTelegramUseCase vincular;
    private final EndTelegramLinkUseCase encerrarVinculo;
    private final UpdateTelegramConversationUseCase atualizarConversa;
    private final GetTeacherHomeUseCase obterInicio;
    private final SearchStudentsUseCase buscarAlunos;
    private final ApresentadorTelegram apresentador;
    private final ClockPort relogio;
    private final BusinessParameters parametros;
    private final ObjectProvider<FluxoTelegram> provedorFluxos;
    private volatile List<FluxoTelegram> fluxos;

    public InterpretadorConversaTelegram(
            IdentifyTelegramTeacherUseCase identificar,
            LinkTelegramUseCase vincular,
            EndTelegramLinkUseCase encerrarVinculo,
            UpdateTelegramConversationUseCase atualizarConversa,
            GetTeacherHomeUseCase obterInicio,
            SearchStudentsUseCase buscarAlunos,
            ApresentadorTelegram apresentador,
            ClockPort relogio,
            BusinessParameters parametros,
            ObjectProvider<FluxoTelegram> provedorFluxos) {
        this.identificar = identificar;
        this.vincular = vincular;
        this.encerrarVinculo = encerrarVinculo;
        this.atualizarConversa = atualizarConversa;
        this.obterInicio = obterInicio;
        this.buscarAlunos = buscarAlunos;
        this.apresentador = apresentador;
        this.relogio = relogio;
        this.parametros = parametros;
        this.provedorFluxos = provedorFluxos;
    }

    /**
     * Fluxos resolvidos sob demanda: eles também usam este interpretador (sem ciclo na criação).
     */
    private List<FluxoTelegram> fluxos() {
        List<FluxoTelegram> atuais = fluxos;
        if (atuais == null) {
            atuais = provedorFluxos.orderedStream().toList();
            fluxos = atuais;
        }
        return atuais;
    }

    public List<SaidaTelegram> interpretar(UpdateTelegram update) {
        if (update.callbackQuery() != null) {
            return callback(update.callbackQuery());
        }
        if (update.message() != null && update.message().from() != null) {
            return mensagem(update.message());
        }
        return List.of();
    }

    // ------------------------------------------------------------------ mensagens

    private List<SaidaTelegram> mensagem(MensagemTelegram mensagem) {
        ContextoTelegram contexto =
                new ContextoTelegram(mensagem.from().id(), mensagem.chat().id(), null);
        if (!mensagem.chat().privado()) {
            contexto.responder(apresentador.simples(contexto.chatId(), "SOMENTE_CONVERSA_PRIVADA"));
            return contexto.saidas();
        }
        carregar(contexto);
        try {
            if (!contexto.vinculado()) {
                naoVinculado(contexto, mensagem);
            } else {
                vinculado(contexto, mensagem);
            }
        } catch (DomainException erro) {
            contexto.responder(apresentador.erro(contexto.chatId(), erro.code(), erro.details()));
        } finally {
            gravar(contexto);
        }
        return contexto.saidas();
    }

    private void carregar(ContextoTelegram contexto) {
        identificar.run(contexto.usuarioId()).ifPresent(contexto::identificar);
        if (contexto.vinculado() && contexto.vinculo().chatId() != contexto.chatId()) {
            contexto.vinculo().updateChat(contexto.chatId(), relogio.now());
        }
    }

    private void gravar(ContextoTelegram contexto) {
        if (contexto.vinculado()) {
            atualizarConversa.run(contexto.vinculo());
        }
    }

    /** Estado #1: não vinculado. Texto com cara de código é tentativa de vinculação (FR-004). */
    private void naoVinculado(ContextoTelegram contexto, MensagemTelegram mensagem) {
        String texto = mensagem.text();
        boolean comando = texto != null && PalavrasChaveTelegram.identificar(texto).isPresent();
        if (texto == null || comando || !AccessCode.looksLikeCode(texto)) {
            contexto.responder(apresentador.simples(contexto.chatId(), "CODIGO_NECESSARIO"));
            return;
        }
        LinkTelegramUseCase.Result resultado =
                vincular.run(contexto.usuarioId(), contexto.chatId(), texto);
        identificar.run(contexto.usuarioId()).ifPresent(contexto::identificar);
        String prefixo =
                apresentador.texto(
                        "VINCULO_CRIADO", Map.of("nome", resultado.teacher().firstName()));
        contexto.responder(
                apresentador.inicio(
                        contexto.chatId(), obterInicio.run(resultado.teacher()), prefixo));
    }

    private void vinculado(ContextoTelegram contexto, MensagemTelegram mensagem) {
        TelegramConversation conversa = contexto.conversa();
        if (conversa.discardAudioIfExpired(relogio.now(), parametros.pendingAudioExpiry())) {
            contexto.responder(apresentador.simples(contexto.chatId(), "AUDIO_PENDENTE_EXPIRADO"));
        }
        ArquivoTelegram arquivo = mensagem.arquivoDeAudio();
        if (mensagem.text() != null) {
            texto(contexto, mensagem.text().strip());
        } else if (arquivo != null) {
            audio(contexto, mensagem, arquivo);
        } else {
            tipoNaoSuportado(contexto);
        }
    }

    private void texto(ContextoTelegram contexto, String texto) {
        TelegramConversation conversa = contexto.conversa();
        Optional<Comando> comando = PalavrasChaveTelegram.identificar(texto);
        Optional<String> confirmacao = conversa.pendingConfirmation();
        if (confirmacao.isPresent()) {
            conversa.cleanConfirmation();
            if (comando.orElse(null) == Comando.SIM) {
                confirmar(contexto, confirmacao.get());
                return;
            }
            // qualquer outro texto desiste da confirmação e é reinterpretado (estado #2)
        }
        if (comando.isPresent() && comando.get() != Comando.SIM) {
            comando(contexto, comando.get());
            return;
        }
        for (FluxoTelegram fluxo : fluxos()) {
            if (fluxo.aoTexto(contexto, texto)) {
                return;
            }
        }
        buscarAluno(contexto, texto);
    }

    private void audio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        contexto.conversa().cleanConfirmation();
        for (FluxoTelegram fluxo : fluxos()) {
            if (fluxo.aoAudio(contexto, mensagem, arquivo)) {
                return;
            }
        }
        tipoNaoSuportado(contexto);
    }

    public void tipoNaoSuportado(ContextoTelegram contexto) {
        String oQuePossoReceber = null;
        if (!contexto.vinculado()) {
            oQuePossoReceber = apresentador.rotulo("RECEBER_CODIGO");
        } else if (contexto.conversa().pendingConfirmation().isPresent()) {
            oQuePossoReceber = apresentador.rotulo("RECEBER_CONFIRMACAO");
        } else {
            for (FluxoTelegram fluxo : fluxos()) {
                oQuePossoReceber = fluxo.oQuePossoReceber(contexto);
                if (oQuePossoReceber != null) {
                    break;
                }
            }
            if (oQuePossoReceber == null) {
                oQuePossoReceber =
                        contexto.conversa().step() == ConversationFlowStep.ESCOLHENDO_ALUNO
                                ? apresentador.rotulo("RECEBER_ALUNO")
                                : apresentador.rotulo("RECEBER_INICIO");
            }
        }
        contexto.responder(
                apresentador.simples(
                        contexto.chatId(),
                        "TIPO_NAO_SUPORTADO",
                        Map.of("oQuePossoReceber", oQuePossoReceber)));
    }

    // ------------------------------------------------------------------ comandos

    private void comando(ContextoTelegram contexto, Comando comando) {
        TelegramConversation conversa = contexto.conversa();
        switch (comando) {
            case INICIO -> {
                conversa.endFlow();
                conversa.cleanFocus();
                conversa.discardAudio();
                mostrarInicio(contexto);
            }
            case REGISTRAR_AULA -> iniciarEscolhaDeAluno(contexto, ConversationFlow.REGISTRO_AULA);
            case SEMESTRAL -> iniciarEscolhaDeAluno(contexto, ConversationFlow.SEMESTRAL);
            case HISTORICO -> iniciarEscolhaDeAluno(contexto, ConversationFlow.HISTORICO);
            case SAIR -> {
                conversa.askConfirmation(CONFIRMACAO_SAIR);
                contexto.responder(
                        apresentador.confirmacao(
                                contexto.chatId(),
                                apresentador.texto("CONFIRMAR_SAIR"),
                                apresentador.botao("BOTAO_CONFIRMAR_SAIR", "sairok")));
            }
            case AJUDA ->
                    contexto.responder(
                            new SaidaTelegram.Mensagem(
                                    contexto.chatId(),
                                    apresentador.texto("AJUDA"),
                                    apresentador.acoesInicio()));
            default -> {
                for (FluxoTelegram fluxo : fluxos()) {
                    if (fluxo.aoComando(contexto, comando)) {
                        return;
                    }
                }
                contexto.responder(
                        apresentador.erro(
                                contexto.chatId(),
                                ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA,
                                Map.of()));
            }
        }
    }

    private void confirmar(ContextoTelegram contexto, String confirmacao) {
        if (CONFIRMACAO_SAIR.equals(confirmacao)) {
            encerrarVinculo.run(contexto.usuarioId());
            contexto.marcarVinculoEncerrado();
            contexto.responder(apresentador.simples(contexto.chatId(), "VINCULO_ENCERRADO"));
            return;
        }
        for (FluxoTelegram fluxo : fluxos()) {
            if (fluxo.aoConfirmar(contexto, confirmacao)) {
                return;
            }
        }
        contexto.responder(apresentador.simples(contexto.chatId(), "ACAO_EXPIRADA"));
        mostrarInicio(contexto);
    }

    // ------------------------------------------------------------------ início e alunos

    public void mostrarInicio(ContextoTelegram contexto) {
        contexto.responder(
                apresentador.inicio(
                        contexto.chatId(), obterInicio.run(contexto.professor()), null));
    }

    /**
     * Começa um fluxo pela escolha do aluno ("Para qual aluno?"). Limpa o foco, sem cancelar nada.
     */
    public void iniciarEscolhaDeAluno(ContextoTelegram contexto, ConversationFlow fluxo) {
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.discardAudio();
        conversa.startFlow(fluxo, ConversationFlowStep.ESCOLHENDO_ALUNO);
        SearchStudentsUseCase.Result todos = buscarAlunos.run(contexto.professor().id(), "");
        if (todos.students().isEmpty()) {
            contexto.responder(apresentador.simples(contexto.chatId(), "SEM_ALUNOS"));
            return;
        }
        oferecerAlunos(contexto, apresentador.texto("ESCOLHER_ALUNO"), todos.students());
    }

    /** Lista de alunos como botões; as opções ficam na conversa para validar o toque. */
    public void oferecerAlunos(
            ContextoTelegram contexto, String cabecalho, List<StudentSummary> alunos) {
        TelegramConversation conversa = contexto.conversa();
        conversa.defineStep(ConversationFlowStep.ESCOLHENDO_ALUNO);
        conversa.store(
                OPCOES_ALUNOS,
                String.join(",", alunos.stream().map(a -> a.id().toString()).toList()));
        contexto.responder(apresentador.listaAlunos(contexto.chatId(), cabecalho, alunos));
    }

    /** Estado #10 (e #7 a #9): o texto é o nome, ou parte do nome, de um aluno (FR-013). */
    private void buscarAluno(ContextoTelegram contexto, String texto) {
        SearchStudentsUseCase.Result resultado = buscarAlunos.run(contexto.professor().id(), texto);
        if (resultado.students().isEmpty()) {
            contexto.responder(apresentador.simples(contexto.chatId(), "SEM_ALUNOS"));
            return;
        }
        if (resultado.type() == ResultType.ENCONTRADOS && resultado.students().size() == 1) {
            escolherAluno(contexto, resultado.students().getFirst());
        } else if (resultado.type() == ResultType.ENCONTRADOS) {
            oferecerAlunos(
                    contexto,
                    apresentador.texto("VARIOS_ALUNOS_ENCONTRADOS"),
                    resultado.students());
        } else if (resultado.type() == ResultType.NENHUM) {
            oferecerAlunos(
                    contexto,
                    apresentador.texto("NENHUM_ALUNO_ENCONTRADO", Map.of("busca", texto)),
                    resultado.students());
        } else {
            oferecerAlunos(contexto, apresentador.texto("ESCOLHER_ALUNO"), resultado.students());
        }
    }

    public void escolherAluno(ContextoTelegram contexto, StudentSummary aluno) {
        for (FluxoTelegram fluxo : fluxos()) {
            if (fluxo.aoEscolherAluno(contexto, aluno)) {
                return;
            }
        }
        selecionarParaRegistro(contexto, aluno);
    }

    /** Aluno selecionado para registrar aula: pede o áudio (sequência 5.2, passo 2). */
    public void selecionarParaRegistro(ContextoTelegram contexto, StudentSummary aluno) {
        TelegramConversation conversa = contexto.conversa();
        conversa.cleanFocus();
        conversa.selectStudent(aluno.id());
        conversa.startFlow(ConversationFlow.REGISTRO_AULA, ConversationFlowStep.AGUARDANDO_AUDIO);
        contexto.responder(
                apresentador.simples(
                        contexto.chatId(),
                        "ALUNO_SELECIONADO",
                        Map.of(
                                "data",
                                apresentador.formatacao().data(relogio.todayInSchoolTimezone()),
                                "aluno",
                                aluno.name(),
                                "limiteMb",
                                parametros.audioLimitMb())));
    }

    // ------------------------------------------------------------------ botões

    private List<SaidaTelegram> callback(CallbackTelegram callback) {
        long chatId =
                callback.message() != null && callback.message().chat() != null
                        ? callback.message().chat().id()
                        : callback.from().id();
        Long mensagemId = callback.message() != null ? callback.message().messageId() : null;
        ContextoTelegram contexto = new ContextoTelegram(callback.from().id(), chatId, mensagemId);
        contexto.responder(new SaidaTelegram.RespostaCallback(callback.id()));
        carregar(contexto);
        try {
            if (!contexto.vinculado()) {
                contexto.responder(apresentador.simples(chatId, "CODIGO_NECESSARIO"));
                return contexto.saidas();
            }
            Optional<DadosCallback> dados = DadosCallbackCodificador.decodificar(callback.data());
            if (dados.isEmpty() || !tratarCallback(contexto, dados.get())) {
                acaoExpirada(contexto);
            }
        } catch (DomainException erro) {
            contexto.responder(apresentador.erro(chatId, erro.code(), erro.details()));
        } finally {
            gravar(contexto);
        }
        return contexto.saidas();
    }

    public void acaoExpirada(ContextoTelegram contexto) {
        contexto.responder(apresentador.simples(contexto.chatId(), "ACAO_EXPIRADA"));
        mostrarInicio(contexto);
    }

    private boolean tratarCallback(ContextoTelegram contexto, DadosCallback dados) {
        TelegramConversation conversa = contexto.conversa();
        switch (dados.acao()) {
            case "inicio" -> {
                conversa.cleanConfirmation();
                comando(contexto, Comando.INICIO);
                return true;
            }
            case "reg" -> {
                conversa.cleanConfirmation();
                iniciarEscolhaDeAluno(contexto, ConversationFlow.REGISTRO_AULA);
                return true;
            }
            case "sem" -> {
                conversa.cleanConfirmation();
                iniciarEscolhaDeAluno(contexto, ConversationFlow.SEMESTRAL);
                return true;
            }
            case "hist" -> {
                conversa.cleanConfirmation();
                iniciarEscolhaDeAluno(contexto, ConversationFlow.HISTORICO);
                return true;
            }
            case "sairok" -> {
                if (!conversa.pendingConfirmation().filter(CONFIRMACAO_SAIR::equals).isPresent()) {
                    return false;
                }
                conversa.cleanConfirmation();
                confirmar(contexto, CONFIRMACAO_SAIR);
                return true;
            }
            case "aluno" -> {
                return alunoEscolhidoNaLista(contexto, dados.argumento());
            }
            default -> {
                for (FluxoTelegram fluxo : fluxos()) {
                    if (fluxo.aoCallback(contexto, dados.acao(), dados.argumento())) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private boolean alunoEscolhidoNaLista(ContextoTelegram contexto, String argumento) {
        TelegramConversation conversa = contexto.conversa();
        if (conversa.step() != ConversationFlowStep.ESCOLHENDO_ALUNO) {
            return false;
        }
        Optional<UUID> alunoId = opcao(conversa, argumento);
        if (alunoId.isEmpty()) {
            return false;
        }
        conversa.cleanConfirmation();
        SearchStudentsUseCase.Result todos = buscarAlunos.run(contexto.professor().id(), "");
        Optional<StudentSummary> aluno =
                todos.students().stream().filter(a -> a.id().equals(alunoId.get())).findFirst();
        if (aluno.isEmpty()) {
            return false;
        }
        escolherAluno(contexto, aluno.get());
        return true;
    }

    static Optional<UUID> opcao(TelegramConversation conversa, String argumento) {
        try {
            int indice = Integer.parseInt(argumento);
            List<String> opcoes =
                    conversa.data(OPCOES_ALUNOS).map(s -> List.of(s.split(","))).orElse(List.of());
            if (indice < 0 || indice >= opcoes.size() || opcoes.get(indice).isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(opcoes.get(indice)));
        } catch (IllegalArgumentException erro) {
            return Optional.empty();
        }
    }
}
