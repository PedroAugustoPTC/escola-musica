package br.com.felipeblaft.reports.suporte;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.JobProcessorService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Cria cadastros fictícios com códigos conhecidos para os testes de integração. */
@Component
public class CenarioIT {

    public static final String CODIGO_CLARA = "K7QM2XPA";
    public static final String CODIGO_BRUNO = "BRUN2XPA";

    private final TeacherRepository professores;
    private final StudentRepository alunos;
    private final AssignmentRepository associacoes;
    private final DataProtectionPort protecao;
    private final ClockPort relogio;
    private final JobQueuePort fila;
    private final JobProcessorService processador;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.port.LessonReportRepository relatoriosAula;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.port.ReportVersionRepository versoes;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.reports.service.DocumentGenerationService documentos;

    private int sequenciaCpf;

    /**
     * Tempo que cada trabalho leva, simulado avançando o relógio ajustável durante o processamento;
     * zero (o padrão) mantém o relógio parado. Quem altera restaura o zero ao terminar.
     */
    public volatile Duration tempoPorTrabalho = Duration.ZERO;

    public CenarioIT(
            TeacherRepository professores,
            StudentRepository alunos,
            AssignmentRepository associacoes,
            DataProtectionPort protecao,
            ClockPort relogio,
            JobQueuePort fila,
            JobProcessorService processador) {
        this.professores = professores;
        this.alunos = alunos;
        this.associacoes = associacoes;
        this.protecao = protecao;
        this.relogio = relogio;
        this.fila = fila;
        this.processador = processador;
    }

    /**
     * Processa a fila até esvaziar, como o worker faria (os workers ficam desligados nos testes).
     */
    public int processarFila() {
        int processados = 0;
        Optional<ProcessingJob> proximo;
        while ((proximo = fila.reserveNext(relogio.now())).isPresent()) {
            if (!tempoPorTrabalho.isZero() && relogio instanceof RelogioAjustavel ajustavel) {
                ajustavel.avancar(tempoPorTrabalho);
            }
            processador.process(proximo.get());
            processados++;
        }
        return processados;
    }

    public Teacher professor(String nome, String email, String codigo) {
        Teacher professor = Teacher.create(nome, email, true, relogio.now());
        professor.replaceCode(
                protecao.hashAccessCode(AccessCode.of(codigo)), relogio.now(), Duration.ofDays(7));
        professores.save(professor);
        return professor;
    }

    public Teacher clara() {
        return professor("Clara Souza", "clara@exemplo.test", CODIGO_CLARA);
    }

    public Teacher bruno() {
        return professor("Bruno Lima", "bruno@exemplo.test", CODIGO_BRUNO);
    }

    public Student aluno(Teacher professor, String nome, String instrumento) {
        Cpf cpf = Cpf.of(cpfValido(++sequenciaCpf));
        Student aluno =
                Student.create(
                        nome,
                        LocalDate.of(2012, 5, 10),
                        protecao.encryptCpf(cpf),
                        protecao.hashCpf(cpf),
                        "Responsável Fictício",
                        instrumento,
                        relogio.now());
        alunos.save(aluno);
        associacoes.save(TeacherStudent.create(professor.id(), aluno.id(), relogio.now()));
        return aluno;
    }

    /** Relatório de aula já aprovado, com aula na data dada (base do semestral e do histórico). */
    public br.com.felipeblaft.reports.domain.report.LessonReport aulaAprovada(
            Teacher professor, Student aluno, LocalDate data, String conteudo) {
        java.time.Instant agora = relogio.now();
        br.com.felipeblaft.reports.domain.report.Lesson aula =
                br.com.felipeblaft.reports.domain.report.Lesson.create(
                        professor.id(), aluno.id(), data, agora);
        relatoriosAula.saveLesson(aula);
        br.com.felipeblaft.reports.domain.report.LessonReport relatorio =
                new br.com.felipeblaft.reports.domain.report.LessonReport(
                        br.com.felipeblaft.reports.domain.common.GeneratorId.create(),
                        aula.id(),
                        professor.id(),
                        aluno.id(),
                        "transcrição fictícia",
                        new br.com.felipeblaft.reports.domain.report.LessonReportContent(
                                java.util.List.of(conteudo),
                                "Evoluiu bem.",
                                java.util.List.of(),
                                java.util.List.of(),
                                ""),
                        java.util.List.of(),
                        0,
                        br.com.felipeblaft.reports.domain.report.ReportStatus.APROVADO,
                        null,
                        1,
                        br.com.felipeblaft.reports.domain.common.Channel.WEB,
                        null,
                        agora,
                        agora,
                        agora,
                        1,
                        br.com.felipeblaft.reports.domain.common.Channel.WEB,
                        null,
                        0,
                        0);
        relatoriosAula.save(relatorio);
        br.com.felipeblaft.reports.domain.report.ReportVersion versao =
                br.com.felipeblaft.reports.domain.report.ReportVersion.create(
                        br.com.felipeblaft.reports.domain.common.ReportType.AULA,
                        relatorio.id(),
                        1,
                        relatorio.content(),
                        br.com.felipeblaft.reports.domain.report.VersionOrigin.ESTRUTURACAO_INICIAL,
                        null,
                        br.com.felipeblaft.reports.domain.common.Channel.WEB,
                        "estruturacao-aula.v1",
                        agora);
        versoes.save(versao);
        documentos.generateOfficial(relatorio, versao);
        return relatorio;
    }

    /** CPF fictício válido a partir de um número de sequência. */
    static String cpfValido(int n) {
        String base = String.format("%09d", 100_000_000 + n * 7919);
        int d1 = digito(base, 9);
        int d2 = digito(base + d1, 10);
        return base + d1 + d2;
    }

    private static int digito(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
