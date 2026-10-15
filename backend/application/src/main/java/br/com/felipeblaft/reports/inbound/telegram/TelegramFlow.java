package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.inbound.telegram.TelegramKeywords.Command;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramFile;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramMessage;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;

/**
 * Parte da conversa responsável por um fluxo (registro de aula, conferência, retomada, semestral,
 * histórico). O interpretador consulta os fluxos em ordem; cada método devolve {@code true} quando
 * tratou a entrada. Nenhum fluxo guarda estado em memória: tudo está na conversa persistida.
 */
public interface TelegramFlow {

    /** Texto livre que não é palavra-chave (estados #3 a #7 da tabela). */
    default boolean onText(TelegramContext context, String text) {
        return false;
    }

    /** Áudio ou vídeo recebido. */
    default boolean aoAudio(TelegramContext context, TelegramMessage message, TelegramFile file) {
        return false;
    }

    /** Botão tocado ({@code v1:<acao>:<argumento>}). */
    default boolean aoCallback(TelegramContext context, String action, String argument) {
        return false;
    }

    /** Comando digitado (aprovar, cancelar, continuar, descartar, tentar…). */
    default boolean onCommand(TelegramContext context, Command command) {
        return false;
    }

    /** "sim" com uma confirmação pendente (aprovar, cancelar, descartar…). */
    default boolean onConfirm(TelegramContext context, String confirmation) {
        return false;
    }

    /** Aluno escolhido na lista ou encontrado pelo nome. */
    default boolean onChooseStudent(TelegramContext context, StudentSummary student) {
        return false;
    }

    /**
     * Complemento do "o que posso receber" em {@code TIPO_NAO_SUPORTADO}; nulo se não se aplica.
     */
    default String whatICanReceive(TelegramContext context) {
        return null;
    }
}
