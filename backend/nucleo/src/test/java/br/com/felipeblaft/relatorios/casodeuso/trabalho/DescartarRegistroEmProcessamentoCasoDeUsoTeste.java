package br.com.felipeblaft.relatorios.casodeuso.trabalho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DescartarRegistroEmProcessamentoCasoDeUsoTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    private TrabalhoProcessamento registrar() {
        ArquivoTemporario arquivo = CenarioRelatorio.arquivo("aula.webm", 2048);
        return c.registrar.executar(c.clara.id(), c.matteo.id(), arquivo, Canal.WEB);
    }

    @Test
    void trabalhoPendenteEDescartadoEOAudioApagado() {
        TrabalhoProcessamento trabalho = registrar();

        c.descartarRegistro.executar(c.clara.id(), trabalho.id());

        TrabalhoProcessamento descartado = c.fila.buscarPorId(trabalho.id()).orElseThrow();
        assertThat(descartado.status()).isEqualTo(StatusTrabalho.DESCARTADO);
        assertThat(descartado.arquivoTemporario()).isNull();
        assertThat(c.normalizacao.apagados).contains(Path.of("/tmp/audios/aula.webm"));
        c.processarFila();
        assertThat(c.relatorios.relatorios.values()).isEmpty();
        // o aluno fica livre para um novo registro
        assertThat(registrar().status()).isEqualTo(StatusTrabalho.PENDENTE);
    }

    @Test
    void resultadoDoWorkerDepoisDoDescarteEIgnorado() {
        TrabalhoProcessamento trabalho = registrar();
        TrabalhoProcessamento emExecucao = c.fila.reservarProximo(c.relogio.agora()).orElseThrow();

        c.descartarRegistro.executar(c.clara.id(), trabalho.id());
        c.processador.processar(emExecucao);

        assertThat(c.relatorios.relatorios.values()).isEmpty();
        assertThat(c.fila.buscarPorId(trabalho.id()).orElseThrow().status())
                .isEqualTo(StatusTrabalho.DESCARTADO);
        assertThat(c.notificacao.eventos).noneMatch(e -> e.startsWith("resultado:"));
    }

    @Test
    void outroProfessorRecebeNaoEncontrado() {
        TrabalhoProcessamento trabalho = registrar();

        assertThatThrownBy(() -> c.descartarRegistro.executar(c.bruno.id(), trabalho.id()))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoErro.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void trabalhoConcluidoOuComRelatorioNaoPodeSerDescartado() {
        RelatorioAula relatorio = c.relatorioComPerguntas();
        TrabalhoProcessamento concluido =
                c.fila.trabalhos.values().stream()
                        .filter(t -> relatorio.id().equals(t.relatorioId()))
                        .findFirst()
                        .orElseThrow();

        assertThatThrownBy(() -> c.descartarRegistro.executar(c.clara.id(), concluido.id()))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e ->
                                assertThat(e.codigo())
                                        .isEqualTo(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA));
    }
}
