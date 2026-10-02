package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dubles.AcaoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cenários C4 a C6 do contrato: update repetido devolve só o que falta confirmar. */
class ObterAcoesNaoConfirmadasDoUpdateCasoDeUsoTeste {

    private final RelogioFixo relogio = new RelogioFixo();
    private final AcaoCanalRepositorioEmMemoria repositorio = new AcaoCanalRepositorioEmMemoria();
    private final ObterAcoesNaoConfirmadasDoUpdateCasoDeUso obter =
            new ObterAcoesNaoConfirmadasDoUpdateCasoDeUso(repositorio);

    private void gravarTres() {
        new GravarAcoesCanalCasoDeUso(
                        repositorio,
                        relogio,
                        new PoliticaRetentativa(
                                Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)))
                .executar(
                        "u5",
                        OrigemAcaoCanal.RESPOSTA_UPDATE,
                        5L,
                        List.of(
                                nova("u5-0"), nova("u5-1"), nova("u5-2")));
    }

    private static NovaAcao nova(String id) {
        return new NovaAcao(id, TipoAcaoCanal.MENSAGEM, 1L, null, null, "{\"texto\":\"x\"}", null);
    }

    @Test
    void nadaConfirmadoDevolveTudoNaMesmaOrdemComOsMesmosIds() {
        gravarTres();
        assertThat(obter.executar(5L)).extracting(AcaoCanal::id).containsExactly("u5-0", "u5-1", "u5-2");
        assertThat(obter.executar(5L)).extracting(AcaoCanal::id).containsExactly("u5-0", "u5-1", "u5-2");
    }

    @Test
    void confirmadasSaemDaLista() {
        gravarTres();
        repositorio.guardada("u5-1").confirmarExecutada(relogio.agora());
        assertThat(obter.executar(5L)).extracting(AcaoCanal::id).containsExactly("u5-0", "u5-2");
    }

    @Test
    void tudoConfirmadoDevolveListaVazia() {
        gravarTres();
        repositorio.todas().forEach(a -> repositorio.guardada(a.id()).confirmarFalha("ERRO_TELEGRAM", relogio.agora()));
        assertThat(obter.executar(5L)).isEmpty();
    }

    @Test
    void updateSemAcoesOuSemConteudoDevolveVazio() {
        assertThat(obter.executar(999L)).isEmpty();
        gravarTres();
        repositorio.guardada("u5-0").zerarPayload();
        assertThat(obter.executar(5L)).extracting(AcaoCanal::id).containsExactly("u5-1", "u5-2");
    }
}
