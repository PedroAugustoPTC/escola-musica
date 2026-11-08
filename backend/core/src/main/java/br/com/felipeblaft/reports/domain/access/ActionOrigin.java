package br.com.felipeblaft.reports.domain.access;

/**
 * Canal ou origem da ação, gravado no registro de autoria. {@code WEB} e {@code TELEGRAM} são os
 * canais do usuário; {@code SYSTEM_STARTUP} e {@code INITIAL_LOAD} são origens sem usuário
 * (inicialização e carga inicial); {@code RETENTION} e {@code ALERT_EVALUATION} são as rotinas
 * agendadas da feature 007 (retenção diária e apuração de alertas).
 */
public enum ActionOrigin {
    WEB,
    TELEGRAM,
    SYSTEM_STARTUP,
    INITIAL_LOAD,
    RETENTION,
    ALERT_EVALUATION;

    /** Origem sem usuário autenticado (ator de sistema). */
    public boolean system() {
        return this != WEB && this != TELEGRAM;
    }
}
