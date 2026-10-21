package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recebe um campo do cadastro em andamento (texto bruto), valida com as mesmas regras do cadastro
 * da Web e guarda o valor normalizado no rascunho (o CPF só cifrado). Ao fechar o último campo,
 * gera o resumo (sem CPF) e o token de confirmação de uso único. Esqueleto da Fundação; a regra é
 * do fluxo W-D.
 */
public class CollectStudentRegistrationFieldUseCase {

    public record Command(UUID linkId, StudentRegistrationDraft.Field field, String text) {

        @Override
        public String toString() {
            return "CollectStudentRegistrationFieldUseCase.Command[field=" + field + "]";
        }
    }

    /** Resumo mostrado ao professor na confirmação: nunca traz CPF, telefone nem e-mail. */
    public record Summary(
            String name, LocalDate birthDate, String instrument, String guardianName) {

        @Override
        public String toString() {
            return "Summary";
        }
    }

    /**
     * @param nextField próximo campo a perguntar; nulo quando o cadastro está completo
     * @param guardianRequired o aluno é menor de 18 anos na data de hoje no fuso da escola
     * @param summary e {@code confirmationToken} só vêm quando o cadastro está pronto para
     *     confirmar
     */
    public record Result(
            StudentRegistrationDraft.Field nextField,
            boolean guardianRequired,
            Summary summary,
            String confirmationToken) {

        @Override
        public String toString() {
            return "CollectStudentRegistrationFieldUseCase.Result[nextField=" + nextField + "]";
        }
    }

    public CollectStudentRegistrationFieldUseCase(
            ActorAuthorizationService authorization,
            StudentRegistrationDraftRepository drafts,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock,
            Duration draftValidity) {}

    public Result run(Actor actor, Command command) {
        throw new UnsupportedOperationException(
                "CollectStudentRegistrationFieldUseCase: fluxo W-D");
    }
}
