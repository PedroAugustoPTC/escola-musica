package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import java.util.UUID;

/**
 * Atores prontos para os testes: administrador da Web, professor da Web, professor do Telegram e
 * ator de sistema. Os ids são fixos para que os testes comparem registros e alvos sem ruído.
 */
public final class ActorFixtures {

    public static final UUID ADMINISTRATOR_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final UUID TEACHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    private ActorFixtures() {}

    /** Administrador autenticado na Web, com senha definitiva. */
    public static Actor administratorWeb() {
        return administratorWeb(ADMINISTRATOR_ID);
    }

    public static Actor administratorWeb(UUID id) {
        return Actor.administrator(id, false);
    }

    /** Administrador com senha provisória: só pode trocar a senha. */
    public static Actor administratorWithProvisionalPassword() {
        return Actor.administrator(ADMINISTRATOR_ID, true);
    }

    public static Actor teacherWeb() {
        return teacherWeb(TEACHER_ID);
    }

    public static Actor teacherWeb(UUID id) {
        return Actor.teacher(id, ActionOrigin.WEB);
    }

    public static Actor teacherTelegram() {
        return teacherTelegram(TEACHER_ID);
    }

    public static Actor teacherTelegram(UUID id) {
        return Actor.teacher(id, ActionOrigin.TELEGRAM);
    }

    /** Ator de sistema da carga inicial. */
    public static Actor initialLoad() {
        return Actor.system(ActionOrigin.INITIAL_LOAD);
    }

    /** Ator de sistema da inicialização (primeiro administrador). */
    public static Actor bootstrap() {
        return Actor.system(ActionOrigin.SYSTEM_STARTUP);
    }
}
