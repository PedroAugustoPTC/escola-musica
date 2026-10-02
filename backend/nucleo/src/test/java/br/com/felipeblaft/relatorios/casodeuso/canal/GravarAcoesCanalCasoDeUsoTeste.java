package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dubles.AcaoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GravarAcoesCanalCasoDeUsoTeste {

    private final RelogioFixo relogio = new RelogioFixo();
    private final AcaoCanalRepositorioEmMemoria repositorio = new AcaoCanalRepositorioEmMemoria();
    private final GravarAcoesCanalCasoDeUso gravar =
            new GravarAcoesCanalCasoDeUso(
                    repositorio,
                    relogio,
                    new PoliticaRetentativa(
                            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)));

    private static NovaAcao nova(String id, UUID professor) {
        return new NovaAcao(id, TipoAcaoCanal.MENSAGEM, 77L, professor, null, "{\"texto\":\"a\"}", null);
    }

    @Test
    void respostaAUpdateGravaEntregueNaOrdemRecebida() {
        int gravadas =
                gravar.executar(
                        "u9",
                        OrigemAcaoCanal.RESPOSTA_UPDATE,
                        9L,
                        List.of(nova("u9-0", null), nova("u9-1", null)));
        assertThat(gravadas).isEqualTo(2);
        List<AcaoCanal> acoes = repositorio.buscarPorUpdate(9L);
        assertThat(acoes).extracting(AcaoCanal::id).containsExactly("u9-0", "u9-1");
        assertThat(acoes).allSatisfy(
                acao -> {
                    assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
                    assertThat(acao.loteId()).isEqualTo("u9");
                    assertThat(acao.prazoEm()).isEqualTo(relogio.agora().plus(Duration.ofHours(24)));
                });
    }

    @Test
    void proativaGravaPendenteDevidaAgora() {
        UUID professor = UUID.randomUUID();
        gravar.executar(
                "p:resultado:t", OrigemAcaoCanal.PROATIVA, null, List.of(nova("p:resultado:t:0", professor)));
        AcaoCanal acao = repositorio.buscarPorId("p:resultado:t:0").orElseThrow();
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(acao.proximaTentativaEm()).isEqualTo(relogio.agora());
        assertThat(acao.professorId()).isEqualTo(professor);
    }

    @Test
    void mesmoIdNaoSobrescreveNemDuplica() {
        gravar.executar("u9", OrigemAcaoCanal.RESPOSTA_UPDATE, 9L, List.of(nova("u9-0", null)));
        repositorio.guardada("u9-0").confirmarExecutada(relogio.agora());
        int segunda =
                gravar.executar(
                        "u9", OrigemAcaoCanal.RESPOSTA_UPDATE, 9L, List.of(nova("u9-0", null)));
        assertThat(segunda).isZero();
        assertThat(repositorio.todas()).hasSize(1);
        assertThat(repositorio.guardada("u9-0").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }
}
