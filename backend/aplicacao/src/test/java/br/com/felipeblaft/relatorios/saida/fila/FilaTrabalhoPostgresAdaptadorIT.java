package br.com.felipeblaft.relatorios.saida.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.saida.persistencia.FilaTrabalhoPostgresAdaptador;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
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

    private Professor clara;
    private Aluno matteo;

    @BeforeEach
    void preparar() {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
    }

    private TrabalhoProcessamento registro(Aluno aluno) {
        return TrabalhoProcessamento.novo(
                TipoTrabalho.REGISTRO_AULA,
                clara.id(),
                aluno.id(),
                null,
                null,
                Canal.WEB,
                Meio.AUDIO,
                EtapaTrabalho.ENVIANDO,
                relogio.agora());
    }

    @Test
    void doisWorkersNaoReservamOMesmoTrabalho() throws Exception {
        for (int i = 0; i < 12; i++) {
            fila.enfileirar(registro(cenario.aluno(clara, "Aluno " + i, null)));
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
                                Optional<TrabalhoProcessamento> proximo;
                                while ((proximo = fila.reservarProximo(relogio.agora()))
                                        .isPresent()) {
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
        TrabalhoProcessamento primeiro = registro(matteo);
        fila.enfileirar(primeiro);

        assertThatThrownBy(() -> fila.enfileirar(registro(matteo)))
                .isInstanceOf(ConflitoConcorrenciaExcecao.class);

        primeiro.iniciar(relogio.agora());
        primeiro.concluir(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL, relogio.agora());
        fila.atualizar(primeiro);
        fila.enfileirar(registro(matteo));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from trabalho_processamento", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void trabalhoTravadoVoltaParaAFilaEOQueDependiaDeArquivoFalha() {
        TrabalhoProcessamento semArquivo = registro(matteo);
        fila.enfileirar(semArquivo);
        TrabalhoProcessamento comArquivo =
                registro(cenario.aluno(clara, "Helena Prado", "Violino"));
        comArquivo.definirArquivoTemporario("/tmp/audios/sumiu.webm");
        fila.enfileirar(comArquivo);
        fila.reservarProximo(relogio.agora());
        fila.reservarProximo(relogio.agora());

        assertThat(fila.recuperarTravados(relogio.agora())).isEqualTo(2);

        assertThat(fila.buscarPorId(semArquivo.id()).orElseThrow().status())
                .isEqualTo(StatusTrabalho.PENDENTE);
        TrabalhoProcessamento falhou = fila.buscarPorId(comArquivo.id()).orElseThrow();
        assertThat(falhou.status()).isEqualTo(StatusTrabalho.FALHOU);
        assertThat(falhou.resultado()).isEqualTo(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL);
        assertThat(falhou.arquivoTemporario()).isNull();
    }

    @Test
    void avisoDeDemoraEMarcadoUmaVezESobreviveAGravacaoDoWorker() {
        TrabalhoProcessamento trabalho = registro(matteo);
        fila.enfileirar(trabalho);
        TrabalhoProcessamento doWorker = fila.reservarProximo(relogio.agora()).orElseThrow();

        relogio.avancar(Duration.ofMinutes(3));
        assertThat(fila.listarDemoradosSemAviso(relogio.agora().minus(Duration.ofMinutes(3))))
                .hasSize(1);
        assertThat(fila.marcarAvisoDemora(trabalho.id())).isTrue();
        assertThat(fila.marcarAvisoDemora(trabalho.id())).isFalse();

        doWorker.avancar(EtapaTrabalho.TRANSCREVENDO);
        fila.atualizar(doWorker);

        assertThat(fila.buscarPorId(trabalho.id()).orElseThrow().avisoDemoraEnviado()).isTrue();
        assertThat(fila.listarDemoradosSemAviso(relogio.agora())).isEmpty();
    }

    @Test
    void gravacaoDoWorkerNaoRessuscitaTrabalhoDescartadoNemApagaAMensagemDeEtapa() {
        TrabalhoProcessamento trabalho = registro(matteo);
        fila.enfileirar(trabalho);
        TrabalhoProcessamento doWorker = fila.reservarProximo(relogio.agora()).orElseThrow();
        fila.registrarMensagemEtapa(trabalho.id(), 321);

        doWorker.avancar(EtapaTrabalho.TRANSCREVENDO);
        fila.atualizar(doWorker);
        assertThat(
                        fila.buscarPorId(trabalho.id())
                                .orElseThrow()
                                .parametro(TrabalhoProcessamento.MENSAGEM_ETAPA))
                .contains("321");
        assertThat(doWorker.parametro(TrabalhoProcessamento.MENSAGEM_ETAPA)).contains("321");

        TrabalhoProcessamento descartado = fila.buscarPorId(trabalho.id()).orElseThrow();
        descartado.descartar(relogio.agora());
        fila.atualizar(descartado);
        doWorker.avancar(EtapaTrabalho.ESTRUTURANDO);
        fila.atualizar(doWorker);

        assertThat(fila.buscarPorId(trabalho.id()).orElseThrow().status())
                .isEqualTo(StatusTrabalho.DESCARTADO);
    }
}
