package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.TrabalhoProcessamentoEntidade;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * Fila de trabalhos no PostgreSQL (R-06): reserva com {@code FOR UPDATE SKIP LOCKED}, sem duplicar
 * trabalho entre workers; índice único parcial impede dois registros em curso para o mesmo aluno.
 */
@Repository
public class FilaTrabalhoPostgresAdaptador implements JobQueuePort {

    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private final TrabalhoProcessamentoJpa jpa;

    FilaTrabalhoPostgresAdaptador(TrabalhoProcessamentoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void enqueue(ProcessingJob trabalho) {
        try {
            jpa.saveAndFlush(paraEntidade(trabalho));
        } catch (DataIntegrityViolationException conflito) {
            throw new ConcurrencyConflictException("trabalho_processamento");
        }
    }

    @Override
    @Transactional
    public void update(ProcessingJob trabalho) {
        // um trabalho descartado por outro canal não volta a ficar ativo
        Optional<TrabalhoProcessamentoEntidade> atual = jpa.findById(trabalho.id());
        if (atual.isPresent()
                && JobStatus.DESCARTADO.name().equals(atual.get().status)
                && trabalho.status() != JobStatus.DESCARTADO) {
            return;
        }
        // a mensagem de etapa do Telegram é registrada à parte, pelo envio; a gravação do worker a
        // mantém
        if (atual.isPresent() && trabalho.parameter(ProcessingJob.STEP_MESSAGE).isEmpty()) {
            JsonPersistencia.ler(atual.get().parametros, MAPA).entrySet().stream()
                    .filter(p -> ProcessingJob.STEP_MESSAGE.equals(p.getKey()))
                    .findFirst()
                    .ifPresent(p -> trabalho.defineParameter(p.getKey(), p.getValue()));
        }
        TrabalhoProcessamentoEntidade entidade = paraEntidade(trabalho);
        // o aviso de demora é marcado à parte; uma gravação do worker não o desfaz
        entidade.avisoDemoraEnviado =
                entidade.avisoDemoraEnviado || atual.map(a -> a.avisoDemoraEnviado).orElse(false);
        jpa.saveAndFlush(entidade);
    }

    /** Grava o id da mensagem de etapa do Telegram sem disputar com a gravação do worker. */
    @Override
    @Transactional
    public void registerStepMessage(UUID trabalhoId, long mensagemId) {
        jpa.definirParametro(trabalhoId, ProcessingJob.STEP_MESSAGE, Long.toString(mensagemId));
    }

    @Override
    @Transactional
    public boolean markDelayNotice(UUID trabalhoId) {
        return jpa.marcarAvisoDemora(trabalhoId) > 0;
    }

    @Override
    public Optional<ProcessingJob> searchById(UUID id) {
        return jpa.findById(id).map(FilaTrabalhoPostgresAdaptador::paraDominio);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ProcessingJob> reserveNext(Instant agora) {
        Optional<UUID> id = jpa.proximoPendente();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        ProcessingJob trabalho = paraDominio(jpa.findById(id.get()).orElseThrow());
        trabalho.start(agora);
        jpa.saveAndFlush(paraEntidade(trabalho));
        return Optional.of(trabalho);
    }

    @Override
    public Optional<ProcessingJob> searchInProgressByReport(ReportType tipo, UUID relatorioId) {
        return jpa.emCursoDoRelatorio(tipo.name(), relatorioId).stream()
                .findFirst()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio);
    }

    @Override
    public Optional<ProcessingJob> searchRegistrationInProgress(UUID professorId, UUID alunoId) {
        return jpa.registroEmCurso(professorId, alunoId).stream()
                .findFirst()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio);
    }

    @Override
    public List<ProcessingJob> listDelayedWithoutNotice(Instant criadosAntesDe) {
        return jpa.demoradosSemAviso(criadosAntesDe).stream()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio)
                .toList();
    }

    @Override
    public Optional<ProcessingJob> searchConsolidationInProgress(
            UUID professorId, UUID alunoId, String periodoChave) {
        return jpa.consolidacaoEmCurso(professorId, alunoId, periodoChave).stream()
                .findFirst()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio);
    }

    /**
     * Consolidações semestrais em curso do professor (semestrais em aberto ainda sem relatório).
     */
    List<ProcessingJob> consolidacoesEmCursoDoProfessor(UUID professorId) {
        return jpa.consolidacoesEmCursoDoProfessor(professorId).stream()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio)
                .toList();
    }

    /** Registros de aula em transcrição do professor (relatórios em aberto ainda sem relatório). */
    List<ProcessingJob> registrosEmCursoDoProfessor(UUID professorId) {
        return jpa.registrosEmCursoDoProfessor(professorId).stream()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio)
                .toList();
    }

    /**
     * Trabalhos que estavam em execução quando o backend caiu: voltam para a fila, exceto os que
     * dependiam de um arquivo do diretório temporário (apagado na reinicialização), que falham.
     */
    @Transactional
    public int recuperarTravados(Instant agora) {
        int recuperados = 0;
        for (TrabalhoProcessamentoEntidade entidade :
                jpa.findByStatus(JobStatus.EM_EXECUCAO.name())) {
            ProcessingJob trabalho = paraDominio(entidade);
            if (trabalho.temporaryFile() != null) {
                trabalho.complete(JobResult.AUDIO_NAO_PROCESSAVEL, agora);
                trabalho.defineTemporaryFile(null);
            } else {
                trabalho.returnToQueue();
            }
            jpa.save(paraEntidade(trabalho));
            recuperados++;
        }
        return recuperados;
    }

    static TrabalhoProcessamentoEntidade paraEntidade(ProcessingJob t) {
        TrabalhoProcessamentoEntidade e = new TrabalhoProcessamentoEntidade();
        e.id = t.id();
        e.tipo = t.type().name();
        e.professorId = t.teacherId();
        e.alunoId = t.studentId();
        e.relatorioTipo = t.reportType() == null ? null : t.reportType().name();
        e.relatorioId = t.reportId();
        e.canalSolicitacao = t.requestChannel().name();
        e.meio = t.medium().name();
        e.parametros = JsonPersistencia.escrever(t.parameters());
        e.arquivoTemporario = t.temporaryFile();
        e.status = t.status().name();
        e.etapa = t.step().name();
        e.resultadoCodigo = t.result() == null ? null : t.result().name();
        e.relatorioResultanteId = t.resultingReportId();
        e.tentativas = t.attempts();
        e.avisoDemoraEnviado = t.delayNoticeSent();
        e.criadoEm = t.createdAt();
        e.iniciadoEm = t.startedAt();
        e.concluidoEm = t.completedAt();
        return e;
    }

    static ProcessingJob paraDominio(TrabalhoProcessamentoEntidade e) {
        return new ProcessingJob(
                e.id,
                JobType.valueOf(e.tipo),
                e.professorId,
                e.alunoId,
                e.relatorioTipo == null ? null : ReportType.valueOf(e.relatorioTipo),
                e.relatorioId,
                Channel.valueOf(e.canalSolicitacao),
                Medium.valueOf(e.meio),
                JsonPersistencia.ler(e.parametros, MAPA),
                e.arquivoTemporario,
                JobStatus.valueOf(e.status),
                JobStep.valueOf(e.etapa),
                e.resultadoCodigo == null ? null : JobResult.valueOf(e.resultadoCodigo),
                e.relatorioResultanteId,
                e.tentativas,
                e.avisoDemoraEnviado,
                e.criadoEm,
                e.iniciadoEm,
                e.concluidoEm);
    }
}
