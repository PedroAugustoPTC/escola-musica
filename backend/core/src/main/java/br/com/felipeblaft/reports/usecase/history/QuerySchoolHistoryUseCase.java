package br.com.felipeblaft.reports.usecase.history;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import br.com.felipeblaft.reports.domain.history.SchoolHistoryFilter;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.ApprovedReportQueryPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Visão da escola ({@code SCHOOL_HISTORY_QUERY}): relatórios aprovados de qualquer professor e
 * aluno, na ordem única de {@link br.com.felipeblaft.reports.domain.history.HistoryOrder}. Somente
 * leitura: nada é gravado sobre relatórios. A autorização é a primeira instrução; a leitura e o
 * registro de autoria de sucesso valem na mesma transação, de modo que, se o registro falhar,
 * nada é entregue (FR-023).
 */
public class QuerySchoolHistoryUseCase {

    /** Pedido da consulta; todos os filtros são opcionais e só identificadores, tipo e datas. */
    public record Command(
            UUID studentId,
            UUID teacherId,
            ReportType type,
            LocalDate from,
            LocalDate to,
            PageRequest page,
            String correlationId) {

        public Command {
            Objects.requireNonNull(page);
            Objects.requireNonNull(correlationId);
        }
    }

    /** Resumo do aluno filtrado; o nome vai só para a tela. */
    public record StudentRef(UUID id, String name, boolean active) {

        @Override
        public String toString() {
            return "StudentRef[id=" + id + ", active=" + active + "]";
        }
    }

    /** Página do resultado e o resumo do aluno (presente só se o aluno filtrado existe). */
    public record Result(StudentRef student, PageResult<ApprovedReportRow> page) {

        @Override
        public String toString() {
            return "Result[total=" + page.total() + "]";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentRepository students;
    private final ApprovedReportQueryPort query;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;

    public QuerySchoolHistoryUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            ApprovedReportQueryPort query,
            AuthorshipService authorship,
            TransactionPort transaction) {
        this.authorization = Objects.requireNonNull(authorization);
        this.students = Objects.requireNonNull(students);
        this.query = Objects.requireNonNull(query);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
    }

    /**
     * Monta o pedido a partir dos valores crus da borda. A autorização vem primeiro; a forma
     * inválida (data, tipo, página) grava a recusa na autoria (negativa, transação independente) e
     * relança, como a ordem do período em {@link #run}.
     */
    public Command command(
            Actor actor,
            UUID studentId,
            UUID teacherId,
            String type,
            String from,
            String to,
            Integer page,
            Integer size,
            String correlationId) {
        authorization.require(actor, Operation.SCHOOL_HISTORY_QUERY);
        try {
            SchoolHistoryFilter.Syntax syntax =
                    SchoolHistoryFilter.parseSyntax(studentId, teacherId, type, from, to, page, size);
            return new Command(
                    syntax.studentId(),
                    syntax.teacherId(),
                    syntax.type(),
                    syntax.from(),
                    syntax.to(),
                    syntax.page(),
                    correlationId);
        } catch (DomainException refusal) {
            authorship.recordRead(
                    actor,
                    Operation.SCHOOL_HISTORY_QUERY,
                    studentId == null ? null : AuthorshipTargetType.STUDENT,
                    studentId,
                    null,
                    AuthorshipResult.FAILURE,
                    ErrorCode.VALIDATION_FAILED.name(),
                    correlationId);
            throw refusal;
        }
    }

    public Result run(Actor actor, Command command) {
        authorization.require(actor, Operation.SCHOOL_HISTORY_QUERY);
        SchoolHistoryFilter filter = validated(actor, command);
        return transaction.run(
                () -> {
                    StudentRef student = studentRef(command.studentId());
                    PageResult<ApprovedReportRow> page =
                            query.page(filter.criteria(), filter.page());
                    authorship.recordRead(
                            actor,
                            Operation.SCHOOL_HISTORY_QUERY,
                            command.studentId() == null ? null : AuthorshipTargetType.STUDENT,
                            command.studentId(),
                            AuthorshipReadDetail.ofQuery(
                                    command.teacherId(),
                                    command.type(),
                                    command.from(),
                                    command.to(),
                                    command.page().page()),
                            AuthorshipResult.SUCCESS,
                            null,
                            command.correlationId());
                    return new Result(student, page);
                });
    }

    /** Valida o filtro; a recusa grava E2 (negativa, transação independente) e relança. */
    private SchoolHistoryFilter validated(Actor actor, Command command) {
        try {
            return new SchoolHistoryFilter(
                    command.studentId(),
                    command.teacherId(),
                    command.type(),
                    command.from(),
                    command.to(),
                    command.page());
        } catch (DomainException refusal) {
            authorship.recordRead(
                    actor,
                    Operation.SCHOOL_HISTORY_QUERY,
                    command.studentId() == null ? null : AuthorshipTargetType.STUDENT,
                    command.studentId(),
                    AuthorshipReadDetail.ofQuery(
                            command.teacherId(),
                            command.type(),
                            command.from(),
                            command.to(),
                            command.page().page()),
                    AuthorshipResult.FAILURE,
                    ErrorCode.VALIDATION_FAILED.name(),
                    command.correlationId());
            throw refusal;
        }
    }

    private StudentRef studentRef(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return students.searchById(studentId).map(QuerySchoolHistoryUseCase::ref).orElse(null);
    }

    private static StudentRef ref(Student student) {
        return new StudentRef(student.id(), student.name(), student.active());
    }
}
