package br.com.felipeblaft.reports.saida.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.saida.persistencia.FilaTrabalhoPostgresAdaptador;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Fila persistida no PostgreSQL (R-06): reserva sem duplicar, índice parcial e recuperação. */
class FilaTrabalhoPostgresAdaptadorIT extends IntegracaoBase {

    @Autowired private FilaTrabalhoPostgresAdaptador fila;
    @Autowired private CenarioIT cenario;

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void preparar() {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
    }

    private ProcessingJob registro(Student aluno) {
        return ProcessingJob.create(
                JobType.REGISTRO_AULA,
                clara.id(),
                aluno.id(),
                null,
                null,
                Channel.WEB,
                Medium.AUDIO,
                JobStep.ENVIANDO,
                relogio.now());
    }

    @Test
    void doisWorkersNaoReservamOMesmoTrabalho() throws Exception {
        for (int i = 0; i < 12; i++) {
            fila.enqueue(registro(cenario.aluno(clara, "Aluno " + i, null)));
        }
        Set<UUID> reservados = ConcurrentHashMap.newKeySet();
        List<UUID> todos = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        List<Future<?>> execucoes = new ArrayList<>();
        for (int w = 0; w < 2; w++) {
            execucoes.add(
                    workers.submit(
                            () -> {
                                largada.await();
                                Optional<ProcessingJob> proximo;
                                while ((proximo = fila.reserveNext(relogio.now())).isPresent()) {
                                    reservados.add(proximo.get().id());
                                    todos.add(proximo.get().id());
                                }
                                return null;
                            }));
        }
        largada.countDown();
        for (Future<?> execucao : execucoes) {
            execucao.get(60, TimeUnit.SECONDS);
        }
        workers.shutdown();

        assertThat(todos).hasSize(12);
        assertThat(reservados).hasSize(12);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from trabalho_processamento where status = 'EM_EXECUCAO'",
                                Integer.class))
                .isEqualTo(12);
    }

    @Test
    void indiceParcialImpedeDoisRegistrosEmCursoParaOMesmoAluno() {
        ProcessingJob primeiro = registro(matteo);
        fila.enqueue(primeiro);

        assertThatThrownBy(() -> fila.enqueue(registro(matteo)))
                .isInstanceOf(ConcurrencyConflictException.class);

        primeiro.start(relogio.now());
        primeiro.complete(JobResult.AUDIO_NAO_PROCESSAVEL, relogio.now());
        fila.update(primeiro);
        fila.enqueue(registro(matteo));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from trabalho_processamento", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void trabalhoTravadoVoltaParaAFilaEOQueDependiaDeArquivoFalha() {
        ProcessingJob semArquivo = registro(matteo);
        fila.enqueue(semArquivo);
        ProcessingJob comArquivo = registro(cenario.aluno(clara, "Helena Prado", "Violino"));
        comArquivo.defineTemporaryFile("/tmp/audios/sumiu.webm");
        fila.enqueue(comArquivo);
        fila.reserveNext(relogio.now());
        fila.reserveNext(relogio.now());

        assertThat(fila.recuperarTravados(relogio.now())).isEqualTo(2);

        assertThat(fila.searchById(semArquivo.id()).orElseThrow().status())
                .isEqualTo(JobStatus.PENDENTE);
        ProcessingJob falhou = fila.searchById(comArquivo.id()).orElseThrow();
        assertThat(falhou.status()).isEqualTo(JobStatus.FALHOU);
        assertThat(falhou.result()).isEqualTo(JobResult.AUDIO_NAO_PROCESSAVEL);
        assertThat(falhou.temporaryFile()).isNull();
    }

    @Test
    void avisoDeDemoraEMarcadoUmaVezESobreviveAGravacaoDoWorker() {
        ProcessingJob trabalho = registro(matteo);
        fila.enqueue(trabalho);
        ProcessingJob doWorker = fila.reserveNext(relogio.now()).orElseThrow();

        relogio.avancar(Duration.ofMinutes(3));
        assertThat(fila.listDelayedWithoutNotice(relogio.now().minus(Duration.ofMinutes(3))))
                .hasSize(1);
        assertThat(fila.markDelayNotice(trabalho.id())).isTrue();
        assertThat(fila.markDelayNotice(trabalho.id())).isFalse();

        doWorker.advance(JobStep.TRANSCREVENDO);
        fila.update(doWorker);

        assertThat(fila.searchById(trabalho.id()).orElseThrow().delayNoticeSent()).isTrue();
        assertThat(fila.listDelayedWithoutNotice(relogio.now())).isEmpty();
    }

    @Test
    void gravacaoDoWorkerNaoRessuscitaTrabalhoDescartadoNemApagaAMensagemDeEtapa() {
        ProcessingJob trabalho = registro(matteo);
        fila.enqueue(trabalho);
        ProcessingJob doWorker = fila.reserveNext(relogio.now()).orElseThrow();
        fila.registerStepMessage(trabalho.id(), 321);

        doWorker.advance(JobStep.TRANSCREVENDO);
        fila.update(doWorker);
        assertThat(
                        fila.searchById(trabalho.id())
                                .orElseThrow()
                                .parameter(ProcessingJob.STEP_MESSAGE))
                .contains("321");
        assertThat(doWorker.parameter(ProcessingJob.STEP_MESSAGE)).contains("321");

        ProcessingJob descartado = fila.searchById(trabalho.id()).orElseThrow();
        descartado.discard(relogio.now());
        fila.update(descartado);
        doWorker.advance(JobStep.ESTRUTURANDO);
        fila.update(doWorker);

        assertThat(fila.searchById(trabalho.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DESCARTADO);
    }
}
