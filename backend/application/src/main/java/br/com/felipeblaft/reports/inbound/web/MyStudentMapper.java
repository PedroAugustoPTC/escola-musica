package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.usecase.registry.GuardianInput;
import br.com.felipeblaft.reports.usecase.registry.StudentInput;
import br.com.felipeblaft.reports.usecase.registry.StudentView;

/** Conversão entre os DTOs de "Meus alunos" e os tipos dos casos de uso, sem regra de negócio. */
final class MyStudentMapper {

    private MyStudentMapper() {}

    static StudentInput input(
            String name,
            String birthDate,
            String cpf,
            MyStudentGuardianRequest guardian,
            String instrument) {
        return new StudentInput(
                name,
                birthDate,
                cpf == null ? null : new Secret(cpf),
                guardian == null
                        ? null
                        : new GuardianInput(guardian.name(), guardian.phone(), guardian.email()),
                instrument);
    }

    static StudentInput input(MyStudentRequest request) {
        return input(
                request.name(),
                request.birthDate(),
                request.cpf(),
                request.guardian(),
                request.instrument());
    }

    static StudentInput input(MyStudentUpdateRequest request) {
        return input(
                request.name(),
                request.birthDate(),
                request.cpf(),
                request.guardian(),
                request.instrument());
    }

    static MyStudentDto student(StudentView view) {
        StudentView.GuardianView guardian = view.guardian();
        return new MyStudentDto(
                view.id(),
                view.name(),
                view.birthDate(),
                view.age(),
                view.instrument(),
                view.active(),
                guardian == null
                        ? null
                        : new MyStudentGuardianDto(
                                guardian.name(), guardian.phone(), guardian.email()),
                view.canInactivate());
    }

    static MyStudentPageDto page(PageResult<StudentView> page) {
        return new MyStudentPageDto(
                page.items().stream().map(MyStudentMapper::student).toList(),
                page.page(),
                page.size(),
                page.total());
    }
}
