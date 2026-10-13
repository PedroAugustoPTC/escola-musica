package br.com.felipeblaft.reports.entrada.carga;

import br.com.felipeblaft.reports.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.AiSemesterReportOutput;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedFile;
import br.com.felipeblaft.reports.usecase.seed.SeedResult;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comando {@code massa --arquivo <cadastros.json>}: massa de DEMONSTRAÇÃO para o ambiente local
 * ({@code local.sh subir --massa=true}). Faz a carga dos cadastros fictícios e cria, pelos mesmos
 * objetos de domínio e pelo mesmo gerador de PDF do sistema, aulas aprovadas no semestre anterior e
 * no atual (PDFs de conferência e oficiais), um semestral aprovado, um semestral em conferência e
 * relatórios de aula em aberto. Só roda com o banco vazio, para nunca misturar dados fictícios com
 * dados reais.
 */
@Component
@Profile("carga")
public class MassaDemonstracaoComando {

    public static final String NOME = "massa";

    private static final String TRANSCRICAO = "Transcrição de demonstração (massa fictícia).";

    /** Plano por aluno: aulas aprovadas no semestre anterior e no atual, e o dia da semana. */
    private record Plano(int anterior, int atual, int deslocamento) {}

    private static final Map<String, Plano> PLANOS =
            Map.of(
                    "Matteo Mafra", new Plano(12, 10, 0),
                    "Olavo Torrente", new Plano(8, 8, 1),
                    "Miguel de Castro Torrente", new Plano(0, 6, 2),
                    "Beatriz Arruda", new Plano(0, 4, 3),
                    "Helena Prado", new Plano(4, 3, 1),
                    "Davi Coutinho", new Plano(0, 2, 4));

    private final LoadInitialRegistryUseCase carga;
    private final TeacherRepository professores;
    private final StudentRepository alunos;
    private final LessonReportRepository relatoriosAula;
    private final ReportVersionRepository versoes;
    private final SemesterReportRepository semestrais;
    private final DocumentGenerationService documentos;
    private final TransactionPort transacao;
    private final ClockPort relogio;
    private final FormatacaoMensagens formatacao;
    private final JdbcTemplate jdbc;
    private final JsonMapper json =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @SuppressWarnings("java:S107")
    public MassaDemonstracaoComando(
            LoadInitialRegistryUseCase carga,
            TeacherRepository professores,
            StudentRepository alunos,
            LessonReportRepository relatoriosAula,
            ReportVersionRepository versoes,
            SemesterReportRepository semestrais,
            DocumentGenerationService documentos,
            TransactionPort transacao,
            ClockPort relogio,
            FormatacaoMensagens formatacao,
            JdbcTemplate jdbc) {
        this.carga = carga;
        this.professores = professores;
        this.alunos = alunos;
        this.relatoriosAula = relatoriosAula;
        this.versoes = versoes;
        this.semestrais = semestrais;
        this.documentos = documentos;
        this.transacao = transacao;
        this.relogio = relogio;
        this.formatacao = formatacao;
        this.jdbc = jdbc;
    }

    /** Devolve o código de saída do processo (0 = sucesso). */
    public int executar(List<String> argumentos, PrintStream saida) {
        String arquivo = ArgumentosComando.valor(argumentos, "--arquivo");
        if (arquivo == null) {
            saida.println("Uso: massa --arquivo <cadastros.json>");
            return 2;
        }
        Integer existentes = jdbc.queryForObject("select count(*) from professor", Integer.class);
        if (existentes != null && existentes > 0) {
            saida.println(
                    "A massa de demonstração só é criada em banco vazio (há professores cadastrados)."
                            + " Use: local.sh subir --massa=true, que zera os dados locais antes.");
            return 1;
        }
        SeedFile cadastros;
        try {
            cadastros = json.readValue(Files.readString(Path.of(arquivo)), SeedFile.class);
        } catch (Exception erro) {
            saida.println(
                    "Não foi possível ler o arquivo de cadastros (JSON inválido ou ausente).");
            return 1;
        }
        SeedResult resultado = carga.run(cadastros, false);
        if (!resultado.success()) {
            saida.println("Carga dos cadastros recusada: " + resultado.errors());
            return 1;
        }
        Resumo resumo = gerar(cadastros);
        saida.println("Massa de demonstração criada (dados fictícios).");
        saida.printf(
                "Professores: %d · Alunos: %d%n",
                resultado.teachers().created(), resultado.students().created());
        saida.printf(
                "Relatórios de aula: %d aprovados, %d em aberto · Semestrais: %d aprovado(s), %d em"
                        + " conferência · PDFs gerados: %d%n",
                resumo.aulasAprovadas,
                resumo.aulasEmAberto,
                resumo.semestraisAprovados,
                resumo.semestraisEmConferencia,
                resumo.pdfs);
        saida.println("Códigos de acesso (repasse fora do sistema):");
        for (SeedResult.IssuedCode codigo : resultado.issuedCodes()) {
            saida.println(ArgumentosComando.linhaCodigo(codigo, formatacao));
        }
        return 0;
    }

    /** Contadores do que foi criado. */
    static final class Resumo {
        int aulasAprovadas;
        int aulasEmAberto;
        int semestraisAprovados;
        int semestraisEmConferencia;
        int pdfs;
    }

    Resumo gerar(SeedFile cadastros) {
        Resumo resumo = new Resumo();
        LocalDate hoje = relogio.todayInSchoolTimezone();
        SemesterPeriod atual = SemesterPeriod.containing(hoje);
        SemesterPeriod anterior = atual.previous();
        for (SeedFile.SeedTeacher dados : cadastros.professores()) {
            Teacher professor =
                    professores.searchByEmail(dados.email().strip().toLowerCase()).orElseThrow();
            List<Student> doProfessor =
                    alunos.listByTeacher(professor.id()).stream()
                            .sorted(Comparator.comparing(Student::name))
                            .toList();
            for (Student aluno : doProfessor) {
                Plano plano = PLANOS.get(aluno.name());
                if (plano == null) {
                    continue; // sem histórico: serve para testar as telas vazias
                }
                List<LocalDate> datasAnteriores =
                        semanas(
                                anterior.start(),
                                anterior.end(),
                                plano.deslocamento(),
                                plano.anterior());
                List<LocalDate> datasAtuais =
                        semanas(
                                atual.start(),
                                hoje.minusDays(7),
                                plano.deslocamento(),
                                plano.atual());
                int indice = 0;
                for (LocalDate data : concatenar(datasAnteriores, datasAtuais)) {
                    aulaAprovada(professor, aluno, data, indice++);
                    resumo.aulasAprovadas++;
                    resumo.pdfs += 2;
                }
                switch (aluno.name()) {
                    case "Matteo Mafra" -> {
                        semestral(professor, aluno, anterior, datasAnteriores.size(), true);
                        resumo.semestraisAprovados++;
                        resumo.pdfs += 2;
                    }
                    case "Olavo Torrente" -> {
                        semestral(professor, aluno, anterior, datasAnteriores.size(), false);
                        resumo.semestraisEmConferencia++;
                        resumo.pdfs++;
                        aulaEmAberto(professor, aluno, hoje.minusDays(1), indice, false);
                        resumo.aulasEmAberto++;
                        resumo.pdfs++;
                    }
                    case "Miguel de Castro Torrente" -> {
                        aulaEmAberto(professor, aluno, hoje, indice, true);
                        resumo.aulasEmAberto++;
                    }
                    default -> {
                        // só aulas aprovadas
                    }
                }
            }
        }
        return resumo;
    }

    private static List<LocalDate> concatenar(List<LocalDate> a, List<LocalDate> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    /** Uma aula por semana a partir da 2ª semana do período, até o limite e a quantidade. */
    private static List<LocalDate> semanas(
            LocalDate inicio, LocalDate limite, int deslocamento, int quantidade) {
        List<LocalDate> datas = new java.util.ArrayList<>();
        LocalDate data = inicio.plusDays(7L + deslocamento);
        while (datas.size() < quantidade && !data.isAfter(limite)) {
            datas.add(data);
            data = data.plusWeeks(1);
        }
        return datas;
    }

    private Instant as(LocalDate data, int hora, int minuto) {
        return data.atTime(hora, minuto).atZone(relogio.schoolTimezone()).toInstant();
    }

    private void aulaAprovada(Teacher professor, Student aluno, LocalDate data, int indice) {
        transacao.run(
                () -> {
                    Channel canal = indice % 3 == 2 ? Channel.TELEGRAM : Channel.WEB;
                    Instant registrada = as(data, 18, 10);
                    Lesson aula = Lesson.create(professor.id(), aluno.id(), data, registrada);
                    relatoriosAula.saveLesson(aula);
                    LessonReport relatorio =
                            LessonReport.create(aula, TRANSCRICAO, canal, registrada);
                    relatorio.registerStructuring(
                            ConteudosDemonstracao.aula(aluno.instrument(), indice),
                            List.of(),
                            as(data, 18, 12));
                    relatorio.approve(1, canal, as(data, 18, 40));
                    relatoriosAula.save(relatorio);
                    ReportVersion versao = versaoAula(relatorio, canal, registrada);
                    documentos.generateReview(relatorio, versao);
                    documentos.generateOfficial(relatorio, versao);
                });
    }

    private void aulaEmAberto(
            Teacher professor, Student aluno, LocalDate data, int indice, boolean comPerguntas) {
        transacao.run(
                () -> {
                    Instant registrada = as(data, 17, 5);
                    Lesson aula = Lesson.create(professor.id(), aluno.id(), data, registrada);
                    relatoriosAula.saveLesson(aula);
                    LessonReport relatorio =
                            LessonReport.create(aula, TRANSCRICAO, Channel.TELEGRAM, registrada);
                    boolean criarVersao =
                            relatorio.registerStructuring(
                                    ConteudosDemonstracao.aula(aluno.instrument(), indice),
                                    comPerguntas ? ConteudosDemonstracao.PERGUNTAS : List.of(),
                                    as(data, 17, 7));
                    relatoriosAula.save(relatorio);
                    if (criarVersao) {
                        documentos.generateReview(
                                relatorio, versaoAula(relatorio, Channel.TELEGRAM, registrada));
                    }
                });
    }

    private ReportVersion versaoAula(LessonReport relatorio, Channel canal, Instant quando) {
        ReportVersion versao =
                ReportVersion.create(
                        ReportType.AULA,
                        relatorio.id(),
                        1,
                        relatorio.content(),
                        VersionOrigin.ESTRUTURACAO_INICIAL,
                        null,
                        canal,
                        "estruturacao-aula.v1",
                        quando);
        versoes.save(versao);
        return versao;
    }

    private void semestral(
            Teacher professor,
            Student aluno,
            SemesterPeriod periodo,
            int quantidadeAulas,
            boolean aprovar) {
        if (quantidadeAulas == 0) {
            return;
        }
        transacao.run(
                () -> {
                    LocalDate geradoEm = periodo.end().plusDays(3);
                    Instant consolidado = as(geradoEm, 10, 0);
                    GeneralInformation informacoes =
                            GeneralInformation.calculate(
                                    aluno.name(),
                                    aluno.birthDate(),
                                    aluno.instrument(),
                                    professor.name(),
                                    periodo,
                                    quantidadeAulas,
                                    geradoEm);
                    SemesterReport relatorio =
                            SemesterReport.create(
                                    professor.id(),
                                    aluno.id(),
                                    periodo,
                                    quantidadeAulas,
                                    0,
                                    informacoes,
                                    conteudoSemestral().content(),
                                    Channel.WEB,
                                    consolidado);
                    if (aprovar) {
                        relatorio.approve(1, Channel.WEB, as(geradoEm, 10, 30));
                    }
                    semestrais.save(relatorio);
                    ReportVersion versao =
                            ReportVersion.create(
                                    ReportType.SEMESTRAL,
                                    relatorio.id(),
                                    1,
                                    relatorio.content(),
                                    VersionOrigin.CONSOLIDACAO,
                                    null,
                                    Channel.WEB,
                                    "consolidacao-semestral.v1",
                                    consolidado);
                    versoes.save(versao);
                    documentos.generateReview(relatorio, versao);
                    if (aprovar) {
                        documentos.generateOfficial(relatorio, versao);
                    }
                });
    }

    /** O mesmo conteúdo semestral da IA simulada (ia/simulado/consolidacao-semestral.json). */
    private AiSemesterReportOutput conteudoSemestral() {
        try (InputStream entrada =
                MassaDemonstracaoComando.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/consolidacao-semestral.json")) {
            return json.readValue(entrada, AiSemesterReportOutput.class);
        } catch (java.io.IOException erro) {
            throw new java.io.UncheckedIOException(erro);
        }
    }
}
