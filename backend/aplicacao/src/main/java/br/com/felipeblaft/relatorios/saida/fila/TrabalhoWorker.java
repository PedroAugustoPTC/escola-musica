package br.com.felipeblaft.relatorios.saida.fila;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.saida.persistencia.FilaTrabalhoPostgresAdaptador;
import br.com.felipeblaft.relatorios.servico.ProcessadorTrabalhoServico;
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

    private final FilaTrabalhoPorta fila;
    private final FilaTrabalhoPostgresAdaptador filaPostgres;
    private final ProcessadorTrabalhoServico processador;
    private final RelogioPorta relogio;
    private final Semaphore vagas;
    private final int workers;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public TrabalhoWorker(
            FilaTrabalhoPorta fila,
            FilaTrabalhoPostgresAdaptador filaPostgres,
            ProcessadorTrabalhoServico processador,
            RelogioPorta relogio,
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
        int recuperados = filaPostgres.recuperarTravados(relogio.agora());
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
            Optional<TrabalhoProcessamento> proximo;
            try {
                proximo = fila.reservarProximo(relogio.agora());
            } catch (RuntimeException erro) {
                vagas.release();
                LOG.warn("fila-indisponivel tipo={}", erro.getClass().getSimpleName());
                return;
            }
            if (proximo.isEmpty()) {
                vagas.release();
                return;
            }
            TrabalhoProcessamento trabalho = proximo.get();
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

    void executar(TrabalhoProcessamento trabalho) {
        MDC.put("trabalhoId", trabalho.id().toString());
        MDC.put("canal", trabalho.canalSolicitacao().name());
        long inicio = System.currentTimeMillis();
        try {
            processador.processar(trabalho);
            LOG.info(
                    "trabalho-concluido tipo={} resultado={} duracaoMs={}",
                    trabalho.tipo(),
                    trabalho.resultado(),
                    System.currentTimeMillis() - inicio);
        } catch (RuntimeException | Error erro) {
            // o executor descartaria o erro em silêncio: registra sempre, mas só o tipo do
            // trabalho,
            // o da exceção e o ponto do código; a mensagem e a causa podem trazer SQL, texto de
            // relatório ou dado de aluno (FR-055)
            LOG.error(
                    "trabalho-falhou tipo={} erro={} local={}",
                    trabalho.tipo(),
                    erro.getClass().getName(),
                    localDe(erro));
        } finally {
            MDC.clear();
            vagas.release();
        }
    }
}
