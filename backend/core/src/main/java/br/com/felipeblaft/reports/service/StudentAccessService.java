package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Escopo de alunos por ator (R-14): o administrador enxerga a escola inteira; o professor, só os
 * alunos associados. O ator de sistema (sem papel) é negado por padrão com {@code
 * RESOURCE_NOT_FOUND}, o mesmo erro do aluno inexistente; a única exceção é a carga inicial ({@link
 * ActionOrigin#INITIAL_LOAD}), que orquestra os casos de uso de cadastro e precisa do escopo total.
 * A inicialização ({@code SYSTEM_STARTUP}) segue negada. O escopo vai na própria consulta ({@code
 * findVisible}, uma só leitura); nunca se carrega o aluno para depois comparar. Aluno alheio, sem
 * professor ou inexistente produzem o mesmo erro, sem nenhum dado.
 */
public class StudentAccessService {

    private final StudentRepository students;

    public StudentAccessService(StudentRepository students) {
        this.students = Objects.requireNonNull(students);
    }

    /**
     * Escopo do ator. O {@code switch} é exaustivo sobre {@link Role}: um papel novo não compila
     * sem decisão explícita aqui, em vez de herdar o acesso à escola inteira.
     */
    public StudentScope scopeOf(Actor actor) {
        Role role = actor.role();
        if (role == null) {
            if (actor.origin() == ActionOrigin.INITIAL_LOAD) {
                return StudentScope.all();
            }
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return switch (role) {
            case ADMINISTRATOR -> StudentScope.all();
            case TEACHER -> StudentScope.ofTeacher(actor.id());
        };
    }

    /** Aluno visível ao ator, ou {@code RESOURCE_NOT_FOUND} (alheio ≡ inexistente). */
    public Student findVisible(Actor actor, UUID studentId) {
        return students.findVisible(scopeOf(actor), studentId)
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Alunos do escopo do ator na situação pedida, em ordem alfabética. */
    public List<Student> list(Actor actor, StudentStatusFilter status) {
        return students.list(scopeOf(actor), status);
    }
}
