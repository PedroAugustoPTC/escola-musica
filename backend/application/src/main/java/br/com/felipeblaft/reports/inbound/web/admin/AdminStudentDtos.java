package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.usecase.registry.GuardianInput;
import br.com.felipeblaft.reports.usecase.registry.StudentInput;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs e conversões dos alunos da área administrativa. O pedido leva o CPF só na entrada; nenhuma
 * resposta tem propriedade de CPF, cifrado ou não (FR-020, FR-039). Nenhuma validação de regra
 * aqui: texto, data e CPF seguem brutos até o núcleo.
 */
public final class AdminStudentDtos {

    private AdminStudentDtos() {}

    public record AdminGuardianRequest(String name, String phone, String email) {

        @Override
        public String toString() {
            return "AdminGuardianRequest";
        }
    }

    /**
     * Cadastro (todos os campos) e edição (campos ausentes não mudam); {@code teacherIds} só no
     * cadastro.
     */
    public record AdminStudentRequest(
            String name,
            String birthDate,
            String cpf,
            AdminGuardianRequest guardian,
            String instrument,
            List<UUID> teacherIds) {

        @Override
        public String toString() {
            return "AdminStudentRequest";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminGuardianResponse(String name, String phone, String email) {

        @Override
        public String toString() {
            return "AdminGuardianResponse";
        }
    }

    public record AdminTeacherRefResponse(UUID id, String name, boolean active) {

        @Override
        public String toString() {
            return "AdminTeacherRefResponse[id=" + id + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminStudentResponse(
            UUID id,
            String name,
            LocalDate birthDate,
            int age,
            String instrument,
            boolean active,
            AdminGuardianResponse guardian,
            List<AdminTeacherRefResponse> teachers) {

        @Override
        public String toString() {
            return "AdminStudentResponse[id=" + id + "]";
        }
    }

    static StudentInput input(AdminStudentRequest request) {
        GuardianInput guardian =
                request.guardian() == null
                        ? null
                        : new GuardianInput(
                                request.guardian().name(),
                                request.guardian().phone(),
                                request.guardian().email());
        return new StudentInput(
                request.name(),
                request.birthDate(),
                request.cpf() == null ? null : new Secret(request.cpf()),
                guardian,
                request.instrument());
    }

    static AdminStudentResponse student(StudentView view) {
        StudentView.GuardianView guardian = view.guardian();
        return new AdminStudentResponse(
                view.id(),
                view.name(),
                view.birthDate(),
                view.age(),
                view.instrument(),
                view.active(),
                guardian == null
                        ? null
                        : new AdminGuardianResponse(
                                guardian.name(), guardian.phone(), guardian.email()),
                view.teachers().stream()
                        .map(t -> new AdminTeacherRefResponse(t.id(), t.name(), t.active()))
                        .toList());
    }

    static AdminPageResponse<AdminStudentResponse> page(PageResult<StudentView> result) {
        return new AdminPageResponse<>(
                result.items().stream().map(AdminStudentDtos::student).toList(),
                result.page(),
                result.size(),
                result.total());
    }
}
