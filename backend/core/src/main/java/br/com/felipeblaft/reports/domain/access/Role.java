package br.com.felipeblaft.reports.domain.access;

/**
 * Papéis autenticáveis do sistema: exatamente dois (FR-001). Aluno e responsável não têm conta,
 * credencial, rota nem comando; "sistema" não é papel, é uma origem ({@link ActionOrigin}).
 */
public enum Role {
    ADMINISTRATOR,
    TEACHER
}
