package br.com.felipeblaft.reports.architecture.fixture;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;

/** Cria o ator de sistema fora dos pontos permitidos (apenas autoteste do ArchUnit). */
public class SystemActorFixture {

    public Actor create() {
        return Actor.system(ActionOrigin.INITIAL_LOAD);
    }
}
