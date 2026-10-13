package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Relatórios semestrais (Informações Gerais e seções 2 a 10 em jsonb). */
@Repository
class RelatorioSemestralRepositorioAdaptador implements SemesterReportRepository {

    private final RelatorioSemestralJpa jpa;

    RelatorioSemestralRepositorioAdaptador(RelatorioSemestralJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(SemesterReport relatorio) {
        try {
            RelatorioSemestralEntidade salvo = jpa.saveAndFlush(paraEntidade(relatorio));
            relatorio.registerPersistedVersion(salvo.bloqueioOtimista);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflito) {
            throw new ConcurrencyConflictException("relatorio_semestral");
        }
    }

    @Override
    public Optional<SemesterReport> searchById(UUID id) {
        return jpa.findById(id).map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public Optional<SemesterReport> searchForUpdate(UUID id) {
        return jpa.buscarParaAtualizar(id).map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public Optional<SemesterReport> searchApproved(
            UUID professorId, UUID alunoId, String periodoChave) {
        return jpa.doTrio(professorId, alunoId, periodoChave, ReportStatus.APROVADO.name())
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public Optional<SemesterReport> searchOpen(
            UUID professorId, UUID alunoId, String periodoChave) {
        return jpa.doTrio(
                        professorId,
                        alunoId,
                        periodoChave,
                        ReportStatus.AGUARDANDO_CONFERENCIA.name())
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public List<SemesterReport> listApproved(UUID professorId, UUID alunoId) {
        return jpa.aprovados(professorId, alunoId).stream()
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio)
                .toList();
    }

    List<SemesterReport> emAbertoDoProfessor(UUID professorId) {
        return jpa.emAbertoDoProfessor(professorId).stream()
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio)
                .toList();
    }

    static RelatorioSemestralEntidade paraEntidade(SemesterReport r) {
        RelatorioSemestralEntidade e = new RelatorioSemestralEntidade();
        e.id = r.id();
        e.professorId = r.teacherId();
        e.alunoId = r.studentId();
        e.periodoChave = r.period().key();
        e.periodoInicio = r.period().start();
        e.periodoFim = r.period().end();
        e.quantidadeAulasConsideradas = r.consideredLessonCount();
        e.quantidadeRelatoriosEmAberto = r.openReportCount();
        e.informacoesGerais = JsonPersistencia.escrever(r.generalInformation());
        e.conteudo = JsonPersistencia.escrever(r.content());
        e.status = r.status().name();
        e.etapaProcessamento = r.step() == null ? null : r.step().name();
        e.versaoVigente = r.currentVersion();
        e.canalOrigem = r.originChannel().name();
        e.criadoEm = r.createdAt();
        e.atualizadoEm = r.updatedAt();
        e.aprovadoEm = r.approvedAt();
        e.versaoAprovada = r.approvedVersion();
        e.canalAprovacao = r.approvalChannel() == null ? null : r.approvalChannel().name();
        e.canceladoEm = r.cancelledAt();
        e.tempoProcessamentoMs = r.processingTimeMs();
        e.bloqueioOtimista = r.version();
        return e;
    }

    static SemesterReport paraDominio(RelatorioSemestralEntidade e) {
        return new SemesterReport(
                e.id,
                e.professorId,
                e.alunoId,
                SemesterPeriod.of(e.periodoChave),
                e.quantidadeAulasConsideradas,
                e.quantidadeRelatoriosEmAberto,
                JsonPersistencia.ler(e.informacoesGerais, GeneralInformation.class),
                JsonPersistencia.ler(e.conteudo, SemesterReportContent.class),
                ReportStatus.valueOf(e.status),
                e.etapaProcessamento == null ? null : ReportStep.valueOf(e.etapaProcessamento),
                e.versaoVigente,
                Channel.valueOf(e.canalOrigem),
                e.criadoEm,
                e.atualizadoEm,
                e.aprovadoEm,
                e.versaoAprovada,
                e.canalAprovacao == null ? null : Channel.valueOf(e.canalAprovacao),
                e.canceladoEm,
                e.tempoProcessamentoMs,
                e.bloqueioOtimista);
    }
}
