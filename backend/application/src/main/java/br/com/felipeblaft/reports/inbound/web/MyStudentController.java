package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.InactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.ReactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Meus alunos" do professor na Web (feature 005, FR-024 a FR-026). Adaptador fino: o ator vem da
 * sessão do professor (canal Web), nunca do corpo; escopo, permissão, validação, limite de sondagem
 * de CPF e erros são do núcleo, traduzidos pelo {@link ErrorHandlerController}. Nenhuma resposta
 * traz CPF e o CPF não aparece em URL nem em log.
 */
@RestController
@RequestMapping("/api/v1/my-students")
public class MyStudentController {

    private final ListStudentsUseCase listStudents;
    private final GetStudentUseCase getStudent;
    private final RegisterStudentUseCase registerStudent;
    private final UpdateStudentUseCase updateStudent;
    private final InactivateStudentUseCase inactivateStudent;
    private final ReactivateStudentUseCase reactivateStudent;

    public MyStudentController(
            ListStudentsUseCase listStudents,
            GetStudentUseCase getStudent,
            RegisterStudentUseCase registerStudent,
            UpdateStudentUseCase updateStudent,
            InactivateStudentUseCase inactivateStudent,
            ReactivateStudentUseCase reactivateStudent) {
        this.listStudents = listStudents;
        this.getStudent = getStudent;
        this.registerStudent = registerStudent;
        this.updateStudent = updateStudent;
        this.inactivateStudent = inactivateStudent;
        this.reactivateStudent = reactivateStudent;
    }

    @GetMapping
    public MyStudentPageDto list(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ACTIVE") StudentStatusFilter status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var command = new ListStudentsUseCase.Command(search, status, PageRequest.of(page, size));
        return MyStudentMapper.page(listStudents.run(actor(teacher), command));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MyStudentDto register(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @RequestBody MyStudentRequest request) {
        var command =
                new RegisterStudentUseCase.Command(
                        MyStudentMapper.input(request), List.of(), correlation());
        return MyStudentMapper.student(registerStudent.run(actor(teacher), command));
    }

    @GetMapping("/{studentId}")
    public MyStudentDto open(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID studentId) {
        var command = new GetStudentUseCase.Command(studentId, correlation());
        return MyStudentMapper.student(getStudent.run(actor(teacher), command));
    }

    @PatchMapping("/{studentId}")
    public MyStudentDto update(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID studentId,
            @RequestBody MyStudentUpdateRequest request) {
        var command =
                new UpdateStudentUseCase.Command(
                        studentId, MyStudentMapper.input(request), correlation());
        return MyStudentMapper.student(updateStudent.run(actor(teacher), command));
    }

    @PostMapping("/{studentId}/inactivation")
    public MyStudentDto inactivate(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID studentId) {
        var command = new InactivateStudentUseCase.Command(studentId, correlation());
        return MyStudentMapper.student(inactivateStudent.run(actor(teacher), command));
    }

    @PostMapping("/{studentId}/reactivation")
    public MyStudentDto reactivate(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID studentId) {
        var command = new ReactivateStudentUseCase.Command(studentId, correlation());
        return MyStudentMapper.student(reactivateStudent.run(actor(teacher), command));
    }

    /** O ator é sempre o professor da sessão, no canal Web. */
    private static Actor actor(AuthenticatedTeacher teacher) {
        return Actor.teacher(teacher.id(), ActionOrigin.WEB);
    }

    /** Identificador da requisição para a autoria (Web: UUID). */
    private static String correlation() {
        return UUID.randomUUID().toString();
    }
}
