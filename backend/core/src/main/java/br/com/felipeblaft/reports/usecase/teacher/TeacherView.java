package br.com.felipeblaft.reports.usecase.teacher;

import java.util.UUID;

/**
 * Professor como o caso de uso o devolve às interfaces: nunca traz o código de acesso. O {@code
 * toString} não revela dado pessoal.
 */
public record TeacherView(
        UUID id, String name, String email, boolean active, boolean telegramLinked) {

    @Override
    public String toString() {
        return "TeacherView[id=" + id + "]";
    }
}
