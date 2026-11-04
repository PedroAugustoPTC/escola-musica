package br.com.felipeblaft.reports.domain.access;

import static br.com.felipeblaft.reports.domain.access.Permission.ALLOW;
import static br.com.felipeblaft.reports.domain.access.Permission.ALLOW_OWN_STUDENTS;
import static br.com.felipeblaft.reports.domain.access.Permission.ALLOW_SOLE_ACTIVE_TEACHER;
import static br.com.felipeblaft.reports.domain.access.Permission.DENY;
import static br.com.felipeblaft.reports.domain.access.Permission.PUBLIC;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Matriz de permissões (FR-048): decide, no núcleo, o que cada ator pode fazer em cada operação,
 * independentemente de interface, canal ou transporte. Negada por padrão: operação ausente é {@link
 * Permission#DENY}. A matriz padrão é idêntica a {@code
 * specs/005-admin-registrations-access/contracts/permission-matrix.json}; um teste de deriva
 * reprova qualquer divergência.
 */
public final class PermissionMatrix {

    /**
     * Linha da matriz: a permissão de cada ator (administrador na Web, professor na Web, professor
     * no Telegram) e as origens de sistema que podem executar a operação.
     */
    public record Rule(
            Permission administratorWeb,
            Permission teacherWeb,
            Permission teacherTelegram,
            Set<ActionOrigin> system) {

        public Rule {
            Objects.requireNonNull(administratorWeb);
            Objects.requireNonNull(teacherWeb);
            Objects.requireNonNull(teacherTelegram);
            system =
                    system.isEmpty()
                            ? Set.of()
                            : Collections.unmodifiableSet(EnumSet.copyOf(system));
        }
    }

    private static final PermissionMatrix STANDARD = new PermissionMatrix(standardRules());

    private final Map<Operation, Rule> rules;

    public PermissionMatrix(Map<Operation, Rule> rules) {
        EnumMap<Operation, Rule> copy = new EnumMap<>(Operation.class);
        copy.putAll(rules);
        this.rules = Collections.unmodifiableMap(copy);
    }

    /** A matriz da feature 005, espelho do contrato. */
    public static PermissionMatrix standard() {
        return STANDARD;
    }

    public Map<Operation, Rule> rules() {
        return rules;
    }

    /**
     * Permissão do ator na operação. Administrador só existe na Web; o ator de sistema só executa o
     * que a matriz lista para a sua origem; combinação sem linha é negada.
     */
    public Permission decide(Operation operation, Actor actor) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(actor);
        Rule rule = rules.get(operation);
        if (rule == null) {
            return DENY;
        }
        if (actor.isSystem()) {
            return rule.system().contains(actor.origin()) ? ALLOW : DENY;
        }
        return switch (actor.role()) {
            case ADMINISTRATOR ->
                    actor.origin() == ActionOrigin.WEB ? rule.administratorWeb() : DENY;
            // o Actor só admite professor na Web ou no Telegram
            case TEACHER ->
                    actor.origin() == ActionOrigin.WEB ? rule.teacherWeb() : rule.teacherTelegram();
        };
    }

    /**
     * Permissão de quem chega sem credencial: só as operações de entrada são {@link
     * Permission#PUBLIC}; tudo o mais é negado.
     */
    public Permission decideAnonymous(Operation operation) {
        Objects.requireNonNull(operation);
        Rule rule = rules.get(operation);
        if (rule == null) {
            return DENY;
        }
        boolean publicOperation =
                rule.administratorWeb() == PUBLIC
                        || rule.teacherWeb() == PUBLIC
                        || rule.teacherTelegram() == PUBLIC;
        return publicOperation ? PUBLIC : DENY;
    }

    private static Map<Operation, Rule> standardRules() {
        Map<Operation, Rule> map = new EnumMap<>(Operation.class);
        // entrada do administrador
        map.put(Operation.ADMIN_SIGN_IN, new Rule(PUBLIC, PUBLIC, DENY, Set.of()));
        map.put(Operation.ADMIN_ACTIVATE, new Rule(PUBLIC, PUBLIC, DENY, Set.of()));
        map.put(Operation.ADMIN_GET_SESSION, onlyAdministrator(Set.of()));
        map.put(Operation.ADMIN_SIGN_OUT, onlyAdministrator(Set.of()));
        map.put(Operation.ADMIN_CHANGE_PASSWORD, onlyAdministrator(Set.of()));
        // entrada do professor (rota da 001; o Telegram vincula pelo mesmo código)
        map.put(Operation.TEACHER_SIGN_IN, new Rule(PUBLIC, PUBLIC, PUBLIC, Set.of()));
        // professores
        Set<ActionOrigin> load = Set.of(ActionOrigin.INITIAL_LOAD);
        map.put(Operation.TEACHER_LIST, onlyAdministrator(Set.of()));
        map.put(Operation.TEACHER_GET, onlyAdministrator(Set.of()));
        map.put(Operation.TEACHER_REGISTER, onlyAdministrator(load));
        map.put(Operation.TEACHER_UPDATE, onlyAdministrator(load));
        map.put(Operation.TEACHER_INACTIVATE, onlyAdministrator(load));
        map.put(Operation.TEACHER_REACTIVATE, onlyAdministrator(load));
        map.put(Operation.TEACHER_REISSUE_ACCESS_CODE, onlyAdministrator(load));
        map.put(Operation.TEACHER_UNLINK_TELEGRAM, onlyAdministrator(Set.of()));
        // administradores
        map.put(Operation.ADMINISTRATOR_LIST, onlyAdministrator(Set.of()));
        map.put(Operation.ADMINISTRATOR_CREATE, onlyAdministrator(Set.of()));
        map.put(Operation.ADMINISTRATOR_INACTIVATE, onlyAdministrator(Set.of()));
        map.put(Operation.ADMINISTRATOR_REACTIVATE, onlyAdministrator(Set.of()));
        map.put(
                Operation.FIRST_ADMINISTRATOR_BOOTSTRAP,
                new Rule(DENY, DENY, DENY, Set.of(ActionOrigin.SYSTEM_STARTUP)));
        // associações
        map.put(Operation.ASSIGNMENT_ASSIGN, onlyAdministrator(load));
        map.put(Operation.ASSIGNMENT_REMOVE, onlyAdministrator(Set.of()));
        map.put(Operation.ASSIGNMENT_PREVIEW_REMOVAL, onlyAdministrator(Set.of()));
        // alunos: o professor age só sobre os próprios
        map.put(Operation.STUDENT_LIST, ownStudents(Set.of()));
        map.put(Operation.STUDENT_GET, ownStudents(Set.of()));
        map.put(Operation.STUDENT_REGISTER, ownStudents(load));
        map.put(Operation.STUDENT_UPDATE, ownStudents(load));
        Rule situation =
                new Rule(ALLOW, ALLOW_SOLE_ACTIVE_TEACHER, ALLOW_SOLE_ACTIVE_TEACHER, Set.of());
        map.put(Operation.STUDENT_INACTIVATE, situation);
        map.put(Operation.STUDENT_REACTIVATE, situation);
        // relatórios (001): o administrador nunca toca em relatório (Princípio III)
        map.put(
                Operation.LESSON_AND_SEMESTER_REPORTS,
                new Rule(DENY, ALLOW_OWN_STUDENTS, ALLOW_OWN_STUDENTS, Set.of()));
        // visão da escola (006): só o administrador, só leitura de relatórios aprovados
        map.put(Operation.SCHOOL_HISTORY_QUERY, onlyAdministrator(Set.of()));
        map.put(Operation.SCHOOL_REPORT_DOCUMENT_OPEN, onlyAdministrator(Set.of()));
        map.put(Operation.SCHOOL_REPORT_DOCUMENT_DOWNLOAD, onlyAdministrator(Set.of()));
        return map;
    }

    private static Rule onlyAdministrator(Set<ActionOrigin> system) {
        return new Rule(ALLOW, DENY, DENY, system);
    }

    private static Rule ownStudents(Set<ActionOrigin> system) {
        return new Rule(ALLOW, ALLOW_OWN_STUDENTS, ALLOW_OWN_STUDENTS, system);
    }
}
