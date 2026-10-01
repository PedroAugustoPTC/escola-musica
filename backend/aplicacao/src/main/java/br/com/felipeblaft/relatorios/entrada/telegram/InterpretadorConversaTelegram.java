package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.acesso.AtualizarConversaTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EncerrarVinculoTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.IdentificarProfessorTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.VincularTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterInicioProfessorCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.BuscaAluno.TipoResultado;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.EtapaFluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.FluxoConversa;
import br.com.felipeblaft.relatorios.entrada.telegram.DadosCallbackCodificador.DadosCallback;
import br.com.felipeblaft.relatorios.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.CallbackTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.MensagemTelegram;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
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

    private final IdentificarProfessorTelegramCasoDeUso identificar;
    private final VincularTelegramCasoDeUso vincular;
    private final EncerrarVinculoTelegramCasoDeUso encerrarVinculo;
    private final AtualizarConversaTelegramCasoDeUso atualizarConversa;
    private final ObterInicioProfessorCasoDeUso obterInicio;
    private final BuscarAlunosCasoDeUso buscarAlunos;
    private final ApresentadorTelegram apresentador;
    private final RelogioPorta relogio;
    private final ParametrosNegocio parametros;
    private final ObjectProvider<FluxoTelegram> provedorFluxos;
    private volatile List<FluxoTelegram> fluxos;

    public InterpretadorConversaTelegram(
            IdentificarProfessorTelegramCasoDeUso identificar,
            VincularTelegramCasoDeUso vincular,
            EncerrarVinculoTelegramCasoDeUso encerrarVinculo,
            AtualizarConversaTelegramCasoDeUso atualizarConversa,
            ObterInicioProfessorCasoDeUso obterInicio,
            BuscarAlunosCasoDeUso buscarAlunos,
            ApresentadorTelegram apresentador,
            RelogioPorta relogio,
            ParametrosNegocio parametros,
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
        } catch (ExcecaoDominio erro) {
            contexto.responder(
                    apresentador.erro(contexto.chatId(), erro.codigo(), erro.detalhes()));
        } finally {
            gravar(contexto);
        }
        return contexto.saidas();
    }

    private void carregar(ContextoTelegram contexto) {
        identificar.executar(contexto.usuarioId()).ifPresent(contexto::identificar);
        if (contexto.vinculado() && contexto.vinculo().chatId() != contexto.chatId()) {
            contexto.vinculo().atualizarChat(contexto.chatId(), relogio.agora());
        }
    }

    private void gravar(ContextoTelegram contexto) {
        if (contexto.vinculado()) {
            atualizarConversa.executar(contexto.vinculo());
        }
    }

    /** Estado #1: não vinculado. Texto com cara de código é tentativa de vinculação (FR-004). */
    private void naoVinculado(ContextoTelegram contexto, MensagemTelegram mensagem) {
        String texto = mensagem.text();
        boolean comando = texto != null && PalavrasChaveTelegram.identificar(texto).isPresent();
        if (texto == null || comando || !CodigoAcesso.pareceCodigo(texto)) {
            contexto.responder(apresentador.simples(contexto.chatId(), "CODIGO_NECESSARIO"));
            return;
        }
        VincularTelegramCasoDeUso.Resultado resultado =
                vincular.executar(contexto.usuarioId(), contexto.chatId(), texto);
        identificar.executar(contexto.usuarioId()).ifPresent(contexto::identificar);
        String prefixo =
                apresentador.texto(
                        "VINCULO_CRIADO", Map.of("nome", resultado.professor().primeiroNome()));
        contexto.responder(
                apresentador.inicio(
                        contexto.chatId(), obterInicio.executar(resultado.professor()), prefixo));
    }

    private void vinculado(ContextoTelegram contexto, MensagemTelegram mensagem) {
        ConversaTelegram conversa = contexto.conversa();
        if (conversa.descartarAudioSeExpirado(
                relogio.agora(), parametros.expiracaoAudioPendente())) {
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
        ConversaTelegram conversa = contexto.conversa();
        Optional<Comando> comando = PalavrasChaveTelegram.identificar(texto);
        Optional<String> confirmacao = conversa.confirmacaoPendente();
        if (confirmacao.isPresent()) {
            conversa.limparConfirmacao();
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
        contexto.conversa().limparConfirmacao();
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
        } else if (contexto.conversa().confirmacaoPendente().isPresent()) {
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
                        contexto.conversa().etapa() == EtapaFluxoConversa.ESCOLHENDO_ALUNO
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
        ConversaTelegram conversa = contexto.conversa();
        switch (comando) {
            case INICIO -> {
                conversa.encerrarFluxo();
                conversa.limparFoco();
                conversa.descartarAudio();
                mostrarInicio(contexto);
            }
            case REGISTRAR_AULA -> iniciarEscolhaDeAluno(contexto, FluxoConversa.REGISTRO_AULA);
            case SEMESTRAL -> iniciarEscolhaDeAluno(contexto, FluxoConversa.SEMESTRAL);
            case HISTORICO -> iniciarEscolhaDeAluno(contexto, FluxoConversa.HISTORICO);
            case SAIR -> {
                conversa.pedirConfirmacao(CONFIRMACAO_SAIR);
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
                                CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA,
                                Map.of()));
            }
        }
    }

    private void confirmar(ContextoTelegram contexto, String confirmacao) {
        if (CONFIRMACAO_SAIR.equals(confirmacao)) {
            encerrarVinculo.executar(contexto.usuarioId());
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
                        contexto.chatId(), obterInicio.executar(contexto.professor()), null));
    }

    /**
     * Começa um fluxo pela escolha do aluno ("Para qual aluno?"). Limpa o foco, sem cancelar nada.
     */
    public void iniciarEscolhaDeAluno(ContextoTelegram contexto, FluxoConversa fluxo) {
        ConversaTelegram conversa = contexto.conversa();
        conversa.limparFoco();
        conversa.descartarAudio();
        conversa.iniciarFluxo(fluxo, EtapaFluxoConversa.ESCOLHENDO_ALUNO);
        BuscarAlunosCasoDeUso.Resultado todos =
                buscarAlunos.executar(contexto.professor().id(), "");
        if (todos.alunos().isEmpty()) {
            contexto.responder(apresentador.simples(contexto.chatId(), "SEM_ALUNOS"));
            return;
        }
        oferecerAlunos(contexto, apresentador.texto("ESCOLHER_ALUNO"), todos.alunos());
    }

    /** Lista de alunos como botões; as opções ficam na conversa para validar o toque. */
    public void oferecerAlunos(
            ContextoTelegram contexto, String cabecalho, List<AlunoResumo> alunos) {
        ConversaTelegram conversa = contexto.conversa();
        conversa.definirEtapa(EtapaFluxoConversa.ESCOLHENDO_ALUNO);
        conversa.guardar(
                OPCOES_ALUNOS,
                String.join(",", alunos.stream().map(a -> a.id().toString()).toList()));
        contexto.responder(apresentador.listaAlunos(contexto.chatId(), cabecalho, alunos));
    }

    /** Estado #10 (e #7 a #9): o texto é o nome, ou parte do nome, de um aluno (FR-013). */
    private void buscarAluno(ContextoTelegram contexto, String texto) {
        BuscarAlunosCasoDeUso.Resultado resultado =
                buscarAlunos.executar(contexto.professor().id(), texto);
        if (resultado.alunos().isEmpty()) {
            contexto.responder(apresentador.simples(contexto.chatId(), "SEM_ALUNOS"));
            return;
        }
        if (resultado.tipo() == TipoResultado.ENCONTRADOS && resultado.alunos().size() == 1) {
            escolherAluno(contexto, resultado.alunos().getFirst());
        } else if (resultado.tipo() == TipoResultado.ENCONTRADOS) {
            oferecerAlunos(
                    contexto, apresentador.texto("VARIOS_ALUNOS_ENCONTRADOS"), resultado.alunos());
        } else if (resultado.tipo() == TipoResultado.NENHUM) {
            oferecerAlunos(
                    contexto,
                    apresentador.texto("NENHUM_ALUNO_ENCONTRADO", Map.of("busca", texto)),
                    resultado.alunos());
        } else {
            oferecerAlunos(contexto, apresentador.texto("ESCOLHER_ALUNO"), resultado.alunos());
        }
    }

    public void escolherAluno(ContextoTelegram contexto, AlunoResumo aluno) {
        for (FluxoTelegram fluxo : fluxos()) {
            if (fluxo.aoEscolherAluno(contexto, aluno)) {
                return;
            }
        }
        selecionarParaRegistro(contexto, aluno);
    }

    /** Aluno selecionado para registrar aula: pede o áudio (sequência 5.2, passo 2). */
    public void selecionarParaRegistro(ContextoTelegram contexto, AlunoResumo aluno) {
        ConversaTelegram conversa = contexto.conversa();
        conversa.limparFoco();
        conversa.selecionarAluno(aluno.id());
        conversa.iniciarFluxo(FluxoConversa.REGISTRO_AULA, EtapaFluxoConversa.AGUARDANDO_AUDIO);
        contexto.responder(
                apresentador.simples(
                        contexto.chatId(),
                        "ALUNO_SELECIONADO",
                        Map.of(
                                "data",
                                apresentador.formatacao().data(relogio.hojeNoFusoDaEscola()),
                                "aluno",
                                aluno.nome(),
                                "limiteMb",
                                parametros.limiteAudioMb())));
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
        } catch (ExcecaoDominio erro) {
            contexto.responder(apresentador.erro(chatId, erro.codigo(), erro.detalhes()));
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
        ConversaTelegram conversa = contexto.conversa();
        switch (dados.acao()) {
            case "inicio" -> {
                conversa.limparConfirmacao();
                comando(contexto, Comando.INICIO);
                return true;
            }
            case "reg" -> {
                conversa.limparConfirmacao();
                iniciarEscolhaDeAluno(contexto, FluxoConversa.REGISTRO_AULA);
                return true;
            }
            case "sem" -> {
                conversa.limparConfirmacao();
                iniciarEscolhaDeAluno(contexto, FluxoConversa.SEMESTRAL);
                return true;
            }
            case "hist" -> {
                conversa.limparConfirmacao();
                iniciarEscolhaDeAluno(contexto, FluxoConversa.HISTORICO);
                return true;
            }
            case "sairok" -> {
                if (!conversa.confirmacaoPendente().filter(CONFIRMACAO_SAIR::equals).isPresent()) {
                    return false;
                }
                conversa.limparConfirmacao();
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
        ConversaTelegram conversa = contexto.conversa();
        if (conversa.etapa() != EtapaFluxoConversa.ESCOLHENDO_ALUNO) {
            return false;
        }
        Optional<UUID> alunoId = opcao(conversa, argumento);
        if (alunoId.isEmpty()) {
            return false;
        }
        conversa.limparConfirmacao();
        BuscarAlunosCasoDeUso.Resultado todos =
                buscarAlunos.executar(contexto.professor().id(), "");
        Optional<AlunoResumo> aluno =
                todos.alunos().stream().filter(a -> a.id().equals(alunoId.get())).findFirst();
        if (aluno.isEmpty()) {
            return false;
        }
        escolherAluno(contexto, aluno.get());
        return true;
    }

    static Optional<UUID> opcao(ConversaTelegram conversa, String argumento) {
        try {
            int indice = Integer.parseInt(argumento);
            List<String> opcoes =
                    conversa.dado(OPCOES_ALUNOS).map(s -> List.of(s.split(","))).orElse(List.of());
            if (indice < 0 || indice >= opcoes.size() || opcoes.get(indice).isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(opcoes.get(indice)));
        } catch (IllegalArgumentException erro) {
            return Optional.empty();
        }
    }
}
