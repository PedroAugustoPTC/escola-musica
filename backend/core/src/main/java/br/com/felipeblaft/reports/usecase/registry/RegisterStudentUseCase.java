package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.List;
import java.util.UUID;

/**
 * Cadastra um aluno ({@code STUDENT_REGISTER}) pelo administrador (com zero ou mais professores
 * ativos) ou pelo professor (associado a ele). Valida tudo no núcleo, grava o CPF só cifrado, trata
 * CPF duplicado sem vazar dado de outro professor e limita a sondagem de CPF. Serve à carga inicial
 * (ator de sistema). Esqueleto da Fundação; a regra é do fluxo W-C1.
 */
public class RegisterStudentUseCase {

    /** {@code teacherIds} só vale para o administrador; o professor se associa a si mesmo. */
    public record Command(StudentInput input, List<UUID> teacherIds, String correlationId) {

        @Override
        public String toString() {
            return "RegisterStudentUseCase.Command";
        }
    }

    public RegisterStudentUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {}

    public StudentView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("RegisterStudentUseCase: fluxo W-C1");
    }
}
