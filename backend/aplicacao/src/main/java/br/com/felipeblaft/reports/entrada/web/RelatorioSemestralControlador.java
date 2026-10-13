package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.AlteracaoTextoRequisicao;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.AprovacaoDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.AprovacaoRequisicao;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.InteracaoDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.TrabalhoRefDto;
import br.com.felipeblaft.reports.entrada.web.seguranca.ProfessorAutenticado;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.usecase.semester.ApproveSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.CancelSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase.Preview;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase.SemesterReportView;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase.Semester;
import br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Relatório semestral na Web (FR-041 a FR-046): os mesmos casos de uso do Telegram. */
@RestController
@RequestMapping("/api/v1")
public class RelatorioSemestralControlador {

    public record SemestreDto(
            String chave,
            String rotulo,
            LocalDate inicio,
            LocalDate fim,
            boolean emAndamento,
            boolean preSelecionado) {}

    public record PreviaSemestralDto(
            String periodoChave,
            int aulasAprovadas,
            int relatoriosAulaEmAberto,
            UUID semestralAprovadoId,
            RelatorioEmAbertoDto semestralEmAberto) {}

    public record GerarSemestralRequisicao(
            @NotNull @Pattern(regexp = "^[0-9]{4}-[12]$") String periodoChave,
            Boolean confirmarMesmoComEmAberto) {}

    public record InformacoesGeraisDto(
            String nome,
            int idade,
            String instrumento,
            String professor,
            String periodoRotulo,
            LocalDate periodoInicio,
            LocalDate periodoFim,
            int quantidadeAulasConsideradas) {}

    public record RelatorioSemestralDto(
            UUID relatorioId,
            UUID alunoId,
            String periodoChave,
            String status,
            String etapa,
            UUID trabalhoEmCursoId,
            int versaoVigente,
            String canalOrigem,
            InformacoesGeraisDto informacoesGerais,
            SemesterReportContent conteudo,
            int quantidadeRelatoriosEmAberto,
            InteracaoDto ultimaInteracao,
            boolean pdfDisponivel,
            Instant aprovadoEm,
            Instant atualizadoEm) {}

    private final RecepcaoAudioWeb recepcao;
    private final ListAvailableSemestersUseCase listar;
    private final GetSemesterPreviewUseCase previa;
    private final GenerateSemesterReportUseCase gerar;
    private final GetSemesterReportUseCase obter;
    private final RequestSemesterReportChangeUseCase alterar;
    private final ApproveSemesterReportUseCase aprovar;
    private final CancelSemesterReportUseCase cancelar;
    private final SecoesRelatorioSemestral secoes;

    @SuppressWarnings("java:S107")
    public RelatorioSemestralControlador(
            RecepcaoAudioWeb recepcao,
            ListAvailableSemestersUseCase listar,
            GetSemesterPreviewUseCase previa,
            GenerateSemesterReportUseCase gerar,
            GetSemesterReportUseCase obter,
            RequestSemesterReportChangeUseCase alterar,
            ApproveSemesterReportUseCase aprovar,
            CancelSemesterReportUseCase cancelar,
            SecoesRelatorioSemestral secoes) {
        this.recepcao = recepcao;
        this.listar = listar;
        this.previa = previa;
        this.gerar = gerar;
        this.obter = obter;
        this.alterar = alterar;
        this.aprovar = aprovar;
        this.cancelar = cancelar;
        this.secoes = secoes;
    }

    @GetMapping("/alunos/{alunoId}/semestres")
    public List<SemestreDto> semestres(
            @AuthenticationPrincipal ProfessorAutenticado professor, @PathVariable UUID alunoId) {
        return listar.run(professor.id(), alunoId).stream().map(this::semestre).toList();
    }

    private SemestreDto semestre(Semester s) {
        return new SemestreDto(
                s.period().key(),
                secoes.rotuloPeriodo(s.period().key()),
                s.period().start(),
                s.period().end(),
                s.inProgress(),
                s.preSelected());
    }

    @GetMapping("/alunos/{alunoId}/semestres/{periodoChave}/previa")
    public PreviaSemestralDto previa(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID alunoId,
            @PathVariable String periodoChave) {
        Preview p = previa.run(professor.id(), alunoId, periodoChave);
        return new PreviaSemestralDto(
                p.period().key(),
                p.approvedLessons(),
                p.openLessonReports(),
                p.approvedSemesterId().orElse(null),
                p.openSemester().map(InicioMapeador::emAberto).orElse(null));
    }

    @PostMapping(
            path = "/alunos/{alunoId}/relatorios-semestrais",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto gerar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID alunoId,
            @Valid @RequestBody GerarSemestralRequisicao requisicao) {
        ProcessingJob trabalho =
                gerar.run(
                        professor.id(),
                        alunoId,
                        requisicao.periodoChave(),
                        Boolean.TRUE.equals(requisicao.confirmarMesmoComEmAberto()),
                        Channel.WEB);
        return new TrabalhoRefDto(trabalho.id());
    }

    @GetMapping("/relatorios-semestrais/{relatorioId}")
    public RelatorioSemestralDto obter(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return relatorio(obter.run(professor.id(), relatorioId));
    }

    @PostMapping(
            path = "/relatorios-semestrais/{relatorioId}/alteracoes",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto alterarPorTexto(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @Valid @RequestBody AlteracaoTextoRequisicao requisicao) {
        return new TrabalhoRefDto(
                alterar.changeByText(
                                professor.id(),
                                relatorioId,
                                requisicao.texto(),
                                requisicao.versaoBase(),
                                Channel.WEB)
                        .id());
    }

    @PostMapping(
            path = "/relatorios-semestrais/{relatorioId}/alteracoes",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto alterarPorAudio(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam("versaoBase") String versaoBase) {
        int base;
        try {
            base = Integer.parseInt(versaoBase.strip());
        } catch (NumberFormatException invalido) {
            throw new DomainException(ErrorCode.DADOS_INVALIDOS);
        }
        TemporaryFile arquivo = recepcao.receber(audio);
        return new TrabalhoRefDto(
                alterar.changeByAudio(professor.id(), relatorioId, arquivo, base, Channel.WEB)
                        .id());
    }

    @PostMapping(
            path = "/relatorios-semestrais/{relatorioId}/aprovacao",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public AprovacaoDto aprovar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @Valid @RequestBody AprovacaoRequisicao requisicao) {
        return RelatorioAulaMapeador.aprovacao(
                aprovar.run(
                        professor.id(), relatorioId, requisicao.versaoConfirmada(), Channel.WEB));
    }

    @PostMapping("/relatorios-semestrais/{relatorioId}/cancelamento")
    public ResponseEntity<Void> cancelar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        cancelar.cancel(professor.id(), relatorioId);
        return ResponseEntity.noContent().build();
    }

    private RelatorioSemestralDto relatorio(SemesterReportView visao) {
        SemesterReport r = visao.report();
        GeneralInformation info = r.generalInformation();
        String etapa =
                visao.jobInProgress()
                        .map(t -> t.step().name())
                        .orElse(r.step() == null ? null : r.step().name());
        return new RelatorioSemestralDto(
                r.id(),
                r.studentId(),
                r.period().key(),
                r.status().name(),
                etapa,
                visao.jobInProgress().map(ProcessingJob::id).orElse(null),
                r.currentVersion(),
                r.originChannel().name(),
                new InformacoesGeraisDto(
                        info.nome(),
                        info.idade(),
                        info.instrumento(),
                        info.professor(),
                        secoes.rotuloPeriodo(info.periodoChave()),
                        info.periodoInicio(),
                        info.periodoFim(),
                        info.quantidadeAulasConsideradas()),
                r.content(),
                r.openReportCount(),
                visao.lastInteraction().map(RelatorioAulaMapeador::interacao).orElse(null),
                visao.pdfAvailable(),
                r.approvedAt(),
                r.updatedAt());
    }
}
