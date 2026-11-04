package br.com.felipeblaft.reports.domain.audit;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Eventos da trilha de auditoria (contracts/audit-events.md): o nome é o valor gravado em {@code
 * operation}. O grupo organiza o filtro e os rótulos da tela. Acrescentar um evento aqui exige
 * linha na lista do contrato e cenário em {@code AuditEventCoverageIT}.
 */
public enum AuditEventType {
    ADMIN_SIGN_IN(Group.ACCESS),
    ADMIN_ACTIVATE(Group.ACCESS),
    ADMIN_SIGN_OUT(Group.ACCESS),
    ADMIN_CHANGE_PASSWORD(Group.ACCESS),
    TEACHER_SIGN_IN(Group.ACCESS),
    TEACHER_SIGN_OUT(Group.ACCESS),
    ATTEMPT_BLOCKED(Group.ACCESS),
    TEACHER_LINK_TELEGRAM(Group.ACCESS),
    TEACHER_UNLINK_TELEGRAM(Group.ACCESS),
    AUTHORIZATION_DENIED(Group.ACCESS),
    TEACHER_REGISTER(Group.REGISTRATION),
    TEACHER_UPDATE(Group.REGISTRATION),
    TEACHER_INACTIVATE(Group.REGISTRATION),
    TEACHER_REACTIVATE(Group.REGISTRATION),
    TEACHER_REISSUE_ACCESS_CODE(Group.REGISTRATION),
    STUDENT_REGISTER(Group.REGISTRATION),
    STUDENT_UPDATE(Group.REGISTRATION),
    STUDENT_INACTIVATE(Group.REGISTRATION),
    STUDENT_REACTIVATE(Group.REGISTRATION),
    ASSIGNMENT_ASSIGN(Group.REGISTRATION),
    ASSIGNMENT_REMOVE(Group.REGISTRATION),
    ADMINISTRATOR_CREATE(Group.REGISTRATION),
    ADMINISTRATOR_INACTIVATE(Group.REGISTRATION),
    ADMINISTRATOR_REACTIVATE(Group.REGISTRATION),
    LESSON_REPORT_APPROVE(Group.REPORTS),
    LESSON_REPORT_CANCEL(Group.REPORTS),
    SEMESTER_REPORT_APPROVE(Group.REPORTS),
    SEMESTER_REPORT_CANCEL(Group.REPORTS),
    HISTORY_QUERY(Group.TEACHER_READS),
    REPORT_DOCUMENT_OPEN(Group.TEACHER_READS),
    REPORT_DOCUMENT_DOWNLOAD(Group.TEACHER_READS),
    REPORT_DOCUMENT_SENT(Group.TEACHER_READS),
    SCHOOL_HISTORY_QUERY(Group.ADMINISTRATOR_READS),
    SCHOOL_REPORT_DOCUMENT_OPEN(Group.ADMINISTRATOR_READS),
    SCHOOL_REPORT_DOCUMENT_DOWNLOAD(Group.ADMINISTRATOR_READS),
    CONSENT_RECORD(Group.CONSENT),
    CONSENT_REVOKE(Group.CONSENT),
    CONSENT_TERM_PUBLISH(Group.CONSENT),
    CONSENT_BLOCKED_AI(Group.CONSENT),
    ERASURE_REQUEST_REGISTER(Group.ERASURE),
    ERASURE_PREVIEW(Group.ERASURE),
    ERASURE_CONFIRM(Group.ERASURE),
    ERASURE_COMPLETED(Group.ERASURE),
    ERASURE_RECEIPT_READ(Group.ERASURE),
    RETENTION_RUN(Group.RETENTION),
    AUDIT_TRAIL_PURGED(Group.RETENTION),
    AUDIT_TRAIL_QUERY(Group.TRAIL),
    AUDIT_TRAIL_VERIFY(Group.TRAIL),
    OPERATIONS_COST_READ(Group.OPERATIONS),
    OPERATIONS_ALERT_READ(Group.OPERATIONS),
    OPERATIONS_ALERT_ACK(Group.OPERATIONS),
    ALERT_OPENED(Group.OPERATIONS),
    ALERT_NORMALIZED(Group.OPERATIONS);

    /** Grupo do evento na tela da trilha. */
    public enum Group {
        ACCESS,
        REGISTRATION,
        REPORTS,
        TEACHER_READS,
        ADMINISTRATOR_READS,
        CONSENT,
        ERASURE,
        RETENTION,
        TRAIL,
        OPERATIONS
    }

    private final Group group;

    AuditEventType(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }

    /** Os nomes de todos os eventos, para validar o filtro e conferir o contrato. */
    public static Set<String> names() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
