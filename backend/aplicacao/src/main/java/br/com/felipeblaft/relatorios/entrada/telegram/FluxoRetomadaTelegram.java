package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.aula.CancelarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioEmAbertoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.DescartarRegistroEmProcessamentoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.EtapaFluxoConversa;
import br.com.felipeblaft.relatorios.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Retomada entre canais (US3, sequência 5.3): depois do aviso "Você tem um relatório em aberto",
 * {@code cont} reapresenta a etapa atual (processamento, perguntas ou conferência na versão
 * vigente) e {@code desc} pede confirmação, descarta e segue para o novo registro, reaproveitando o
 * áudio pendente se ainda for válido.
 */
@Component
@Order(5)
public class FluxoRetomadaTelegram implements FluxoTelegram {

    static final String CONFIRMACAO_DESCARTE = "descartar";

    private final ObterRelatorioEmAbertoCasoDeUso obterEmAberto;
    private final CancelarRelatorioAulaCasoDeUso cancelar;
    private final DescartarRegistroEmProcessamentoCasoDeUso descartarRegistro;
    private final BuscarAlunosCasoDeUso buscarAlunos;
    private final ApresentacaoRelatorioAulaTelegram apresentacao;
    private final ApresentadorTelegram apresentador;
    private final FluxoRegistroAulaTelegram registro;
    private final InterpretadorConversaTelegram interpretador;
    private final ParametrosNegocio parametros;
    private final RelogioPorta relogio;

    @SuppressWarnings("java:S107")
    public FluxoRetomadaTelegram(
            ObterRelatorioEmAbertoCasoDeUso obterEmAberto,
            CancelarRelatorioAulaCasoDeUso cancelar,
            DescartarRegistroEmProcessamentoCasoDeUso descartarRegistro,
            BuscarAlunosCasoDeUso buscarAlunos,
            ApresentacaoRelatorioAulaTelegram apresentacao,
            ApresentadorTelegram apresentador,
            FluxoRegistroAulaTelegram registro,
            InterpretadorConversaTelegram interpretador,
            ParametrosNegocio parametros,
            RelogioPorta relogio) {
        this.obterEmAberto = obterEmAberto;
        this.cancelar = cancelar;
        this.descartarRegistro = descartarRegistro;
        this.buscarAlunos = buscarAlunos;
        this.apresentacao = apresentacao;
        this.apresentador = apresentador;
        this.registro = registro;
        this.interpretador = interpretador;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** Aluno do aviso em aberto guardado na conversa, se a conversa está nessa etapa. */
    private Optional<UUID> alunoDoAviso(ContextoTelegram contexto) {
        ConversaTelegram conversa = contexto.conversa();
        if (conversa.etapa() != EtapaFluxoConversa.AVISO_RELATORIO_EM_ABERTO) {
            return Optional.empty();
        }
        return conversa.dado(FluxoRegistroAulaTelegram.ALUNO_EM_ABERTO).map(UUID::fromString);
    }

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        switch (acao) {
            case "cont" -> {
                return alunoDoAviso(contexto)
                        .map(aluno -> continuar(contexto, aluno))
                        .orElse(false);
            }
            case "desc" -> {
                return alunoDoAviso(contexto).map(aluno -> pedirDescarte(contexto)).orElse(false);
            }
            case "descok" -> {
                Optional<UUID> aluno = alunoDoAviso(contexto);
                if (aluno.isEmpty()
                        || !contexto.conversa()
                                .confirmacaoPendente()
                                .filter(CONFIRMACAO_DESCARTE::equals)
                                .isPresent()) {
                    return false;
                }
                contexto.conversa().limparConfirmacao();
                return descartar(contexto, aluno.get());
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean aoComando(ContextoTelegram contexto, Comando comando) {
        Optional<UUID> aluno = alunoDoAviso(contexto);
        if (aluno.isEmpty()) {
            return false;
        }
        return switch (comando) {
            case CONTINUAR -> continuar(contexto, aluno.get());
            case DESCARTAR -> pedirDescarte(contexto);
            default -> false;
        };
    }

    @Override
    public boolean aoConfirmar(ContextoTelegram contexto, String confirmacao) {
        if (!CONFIRMACAO_DESCARTE.equals(confirmacao)) {
            return false;
        }
        return alunoDoAviso(contexto).map(aluno -> descartar(contexto, aluno)).orElse(false);
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return alunoDoAviso(contexto).isPresent()
                ? apresentador.rotulo("BOTAO_CONTINUAR")
                        + " / "
                        + apresentador.rotulo("BOTAO_DESCARTAR")
                : null;
    }

    // ------------------------------------------------------------------ ações

    private boolean continuar(ContextoTelegram contexto, UUID alunoId) {
        ConversaTelegram conversa = contexto.conversa();
        conversa.descartarAudio();
        conversa.encerrarFluxo();
        Optional<RelatorioEmAberto> aberto =
                obterEmAberto.executar(contexto.professor().id(), alunoId);
        if (aberto.isEmpty()) {
            // concluído ou descartado no outro canal enquanto isso: segue para um novo registro
            aluno(contexto, alunoId)
                    .ifPresent(a -> interpretador.selecionarParaRegistro(contexto, a));
            return true;
        }
        RelatorioEmAberto relatorio = aberto.get();
        if (relatorio.relatorioId() != null && relatorio.tipo() == TipoRelatorio.AULA) {
            apresentacao
                    .apresentar(
                            contexto.chatId(),
                            contexto.professor().id(),
                            relatorio.relatorioId(),
                            conversa,
                            null)
                    .forEach(contexto::responder);
        } else {
            String etapa = relatorio.etapa() == null ? "TRANSCREVENDO" : relatorio.etapa().name();
            contexto.responder(
                    apresentador.simples(
                            contexto.chatId(),
                            "PROCESSAMENTO_EM_ANDAMENTO",
                            Map.of("etapa", apresentador.formatacao().etapa(etapa))));
        }
        return true;
    }

    private boolean pedirDescarte(ContextoTelegram contexto) {
        contexto.conversa().pedirConfirmacao(CONFIRMACAO_DESCARTE);
        contexto.responder(
                apresentador.confirmacao(
                        contexto.chatId(),
                        apresentador.texto("CONFIRMAR_DESCARTE"),
                        apresentador.botao("BOTAO_CONFIRMAR_DESCARTE", "descok")));
        return true;
    }

    private boolean descartar(ContextoTelegram contexto, UUID alunoId) {
        UUID professorId = contexto.professor().id();
        obterEmAberto
                .executar(professorId, alunoId)
                .ifPresent(
                        aberto -> {
                            if (aberto.relatorioId() != null) {
                                cancelar.descartar(professorId, aberto.relatorioId());
                            } else if (aberto.trabalhoId() != null) {
                                descartarRegistro.executar(professorId, aberto.trabalhoId());
                            }
                        });
        ConversaTelegram conversa = contexto.conversa();
        conversa.limparFoco();
        conversa.descartarAudioSeExpirado(relogio.agora(), parametros.expiracaoAudioPendente());
        Optional<AlunoResumo> aluno = aluno(contexto, alunoId);
        if (aluno.isEmpty()) {
            interpretador.mostrarInicio(contexto);
            return true;
        }
        contexto.responder(apresentador.simples(contexto.chatId(), "RELATORIO_CANCELADO"));
        if (conversa.audioPendente().isPresent()) {
            // o áudio enviado antes do aviso ainda vale: vira o novo registro
            registro.registrarAudioPendente(contexto, aluno.get(), contexto.mensagemCallbackId());
        } else {
            interpretador.selecionarParaRegistro(contexto, aluno.get());
        }
        return true;
    }

    private Optional<AlunoResumo> aluno(ContextoTelegram contexto, UUID alunoId) {
        return buscarAlunos.executar(contexto.professor().id(), "").alunos().stream()
                .filter(a -> a.id().equals(alunoId))
                .findFirst();
    }
}
