package br.com.felipeblaft.reports.domain.registry;

/** Filtro de situação nas listas de alunos; o padrão das listas é {@link #ACTIVE}. */
public enum StudentStatusFilter {
    ACTIVE,
    INACTIVE,
    ALL;

    public boolean matches(boolean active) {
        return switch (this) {
            case ACTIVE -> active;
            case INACTIVE -> !active;
            case ALL -> true;
        };
    }
}
