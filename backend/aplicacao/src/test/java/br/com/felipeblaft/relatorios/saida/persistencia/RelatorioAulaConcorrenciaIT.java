package br.com.felipeblaft.relatorios.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.RegistrarAulaPorAudioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ResponderPerguntasCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Aprovação concorrente pelos dois canais e unicidade do relatório em aberto (FR-034, FR-033). */
class RelatorioAulaConcorrenciaIT extends IntegracaoBase {

    @Autowired private CenarioIT cenario;
    @Autowired private RegistrarAulaPorAudioCasoDeUso registrar;
    @Autowired private ResponderPerguntasCasoDeUso responder;
    @Autowired private AprovarRelatorioAulaCasoDeUso aprovar;
    @Autowired private RelatorioAulaRepositorio relatorios;
    @Autowired private br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao propriedades;

    private Professor clara;
    private Aluno matteo;
    private UUID relatorioId;

    @BeforeEach
    void chegarAConferencia() throws Exception {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        Path diretorio = propriedades.audio().tempDir();
        Files.createDirectories(diretorio);
        Path arquivo = diretorio.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(arquivo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        registrar.executar(clara.id(), matteo.id(), new ArquivoTemporario(arquivo, 22, "audio/webm"), Canal.WEB);
        cenario.processarFila();
        relatorioId = jdbc.queryForObject("select id from relatorio_aula", UUID.class);
        responder.responderTexto(clara.id(), relatorioId, "Estudou bem.", Canal.WEB);
        cenario.processarFila();
    }

    @Test
    void aprovacaoSimultaneaPelosDoisCanaisSoUmaVence() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService canais = Executors.newFixedThreadPool(2);
        List<Future<String>> resultados = new ArrayList<>();
        for (Canal canal : List.of(Canal.WEB, Canal.TELEGRAM)) {
            Callable<String> tentativa =
                    () -> {
                        largada.await();
                        try {
                            aprovar.executar(clara.id(), relatorioId, 1, canal);
                            return "APROVADO";
                        } catch (ExcecaoDominio recusa) {
                            return recusa.codigo().name();
                        } catch (ConflitoConcorrenciaExcecao conflito) {
                            return "CONFLITO";
                        }
                    };
            resultados.add(canais.submit(tentativa));
        }
        largada.countDown();
        List<String> codigos = new ArrayList<>();
        for (Future<String> resultado : resultados) {
            codigos.add(resultado.get(60, TimeUnit.SECONDS));
        }
        canais.shutdown();

        assertThat(codigos).containsOnlyOnce("APROVADO");
        assertThat(codigos).filteredOn(c -> !c.equals("APROVADO"))
                .singleElement()
                .isIn(CodigoErro.RELATORIO_JA_APROVADO.name(), "CONFLITO");
        assertThat(jdbc.queryForObject("select count(*) from documento_pdf where tipo = 'OFICIAL'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from relatorio_aula", String.class)).isEqualTo("APROVADO");
    }

    @Test
    void segundaAprovacaoDepoisDaPrimeiraERecusada() {
        aprovar.executar(clara.id(), relatorioId, 1, Canal.WEB);

        assertThatThrownBy(() -> aprovar.executar(clara.id(), relatorioId, 1, Canal.TELEGRAM))
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoErro.RELATORIO_JA_APROVADO));
    }

    @Test
    void indiceParcialImpedeSegundoRelatorioEmAbertoParaOMesmoAluno() {
        Aula aula = Aula.nova(clara.id(), matteo.id(), relogio.hojeNoFusoDaEscola(), relogio.agora());
        relatorios.salvarAula(aula);
        RelatorioAula outro = RelatorioAula.criar(aula, "transcrição", Canal.TELEGRAM, relogio.agora());

        assertThatThrownBy(() -> relatorios.salvar(outro)).isInstanceOf(ConflitoConcorrenciaExcecao.class);
    }
}
