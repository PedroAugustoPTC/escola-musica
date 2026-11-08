package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;

/**
 * O contexto da requisição em curso, para a trilha de auditoria (FR-040): o canal por onde o
 * professor age e o identificador de correlação, o mesmo que vai aos logs. Os adaptadores de
 * entrada o definem no começo de cada requisição (Web) ou de cada update (Telegram); o núcleo só o
 * lê. Fora de uma requisição não há contexto, e quem o pede recebe erro.
 */
public interface RequestContextPort {

    /**
     * Há uma requisição em curso? Fica falso nas rotinas de fundo (por exemplo, a entrega
     * automática do PDF de conferência quando o trabalho termina), que não são ação do professor.
     */
    boolean active();

    /** O canal da ação: {@code WEB} ou {@code TELEGRAM}. */
    ActionOrigin origin();

    /** Identificador de correlação da requisição (UUID na Web, {@code update_id} no Telegram). */
    String correlationId();
}
