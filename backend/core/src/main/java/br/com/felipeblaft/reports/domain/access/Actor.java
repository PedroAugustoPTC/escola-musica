package br.com.felipeblaft.reports.domain.access;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem executa uma operação (R-01, R-02). Os adaptadores montam o ator a partir da sessão, do
 * vínculo ou da origem do comando; o núcleo decide a permissão. O ator de sistema não tem papel nem
 * id e nunca vem de cookie, vínculo ou requisição: só a carga inicial e a inicialização o criam.
 *
 * @param role papel do usuário; {@code null} apenas no ator de sistema
 * @param id identificador do administrador ou do professor; {@code null} no ator de sistema
 * @param origin canal ou origem da ação
 * @param passwordChangeRequired administrador com senha provisória: só pode trocar a senha
 */
public record Actor(Role role, UUID id, ActionOrigin origin, boolean passwordChangeRequired) {

    public Actor {
        Objects.requireNonNull(origin);
        if (role == null) {
            if (id != null || !origin.system() || passwordChangeRequired) {
                throw new IllegalArgumentException("Ator de sistema inválido");
            }
        } else if (id == null || origin.system()) {
            throw new IllegalArgumentException("Ator com papel exige id e canal do usuário");
        }
    }

    /** Administrador autenticado na Web (as funções administrativas existem só nela). */
    public static Actor administrator(UUID id, boolean passwordChangeRequired) {
        return new Actor(Role.ADMINISTRATOR, id, ActionOrigin.WEB, passwordChangeRequired);
    }

    /** Professor autenticado em um dos dois canais. */
    public static Actor teacher(UUID id, ActionOrigin origin) {
        if (origin != ActionOrigin.WEB && origin != ActionOrigin.TELEGRAM) {
            throw new IllegalArgumentException("Professor age pela Web ou pelo Telegram");
        }
        return new Actor(Role.TEACHER, id, origin, false);
    }

    /** Ator de sistema, sem papel e sem id; só a inicialização e a carga inicial. */
    public static Actor system(ActionOrigin origin) {
        return new Actor(null, null, origin, false);
    }

    /** Ator da rotina diária de retenção; só {@code RetentionTask} o cria (regra ArchUnit). */
    public static Actor retention() {
        return system(ActionOrigin.RETENTION);
    }

    /** Ator da apuração periódica de alertas; só {@code OperationalAlertTask} o cria. */
    public static Actor alertEvaluation() {
        return system(ActionOrigin.ALERT_EVALUATION);
    }

    public boolean isSystem() {
        return role == null;
    }
}
