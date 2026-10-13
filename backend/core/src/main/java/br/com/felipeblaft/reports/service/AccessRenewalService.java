package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.Objects;

/**
 * Renova a validade do código de acesso a cada uso com sessão ou vínculo ativos, em qualquer canal
 * (FR-005). O domínio limita a gravação a uma vez por hora.
 */
public class AccessRenewalService {

    private final TeacherRepository teachers;
    private final ClockPort clock;
    private final BusinessParameters parameters;

    public AccessRenewalService(
            TeacherRepository teachers, ClockPort clock, BusinessParameters parameters) {
        this.teachers = Objects.requireNonNull(teachers);
        this.clock = Objects.requireNonNull(clock);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public void renew(Teacher teacher) {
        if (teacher.renewValidity(clock.now(), parameters.codeValidity())) {
            teachers.save(teacher);
        }
    }
}
