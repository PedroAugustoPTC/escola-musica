package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.UUID;

/**
 * Edita um aluno ({@code STUDENT_UPDATE}); campos ausentes não mudam. Menor de 18 anos na data da
 * edição exige os três dados do responsável; o CPF novo segue o mesmo caminho do cadastro. Serve
 * também à carga inicial. Esqueleto da Fundação; a regra é do fluxo W-C1.
 */
public class UpdateStudentUseCase {

    public record Command(UUID studentId, StudentInput input, String correlationId) {

        @Override
        public String toString() {
            return "UpdateStudentUseCase.Command[studentId=" + studentId + "]";
        }
    }

    public UpdateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {}

    public StudentView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("UpdateStudentUseCase: fluxo W-C1");
    }
}
