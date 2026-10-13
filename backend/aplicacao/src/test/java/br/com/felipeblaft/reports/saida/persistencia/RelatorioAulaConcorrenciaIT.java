package br.com.felipeblaft.reports.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
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
    @Autowired private RegisterLessonByAudioUseCase registrar;
    @Autowired private AnswerQuestionsUseCase responder;
    @Autowired private ApproveLessonReportUseCase aprovar;
    @Autowired private LessonReportRepository relatorios;

    @Autowired private br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao propriedades;

    private Teacher clara;
    private Student matteo;
    private UUID relatorioId;

    @BeforeEach
    void chegarAConferencia() throws Exception {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        Path diretorio = propriedades.audio().tempDir();
        Files.createDirectories(diretorio);
        Path arquivo = diretorio.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(arquivo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        registrar.run(
                clara.id(), matteo.id(), new TemporaryFile(arquivo, 22, "audio/webm"), Channel.WEB);
        cenario.processarFila();
        relatorioId = jdbc.queryForObject("select id from relatorio_aula", UUID.class);
        responder.answerText(clara.id(), relatorioId, "Estudou bem.", Channel.WEB);
        cenario.processarFila();
    }

    @Test
    void aprovacaoSimultaneaPelosDoisCanaisSoUmaVence() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService canais = Executors.newFixedThreadPool(2);
        List<Future<String>> resultados = new ArrayList<>();
        for (Channel canal : List.of(Channel.WEB, Channel.TELEGRAM)) {
            Callable<String> tentativa =
                    () -> {
                        largada.await();
                        try {
                            aprovar.run(clara.id(), relatorioId, 1, canal);
                            return "APROVADO";
                        } catch (DomainException recusa) {
                            return recusa.code().name();
                        } catch (ConcurrencyConflictException conflito) {
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
        assertThat(codigos)
                .filteredOn(c -> !c.equals("APROVADO"))
                .singleElement()
                .isIn(ErrorCode.RELATORIO_JA_APROVADO.name(), "CONFLITO");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from documento_pdf where tipo = 'OFICIAL'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from relatorio_aula", String.class))
                .isEqualTo("APROVADO");
    }

    @Test
    void segundaAprovacaoDepoisDaPrimeiraERecusada() {
        aprovar.run(clara.id(), relatorioId, 1, Channel.WEB);

        assertThatThrownBy(() -> aprovar.run(clara.id(), relatorioId, 1, Channel.TELEGRAM))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RELATORIO_JA_APROVADO));
    }

    @Test
    void indiceParcialImpedeSegundoRelatorioEmAbertoParaOMesmoAluno() {
        Lesson aula =
                Lesson.create(
                        clara.id(), matteo.id(), relogio.todayInSchoolTimezone(), relogio.now());
        relatorios.saveLesson(aula);
        LessonReport outro =
                LessonReport.create(aula, "transcrição", Channel.TELEGRAM, relogio.now());

        assertThatThrownBy(() -> relatorios.save(outro))
                .isInstanceOf(ConcurrencyConflictException.class);
    }
}
