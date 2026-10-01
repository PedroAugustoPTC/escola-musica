package br.com.felipeblaft.relatorios.casodeuso.aula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObterRelatorioEmAbertoCasoDeUsoTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    @Test
    void semRelatorioEmAbertoDevolveVazio() {
        assertThat(c.obterEmAberto.executar(c.clara.id(), c.matteo.id())).isEmpty();
    }

    @Test
    void devolveORelatorioComCanalEUltimaAlteracao() {
        RelatorioEmAberto aberto =
                new RelatorioEmAberto(
                        TipoRelatorio.AULA,
                        UUID.randomUUID(),
                        null,
                        c.matteo.id(),
                        "Matteo Mafra",
                        StatusRelatorio.AGUARDANDO_CONFERENCIA,
                        null,
                        null,
                        Canal.TELEGRAM,
                        Instant.parse("2026-09-20T12:57:00Z"));
        c.emAberto.adicionar(c.clara.id(), aberto);

        assertThat(c.obterEmAberto.executar(c.clara.id(), c.matteo.id())).contains(aberto);
    }

    @Test
    void alunoDeOutroProfessorNaoERevelado() {
        assertThatThrownBy(() -> c.obterEmAberto.executar(c.clara.id(), c.helena.id()))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoErro.ALUNO_NAO_ASSOCIADO));
    }
}
