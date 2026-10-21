package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.util.UUID;

/**
 * Professor como o caso de uso o devolve às interfaces: nunca traz o código de acesso. O {@code
 * toString} não revela dado pessoal.
 */
public record TeacherView(
        UUID id, String name, String email, boolean active, boolean telegramLinked) {

    /** Visão do professor; o código de acesso (hash ou claro) nunca entra. */
    static TeacherView of(Teacher teacher, boolean telegramLinked) {
        return new TeacherView(
                teacher.id(), teacher.name(), teacher.email(), teacher.active(), telegramLinked);
    }

    @Override
    public String toString() {
        return "TeacherView[id=" + id + "]";
    }
}
