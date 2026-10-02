package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dubles.AcaoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsultarAcaoCanalCasoDeUsoTeste {

    @Test
    void devolveOEstadoDaAcaoOuVazioSeDesconhecida() {
        AcaoCanalRepositorioEmMemoria repositorio = new AcaoCanalRepositorioEmMemoria();
        new GravarAcoesCanalCasoDeUso(
                        repositorio,
                        new RelogioFixo(),
                        new PoliticaRetentativa(
                                Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)))
                .executar(
                        "u1",
                        OrigemAcaoCanal.RESPOSTA_UPDATE,
                        1L,
                        List.of(new NovaAcao("u1-0", TipoAcaoCanal.MENSAGEM, 1L, null, null, "{}", null)));
        ConsultarAcaoCanalCasoDeUso consultar = new ConsultarAcaoCanalCasoDeUso(repositorio);

        assertThat(consultar.executar("u1-0").orElseThrow().estado())
                .isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(consultar.executar("u9-9")).isEmpty();
    }
}
