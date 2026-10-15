package br.com.felipeblaft.reports.usecase.lesson;

import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.service.AuthorizationService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Relatório de aula (ou registro ainda em transcrição) em aberto para o aluno, com canal de origem
 * e última alteração (FR-034): base do aviso "Você tem um relatório em aberto" nos dois canais.
 */
public class GetOpenReportUseCase {

    private final AuthorizationService authorization;
    private final OpenReportsQueryPort query;

    public GetOpenReportUseCase(AuthorizationService authorization, OpenReportsQueryPort query) {
        this.authorization = Objects.requireNonNull(authorization);
        this.query = Objects.requireNonNull(query);
    }

    public Optional<OpenReport> run(UUID teacherId, UUID studentId) {
        authorization.requireAssignment(teacherId, studentId);
        return query.searchByStudent(teacherId, studentId);
    }
}
