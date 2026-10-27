package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.CodeRejectedException;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Validação do código de acesso, a mesma nos dois canais (FR-004, FR-007): bloqueio verificado
 * antes do código; código inexistente, fora do formato, substituído, de professor inativo ou
 * vencido conta como tentativa incorreta e é recusado com a mesma resposta, de modo que o bloqueio
 * não revele qual deles ocorreu (FR-042). O código nunca é consumido.
 */
public class CodeAuthenticationService {

    private final AccessAttemptsControlService attempts;
    private final TeacherRepository teachers;
    private final DataProtectionPort protection;
    private final ClockPort clock;

    public CodeAuthenticationService(
            AccessAttemptsControlService attempts,
            TeacherRepository teachers,
            DataProtectionPort protection,
            ClockPort clock) {
        this.attempts = Objects.requireNonNull(attempts);
        this.teachers = Objects.requireNonNull(teachers);
        this.protection = Objects.requireNonNull(protection);
        this.clock = Objects.requireNonNull(clock);
    }

    public Teacher authenticate(Channel channel, String origin, String codeText) {
        attempts.verifyLock(channel, origin);
        Instant now = clock.now();
        Optional<Teacher> teacher =
                AccessCode.retry(codeText)
                        .map(protection::hashAccessCode)
                        .flatMap(teachers::searchByAccessCodeHash)
                        .filter(Teacher::active);
        if (teacher.isEmpty()) {
            attempts.registerFailure(channel, origin);
            throw CodeRejectedException.unknown();
        }
        if (!teacher.get().validCodeAt(now)) {
            // vencido conta como tentativa, igual ao inexistente (FR-042)
            attempts.registerFailure(channel, origin);
            throw CodeRejectedException.expired(teacher.get().id());
        }
        attempts.registerSuccess(channel, origin);
        return teacher.get();
    }
}
