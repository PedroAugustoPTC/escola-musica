package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.CodeRejectedException;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de autoria da entrada do professor por código (FR-040), comum à Web e ao Telegram:
 * evento de entrada ou de vínculo, com sucesso ou falha, e o bloqueio por tentativas. Grava só
 * identificadores, o código de erro e o resumo da origem; nunca o código digitado. A regra de
 * acesso não muda: este colaborador só observa o resultado.
 */
public final class TeacherAccessAuditService {

    private final AuthorshipService authorship;
    private final DataProtectionPort protection;

    public TeacherAccessAuditService(AuthorshipService authorship, DataProtectionPort protection) {
        this.authorship = Objects.requireNonNull(authorship);
        this.protection = Objects.requireNonNull(protection);
    }

    /**
     * Autentica pelo código e registra a falha ou o bloqueio; o sucesso fica para quem chama,
     * depois das suas próprias verificações.
     */
    public Teacher authenticate(
            CodeAuthenticationService authentication,
            Channel channel,
            String origin,
            String code,
            String event,
            String correlationId) {
        try {
            return authentication.authenticate(channel, origin, code);
        } catch (CodeRejectedException rejected) {
            UUID teacherId = rejected.knownTeacherId().orElse(null);
            record(
                    channel,
                    origin,
                    teacherId,
                    event,
                    AuthorshipResult.FAILURE,
                    rejected.auditCode(),
                    correlationId);
            throw rejected;
        } catch (DomainException refused) {
            if (refused.code() == ErrorCode.TEMPORARILY_BLOCKED) {
                record(
                        channel,
                        origin,
                        null,
                        AuthorshipRecord.ATTEMPT_BLOCKED,
                        AuthorshipResult.BLOCKED,
                        ErrorCode.TEMPORARILY_BLOCKED.name(),
                        correlationId);
            }
            throw refused;
        }
    }

    /** Registra a entrada ou o vínculo bem-sucedido, na mesma transação do caso de uso. */
    public void succeeded(
            Channel channel, String origin, Teacher teacher, String event, String correlationId) {
        record(channel, origin, teacher.id(), event, AuthorshipResult.SUCCESS, null, correlationId);
    }

    /** Registra a recusa de um professor identificado (por exemplo, conta já vinculada). */
    public void refused(
            Channel channel,
            String origin,
            Teacher teacher,
            String event,
            ErrorCode code,
            String correlationId) {
        record(
                channel,
                origin,
                teacher.id(),
                event,
                AuthorshipResult.FAILURE,
                code.name(),
                correlationId);
    }

    private void record(
            Channel channel,
            String origin,
            UUID teacherId,
            String event,
            AuthorshipResult result,
            String errorCode,
            String correlationId) {
        authorship.recordAuthentication(
                channel == Channel.WEB ? ActionOrigin.WEB : ActionOrigin.TELEGRAM,
                teacherId == null ? null : Role.TEACHER,
                teacherId,
                event,
                result,
                errorCode,
                protection.originHash(channel, origin == null ? "" : origin),
                correlationId);
    }
}
