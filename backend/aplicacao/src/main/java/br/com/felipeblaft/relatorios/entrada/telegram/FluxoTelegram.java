package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.entrada.telegram.PalavrasChaveTelegram.Comando;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.ArquivoTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.UpdateTelegram.MensagemTelegram;

/**
 * Parte da conversa responsável por um fluxo (registro de aula, conferência, retomada, semestral,
 * histórico). O interpretador consulta os fluxos em ordem; cada método devolve {@code true} quando
 * tratou a entrada. Nenhum fluxo guarda estado em memória: tudo está na conversa persistida.
 */
public interface FluxoTelegram {

    /** Texto livre que não é palavra-chave (estados #3 a #7 da tabela). */
    default boolean aoTexto(ContextoTelegram contexto, String texto) {
        return false;
    }

    /** Áudio ou vídeo recebido. */
    default boolean aoAudio(
            ContextoTelegram contexto, MensagemTelegram mensagem, ArquivoTelegram arquivo) {
        return false;
    }

    /** Botão tocado ({@code v1:<acao>:<argumento>}). */
    default boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        return false;
    }

    /** Comando digitado (aprovar, cancelar, continuar, descartar, tentar…). */
    default boolean aoComando(ContextoTelegram contexto, Comando comando) {
        return false;
    }

    /** "sim" com uma confirmação pendente (aprovar, cancelar, descartar…). */
    default boolean aoConfirmar(ContextoTelegram contexto, String confirmacao) {
        return false;
    }

    /** Aluno escolhido na lista ou encontrado pelo nome. */
    default boolean aoEscolherAluno(ContextoTelegram contexto, AlunoResumo aluno) {
        return false;
    }

    /**
     * Complemento do "o que posso receber" em {@code TIPO_NAO_SUPORTADO}; nulo se não se aplica.
     */
    default String oQuePossoReceber(ContextoTelegram contexto) {
        return null;
    }
}
