package br.com.felipeblaft.reports.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
import br.com.felipeblaft.reports.usecase.access.SignInPortalUseCase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A falha de código fica gravada mesmo com o rollback do caso de uso (REQUIRES_NEW, lição 4); zera
 * no sucesso e ao fim do bloqueio; a origem nunca fica em claro.
 */
class TentativaAcessoIT extends IntegracaoBase {

    @Autowired SignInPortalUseCase entrar;
    @Autowired CenarioIT cenario;

    private int tentativas() {
        return jdbc.queryForObject(
                "select coalesce(max(tentativas_incorretas), 0) from tentativa_acesso",
                Integer.class);
    }

    @Test
    void falhaGravadaApesarDoRollbackEOrigemSoComoHash() {
        cenario.clara();
        assertThatThrownBy(() -> entrar.run("ZZZZZZZZ", "198.51.100.7"))
                .isInstanceOf(DomainException.class);
        assertThat(tentativas()).isEqualTo(1);
        String origem =
                jdbc.queryForObject("select origem_hash from tentativa_acesso", String.class);
        assertThat(origem).hasSize(64).doesNotContain("198.51");
        assertThat(jdbc.queryForObject("select count(*) from sessao_web", Integer.class)).isZero();
    }

    @Test
    void zeraNoSucessoEAoFimDoBloqueio() {
        cenario.clara();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> entrar.run("ZZZZZZZZ", "ip"))
                    .isInstanceOf(DomainException.class);
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from tentativa_acesso where bloqueado_ate is not null",
                                Integer.class))
                .isEqualTo(1);
        relogio.avancar(Duration.ofMinutes(15));
        entrar.run(CenarioIT.CODIGO_CLARA, "ip");
        assertThat(tentativas()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from tentativa_acesso where bloqueado_ate is not null",
                                Integer.class))
                .isZero();
    }
}
