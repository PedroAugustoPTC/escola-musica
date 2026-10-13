package br.com.felipeblaft.reports.saida.fila;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.saida.persistencia.FilaTrabalhoPostgresAdaptador;
import br.com.felipeblaft.reports.service.JobProcessorService;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consome a fila de trabalhos em threads virtuais (R-06). O número de trabalhos simultâneos é
 * configurável ({@code TRABALHOS_WORKERS}); com zero, a fila não é consumida (testes).
 */
@Component
@Profile("!carga")
public class TrabalhoWorker {

    private static final Logger LOG = LoggerFactory.getLogger(TrabalhoWorker.class);

    private final JobQueuePort fila;
    private final FilaTrabalhoPostgresAdaptador filaPostgres;
    private final JobProcessorService processador;
    private final ClockPort relogio;
    private final Semaphore vagas;
    private final int workers;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public TrabalhoWorker(
            JobQueuePort fila,
            FilaTrabalhoPostgresAdaptador filaPostgres,
            JobProcessorService processador,
            ClockPort relogio,
            PropriedadesAplicacao propriedades) {
        this.fila = fila;
        this.filaPostgres = filaPostgres;
        this.processador = processador;
        this.relogio = relogio;
        this.workers = propriedades.trabalhos().workers();
        this.vagas = new Semaphore(Math.max(0, workers));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        int recuperados = filaPostgres.recuperarTravados(relogio.now());
        if (recuperados > 0) {
            LOG.info("trabalhos-recuperados quantidade={}", recuperados);
        }
    }

    @Scheduled(fixedDelayString = "${relatorios.trabalhos.intervalo-ms:500}")
    public void consumir() {
        if (workers <= 0) {
            return;
        }
        while (vagas.tryAcquire()) {
            Optional<ProcessingJob> proximo;
            try {
                proximo = fila.reserveNext(relogio.now());
            } catch (RuntimeException erro) {
                vagas.release();
                LOG.warn("fila-indisponivel tipo={}", erro.getClass().getSimpleName());
                return;
            }
            if (proximo.isEmpty()) {
                vagas.release();
                return;
            }
            ProcessingJob trabalho = proximo.get();
            executor.submit(() -> executar(trabalho));
        }
    }

    /**
     * Primeiro ponto do código da aplicação na pilha, para depurar sem expor o conteúdo do erro.
     */
    private static String localDe(Throwable erro) {
        for (StackTraceElement ponto : erro.getStackTrace()) {
            if (ponto.getClassName().startsWith("br.com.felipeblaft")) {
                return ponto.getClassName()
                        + "."
                        + ponto.getMethodName()
                        + ":"
                        + ponto.getLineNumber();
            }
        }
        return "desconhecido";
    }

    void executar(ProcessingJob trabalho) {
        MDC.put("trabalhoId", trabalho.id().toString());
        MDC.put("canal", trabalho.requestChannel().name());
        long inicio = System.currentTimeMillis();
        try {
            processador.process(trabalho);
            LOG.info(
                    "trabalho-concluido tipo={} resultado={} duracaoMs={}",
                    trabalho.type(),
                    trabalho.result(),
                    System.currentTimeMillis() - inicio);
        } catch (RuntimeException | Error erro) {
            // o executor descartaria o erro em silêncio: registra sempre, mas só o tipo do
            // trabalho,
            // o da exceção e o ponto do código; a mensagem e a causa podem trazer SQL, texto de
            // relatório ou dado de aluno (FR-055)
            LOG.error(
                    "trabalho-falhou tipo={} erro={} local={}",
                    trabalho.type(),
                    erro.getClass().getName(),
                    localDe(erro));
        } finally {
            MDC.clear();
            vagas.release();
        }
    }
}
