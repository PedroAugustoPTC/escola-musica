package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Validação do código de acesso, a mesma nos dois canais (FR-004, FR-007): bloqueio verificado
 * antes do código; código inexistente, fora do formato ou de professor inativo conta como tentativa
 * incorreta; código vencido é recusado com mensagem própria. O código nunca é consumido.
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
            throw new DomainException(ErrorCode.CODIGO_INVALIDO);
        }
        if (!teacher.get().validCodeAt(now)) {
            throw new DomainException(ErrorCode.CODIGO_EXPIRADO);
        }
        attempts.registerSuccess(channel, origin);
        return teacher.get();
    }
}
