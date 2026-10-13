package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class RelatorioAulaRepositorioAdaptador implements LessonReportRepository {

    private final AulaJpa aulas;
    private final RelatorioAulaJpa relatorios;

    RelatorioAulaRepositorioAdaptador(AulaJpa aulas, RelatorioAulaJpa relatorios) {
        this.aulas = aulas;
        this.relatorios = relatorios;
    }

    @Override
    public void saveLesson(Lesson aula) {
        aulas.save(RelatorioAulaMapeador.paraEntidade(aula));
    }

    @Override
    public Optional<Lesson> searchLesson(UUID aulaId) {
        return aulas.findById(aulaId).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public void save(LessonReport relatorio) {
        try {
            RelatorioAulaEntidade salvo =
                    relatorios.saveAndFlush(RelatorioAulaMapeador.paraEntidade(relatorio));
            relatorio.registerPersistedVersion(salvo.bloqueioOtimista);
        } catch (ObjectOptimisticLockingFailureException
                | DataIntegrityViolationException conflito) {
            throw new ConcurrencyConflictException("relatorio_aula");
        }
    }

    @Override
    public Optional<LessonReport> searchById(UUID id) {
        return relatorios.findById(id).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public Optional<LessonReport> searchForUpdate(UUID id) {
        return relatorios.buscarParaAtualizar(id).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public Optional<LessonReport> searchOpenByStudent(UUID professorId, UUID alunoId) {
        return relatorios
                .buscarEmAberto(professorId, alunoId)
                .map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public List<ApprovedLesson> listApprovedInPeriod(
            UUID professorId, UUID alunoId, LocalDate inicio, LocalDate fim) {
        return relatorios.aprovadosNoPeriodo(professorId, alunoId, inicio, fim).stream()
                .map(
                        linha -> {
                            LessonReport relatorio =
                                    RelatorioAulaMapeador.paraDominio(
                                            (RelatorioAulaEntidade) linha[0]);
                            return new ApprovedLesson(
                                    relatorio.id(),
                                    (LocalDate) linha[1],
                                    relatorio.content(),
                                    relatorio.approvedAt());
                        })
                .toList();
    }

    @Override
    public List<ApprovedLesson> listApproved(UUID professorId, UUID alunoId) {
        return relatorios.aprovados(professorId, alunoId).stream().map(this::aprovada).toList();
    }

    private ApprovedLesson aprovada(Object[] linha) {
        LessonReport relatorio =
                RelatorioAulaMapeador.paraDominio((RelatorioAulaEntidade) linha[0]);
        return new ApprovedLesson(
                relatorio.id(), (LocalDate) linha[1], relatorio.content(), relatorio.approvedAt());
    }

    @Override
    public int countOpenInPeriod(UUID professorId, UUID alunoId, LocalDate inicio, LocalDate fim) {
        return (int) relatorios.contarEmAbertoNoPeriodo(professorId, alunoId, inicio, fim);
    }

    @Override
    public Optional<LocalDate> lessonFirstDate(UUID professorId, UUID alunoId) {
        return relatorios.primeiraAula(professorId, alunoId);
    }
}
