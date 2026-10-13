package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Visão derivada "relatório em aberto" (data-model.md): relatórios de aula não aprovados nem
 * cancelados, registros ainda em transcrição e, quando houver, consultas complementares
 * (semestral).
 */
@Component
public class RelatoriosEmAbertoConsultaAdaptador implements OpenReportsQueryPort {

    /** Fonte adicional de relatórios em aberto (ex.: semestrais), registrada por outra história. */
    public interface FonteEmAberto {
        List<OpenReport> listarDoProfessor(UUID professorId);
    }

    private final RelatorioAulaJpa relatorios;
    private final FilaTrabalhoPostgresAdaptador fila;
    private final AlunoJpa alunos;
    private final JdbcTemplate jdbc;
    private final RelatorioSemestralRepositorioAdaptador semestrais;
    private final List<FonteEmAberto> fontes = new ArrayList<>();

    RelatoriosEmAbertoConsultaAdaptador(
            RelatorioAulaJpa relatorios,
            FilaTrabalhoPostgresAdaptador fila,
            AlunoJpa alunos,
            JdbcTemplate jdbc,
            RelatorioSemestralRepositorioAdaptador semestrais) {
        this.semestrais = semestrais;
        this.relatorios = relatorios;
        this.fila = fila;
        this.alunos = alunos;
        this.jdbc = jdbc;
    }

    public void registrarFonte(FonteEmAberto fonte) {
        fontes.add(fonte);
    }

    @Override
    public List<OpenReport> listByTeacher(UUID professorId) {
        Map<UUID, String> nomes = new HashMap<>();
        List<OpenReport> lista = new ArrayList<>();
        for (RelatorioAulaEntidade r : relatorios.listarEmAbertoDoProfessor(professorId)) {
            lista.add(deRelatorio(r, nome(nomes, r.alunoId)));
        }
        for (ProcessingJob t : fila.registrosEmCursoDoProfessor(professorId)) {
            lista.add(deTrabalho(t, nome(nomes, t.studentId())));
        }
        for (SemesterReport r : semestrais.emAbertoDoProfessor(professorId)) {
            lista.add(
                    new OpenReport(
                            ReportType.SEMESTRAL,
                            r.id(),
                            null,
                            r.studentId(),
                            nome(nomes, r.studentId()),
                            r.status(),
                            r.step() == null ? null : etapaTrabalho(r.step().name()),
                            r.period().key(),
                            r.originChannel(),
                            r.updatedAt()));
        }
        for (ProcessingJob t : fila.consolidacoesEmCursoDoProfessor(professorId)) {
            lista.add(
                    new OpenReport(
                            ReportType.SEMESTRAL,
                            null,
                            t.id(),
                            t.studentId(),
                            nome(nomes, t.studentId()),
                            null,
                            t.step(),
                            t.parameter(ProcessingJob.PERIOD_KEY).orElse(null),
                            t.requestChannel(),
                            t.createdAt()));
        }
        for (FonteEmAberto fonte : fontes) {
            lista.addAll(fonte.listarDoProfessor(professorId));
        }
        lista.sort(Comparator.comparing(OpenReport::updatedAt).reversed());
        return lista;
    }

    @Override
    public Optional<OpenReport> searchByStudent(UUID professorId, UUID alunoId) {
        Optional<RelatorioAulaEntidade> relatorio = relatorios.buscarEmAberto(professorId, alunoId);
        if (relatorio.isPresent()) {
            return Optional.of(deRelatorio(relatorio.get(), nome(new HashMap<>(), alunoId)));
        }
        return fila.searchRegistrationInProgress(professorId, alunoId)
                .map(t -> deTrabalho(t, nome(new HashMap<>(), alunoId)));
    }

    @Override
    public Map<UUID, LocalDate> lastLessonByStudent(UUID professorId) {
        Map<UUID, LocalDate> ultimas = new HashMap<>();
        jdbc.query(
                "select a.aluno_id, max(a.data_aula) as ultima from aula a"
                        + " join relatorio_aula r on r.aula_id = a.id"
                        + " where a.professor_id = ? and r.status <> 'CANCELADO' group by a.aluno_id",
                linha -> {
                    Date data = linha.getDate("ultima");
                    ultimas.put(linha.getObject("aluno_id", UUID.class), data.toLocalDate());
                },
                professorId);
        return ultimas;
    }

    private String nome(Map<UUID, String> cache, UUID alunoId) {
        return cache.computeIfAbsent(
                alunoId, id -> alunos.findById(id).map(AlunoEntidade::getNome).orElse(""));
    }

    private static OpenReport deRelatorio(RelatorioAulaEntidade r, String alunoNome) {
        return new OpenReport(
                ReportType.AULA,
                r.id,
                null,
                r.alunoId,
                alunoNome,
                ReportStatus.valueOf(r.status),
                r.etapaProcessamento == null ? null : etapaTrabalho(r.etapaProcessamento),
                null,
                Channel.valueOf(r.canalOrigem),
                r.atualizadoEm);
    }

    private static JobStep etapaTrabalho(String etapaRelatorio) {
        try {
            return JobStep.valueOf(etapaRelatorio);
        } catch (IllegalArgumentException semEquivalente) {
            return null;
        }
    }

    private static OpenReport deTrabalho(ProcessingJob t, String alunoNome) {
        return new OpenReport(
                ReportType.AULA,
                null,
                t.id(),
                t.studentId(),
                alunoNome,
                ReportStatus.EM_PROCESSAMENTO,
                t.step(),
                null,
                t.requestChannel(),
                t.startedAt() != null ? t.startedAt() : t.createdAt());
    }
}
