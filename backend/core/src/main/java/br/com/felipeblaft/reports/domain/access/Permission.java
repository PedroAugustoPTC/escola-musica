package br.com.felipeblaft.reports.domain.access;

/** Decisão da matriz de permissões para um ator e uma operação (FR-048). */
public enum Permission {
    /** Permitido, sem restrição de escopo. */
    ALLOW,
    /** Permitido somente sobre alunos associados ao próprio professor. */
    ALLOW_OWN_STUDENTS,
    /** Como {@link #ALLOW_OWN_STUDENTS}, e só quando o ator é o único professor ativo associado. */
    ALLOW_SOLE_ACTIVE_TEACHER,
    /** Sem credencial: só as telas e os comandos de entrada. */
    PUBLIC,
    /** Negado: rota inexistente na Web e negativa neutra no Telegram. */
    DENY;

    /** Qualquer valor diferente de {@link #DENY}. */
    public boolean allowed() {
        return this != DENY;
    }
}
