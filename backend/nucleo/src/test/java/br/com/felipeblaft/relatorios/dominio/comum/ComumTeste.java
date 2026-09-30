package br.com.felipeblaft.relatorios.dominio.comum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComumTeste {

    @Test
    void geraUuidVersaoSeteUnicoEOrdenavel() throws InterruptedException {
        Set<UUID> ids = new HashSet<>();
        UUID anterior = GeradorId.novo();
        Thread.sleep(2);
        for (int i = 0; i < 1000; i++) {
            UUID id = GeradorId.novo();
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            ids.add(id);
        }
        UUID posterior = GeradorId.novo();
        assertThat(ids).hasSize(1000);
        assertThat(Long.compareUnsigned(anterior.getMostSignificantBits() >>> 16,
                        posterior.getMostSignificantBits() >>> 16))
                .isNegative();
    }

    @Test
    void excecaoDeDominioCarregaCodigoEDetalhesImutaveis() {
        ExcecaoDominio erro =
                new ExcecaoDominio(CodigoErro.VERSAO_DESATUALIZADA, Map.of("versaoVigente", 3));
        assertThat(erro.codigo()).isEqualTo(CodigoErro.VERSAO_DESATUALIZADA);
        assertThat(erro.getMessage()).isEqualTo("VERSAO_DESATUALIZADA");
        assertThat(erro.detalhes()).containsEntry("versaoVigente", 3);
        assertThatThrownBy(() -> erro.detalhes().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ExcecaoDominio(CodigoErro.ERRO_INTERNO).detalhes()).isEmpty();
    }

    @Test
    void chaveDeMensagemDoCodigo() {
        assertThat(CodigoErro.CODIGO_INVALIDO.chaveMensagem()).isEqualTo("CODIGO_INVALIDO");
        assertThat(CodigoErro.RELATORIO_CANCELADO.chaveMensagem())
                .isEqualTo("RELATORIO_CANCELADO_ERRO");
    }

    @Test
    void parametrosPadraoDaSpec() {
        ParametrosNegocio padrao = ParametrosNegocio.padrao();
        assertThat(padrao.validadeCodigo()).isEqualTo(Duration.ofDays(7));
        assertThat(padrao.maximoTentativasAcesso()).isEqualTo(5);
        assertThat(padrao.duracaoBloqueio()).isEqualTo(Duration.ofMinutes(15));
        assertThat(padrao.inatividadeSessaoWeb()).isEqualTo(Duration.ofHours(12));
        assertThat(padrao.expiracaoAudioPendente()).isEqualTo(Duration.ofMinutes(30));
        assertThat(padrao.limiteAudioMb()).isEqualTo(50);
    }

    @Test
    void parametrosInvalidosSaoRecusados() {
        Duration d = Duration.ofMinutes(1);
        assertThatThrownBy(() -> new ParametrosNegocio(d, 0, d, d, d, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParametrosNegocio(d, 1, d, d, d, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParametrosNegocio(null, 1, d, d, d, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void enumeracoesDosCanais() {
        assertThat(Canal.values()).containsExactly(Canal.WEB, Canal.TELEGRAM);
        assertThat(Meio.valueOf("AUDIO")).isEqualTo(Meio.AUDIO);
        assertThat(TipoRelatorio.valueOf("SEMESTRAL")).isEqualTo(TipoRelatorio.SEMESTRAL);
    }
}
