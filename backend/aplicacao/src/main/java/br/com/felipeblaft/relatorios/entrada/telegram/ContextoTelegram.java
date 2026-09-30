package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.acesso.IdentificarProfessorTelegramCasoDeUso.Identificacao;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados de um único update em processamento: quem falou, em qual chat, o vínculo carregado do banco
 * e as saídas acumuladas (enviadas só depois de gravar o estado).
 */
public final class ContextoTelegram {

    private final long usuarioId;
    private final long chatId;
    private final Long mensagemCallbackId;
    private Identificacao identificacao;
    private boolean vinculoEncerrado;
    private final List<SaidaTelegram> saidas = new ArrayList<>();

    public ContextoTelegram(long usuarioId, long chatId, Long mensagemCallbackId) {
        this.usuarioId = usuarioId;
        this.chatId = chatId;
        this.mensagemCallbackId = mensagemCallbackId;
    }

    public long usuarioId() {
        return usuarioId;
    }

    public long chatId() {
        return chatId;
    }

    /** Mensagem que continha o botão tocado (para editar no lugar), se o update é um callback. */
    public Long mensagemCallbackId() {
        return mensagemCallbackId;
    }

    public void identificar(Identificacao identificacao) {
        this.identificacao = identificacao;
    }

    public boolean vinculado() {
        return identificacao != null && !vinculoEncerrado;
    }

    public Professor professor() {
        return identificacao.professor();
    }

    public VinculoTelegram vinculo() {
        return identificacao.vinculo();
    }

    public ConversaTelegram conversa() {
        return identificacao.vinculo().conversa();
    }

    public void marcarVinculoEncerrado() {
        this.vinculoEncerrado = true;
    }

    public boolean vinculoEncerrado() {
        return vinculoEncerrado;
    }

    public void responder(SaidaTelegram saida) {
        saidas.add(saida);
    }

    public List<SaidaTelegram> saidas() {
        return List.copyOf(saidas);
    }
}
