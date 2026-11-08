package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureConfirmation;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureScope;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de eliminação em {@code erasure_request} e a lista só de acréscimo {@code erased_student}
 * (FR-020 a FR-026). O alcance gravado guarda só os totais; o detalhe por situação existe apenas na
 * prévia.
 */
@Repository
class ErasureRequestRepositoryAdapter implements ErasureRequestRepository {

    private static final String COLUMNS =
            "id, student_id, requester_role, requester_name, requested_on, received_via,"
                    + " protocol_reference, status, registered_by, confirmation_token_hash,"
                    + " confirmation_expires_at, confirmed_by, confirmed_at, completed_at,"
                    + " scope_lesson_reports, scope_semester_reports, scope_pdfs,"
                    + " scope_assignments, scope_consents, created_at";

    private static final String TOTAL = "TOTAL";

    private final JdbcTemplate jdbc;

    ErasureRequestRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(ErasureRequest request) {
        ErasureScope scope = request.recordedScope();
        ErasureConfirmation pending = request.pending();
        Object[] values = {
            request.requesterName(),
            request.status().name(),
            pending == null ? null : pending.tokenHash(),
            pending == null ? null : Timestamp.from(pending.expiresAt()),
            request.confirmedBy(),
            request.confirmedAt() == null ? null : Timestamp.from(request.confirmedAt()),
            request.completedAt() == null ? null : Timestamp.from(request.completedAt()),
            scope == null ? null : scope.totalLessonReports(),
            scope == null ? null : scope.totalSemesterReports(),
            scope == null ? null : scope.pdfs(),
            scope == null ? null : scope.assignments(),
            scope == null ? null : scope.consents()
        };
        int updated =
                jdbc.update(
                        "UPDATE erasure_request SET requester_name = ?, status = ?,"
                                + " confirmation_token_hash = ?, confirmation_expires_at = ?,"
                                + " confirmed_by = ?, confirmed_at = ?, completed_at = ?,"
                                + " scope_lesson_reports = ?, scope_semester_reports = ?,"
                                + " scope_pdfs = ?, scope_assignments = ?, scope_consents = ?,"
                                + " lock_version = lock_version + 1 WHERE id = ?",
                        concat(values, request.id()));
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO erasure_request (id, student_id, requester_role, requester_name,"
                            + " requested_on, received_via, protocol_reference, status,"
                            + " registered_by, confirmation_token_hash, confirmation_expires_at,"
                            + " confirmed_by, confirmed_at, completed_at, scope_lesson_reports,"
                            + " scope_semester_reports, scope_pdfs, scope_assignments,"
                            + " scope_consents, created_at, lock_version)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                            + " ?, 0)",
                    request.id(),
                    request.studentId(),
                    request.requesterRole(),
                    request.requesterName(),
                    java.sql.Date.valueOf(request.requestedOn()),
                    request.receivedVia(),
                    request.protocolReference(),
                    request.status().name(),
                    request.registeredBy(),
                    values[2],
                    values[3],
                    values[4],
                    values[5],
                    values[6],
                    values[7],
                    values[8],
                    values[9],
                    values[10],
                    values[11],
                    Timestamp.from(request.createdAt()));
        } catch (DuplicateKeyException duplicate) {
            throw new DomainException(ErrorCode.ERASURE_ALREADY_OPEN);
        }
    }

    private static Object[] concat(Object[] values, Object last) {
        Object[] all = java.util.Arrays.copyOf(values, values.length + 1);
        all[values.length] = last;
        return all;
    }

    @Override
    public Optional<ErasureRequest> findById(UUID id) {
        return one("SELECT " + COLUMNS + " FROM erasure_request WHERE id = ?", id);
    }

    @Override
    public Optional<ErasureRequest> findForUpdate(UUID id) {
        return one("SELECT " + COLUMNS + " FROM erasure_request WHERE id = ? FOR UPDATE", id);
    }

    @Override
    public Optional<ErasureRequest> findOpenByStudent(UUID studentId) {
        return one(
                "SELECT "
                        + COLUMNS
                        + " FROM erasure_request WHERE student_id = ? AND status <> 'COMPLETED'",
                studentId);
    }

    @Override
    public List<ErasureRequest> listAll() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM erasure_request ORDER BY created_at DESC, id DESC",
                MAPPER);
    }

    @Override
    public List<ErasureRequest> listInProgress() {
        return jdbc.query(
                "SELECT "
                        + COLUMNS
                        + " FROM erasure_request WHERE status = 'IN_PROGRESS'"
                        + " ORDER BY created_at, id",
                MAPPER);
    }

    @Override
    @Transactional
    public void recordErased(ErasedStudent erased) {
        jdbc.update(
                "INSERT INTO erased_student (student_id, erased_at) VALUES (?, ?)"
                        + " ON CONFLICT (student_id) DO NOTHING",
                erased.studentId(),
                Timestamp.from(erased.erasedAt()));
    }

    @Override
    public boolean isErased(UUID studentId) {
        Integer total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM erased_student WHERE student_id = ?",
                        Integer.class,
                        studentId);
        return total != null && total > 0;
    }

    private Optional<ErasureRequest> one(String sql, Object... args) {
        return jdbc.query(sql, MAPPER, args).stream().findFirst();
    }

    private static final RowMapper<ErasureRequest> MAPPER = ErasureRequestRepositoryAdapter::map;

    private static ErasureRequest map(ResultSet rs, int row) throws SQLException {
        String tokenHash = rs.getString("confirmation_token_hash");
        Timestamp expires = rs.getTimestamp("confirmation_expires_at");
        ErasureConfirmation pending =
                tokenHash == null ? null : new ErasureConfirmation(tokenHash, expires.toInstant());
        Object lessons = rs.getObject("scope_lesson_reports");
        ErasureScope scope =
                lessons == null
                        ? null
                        : new ErasureScope(
                                Map.of(TOTAL, rs.getInt("scope_lesson_reports")),
                                Map.of(TOTAL, rs.getInt("scope_semester_reports")),
                                rs.getInt("scope_pdfs"),
                                rs.getInt("scope_assignments"),
                                rs.getInt("scope_consents"));
        return new ErasureRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getString("requester_role"),
                rs.getString("requester_name"),
                rs.getDate("requested_on").toLocalDate(),
                rs.getString("received_via"),
                rs.getString("protocol_reference"),
                ErasureStatus.valueOf(rs.getString("status")),
                rs.getObject("registered_by", UUID.class),
                pending,
                rs.getObject("confirmed_by", UUID.class),
                instant(rs.getTimestamp("confirmed_at")),
                instant(rs.getTimestamp("completed_at")),
                scope,
                rs.getTimestamp("created_at").toInstant());
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
