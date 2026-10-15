package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.usecase.home.GetStudentSummaryUseCase;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alunos associados, com busca aproximada (FR-013), e resumo de um aluno (FR-009). */
@RestController
@Validated
@RequestMapping("/api/v1/students")
public class StudentController {

    private final SearchStudentsUseCase searchStudents;
    private final GetStudentSummaryUseCase getSummary;

    public StudentController(
            SearchStudentsUseCase searchStudents, GetStudentSummaryUseCase getSummary) {
        this.searchStudents = searchStudents;
        this.getSummary = getSummary;
    }

    @GetMapping
    public StudentSearchDto students(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @RequestParam(required = false) @Size(max = 100) String search) {
        return HomeMapper.search(searchStudents.run(teacher.id(), search));
    }

    @GetMapping("/{studentId}")
    public StudentSummaryDto student(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID studentId) {
        return HomeMapper.student(getSummary.run(teacher.id(), studentId));
    }
}
