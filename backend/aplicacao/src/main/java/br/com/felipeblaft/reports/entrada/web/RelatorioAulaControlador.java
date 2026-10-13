package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.AlteracaoTextoRequisicao;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.AprovacaoDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.AprovacaoRequisicao;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.RelatorioAulaDto;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.RespostaTextoRequisicao;
import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.TrabalhoRefDto;
import br.com.felipeblaft.reports.entrada.web.seguranca.ProfessorAutenticado;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import jakarta.validation.Valid;
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

/**
 * Relatório de aula na Web (FR-014 a FR-031): os mesmos casos de uso do Telegram, com o canal
 * {@code WEB}. Os processamentos são assíncronos (202 com o trabalho a acompanhar).
 */
@RestController
@RequestMapping("/api/v1")
public class RelatorioAulaControlador {

    private final RecepcaoAudioWeb recepcao;
    private final RegisterLessonByAudioUseCase registrar;
    private final GetLessonReportUseCase obter;
    private final AnswerQuestionsUseCase responder;
    private final RequestLessonReportChangeUseCase alterar;
    private final RetryLessonReportUseCase tentar;
    private final ApproveLessonReportUseCase aprovar;
    private final CancelLessonReportUseCase cancelar;
    private final GetOpenReportUseCase obterEmAberto;

    @SuppressWarnings("java:S107")
    public RelatorioAulaControlador(
            RecepcaoAudioWeb recepcao,
            RegisterLessonByAudioUseCase registrar,
            GetLessonReportUseCase obter,
            AnswerQuestionsUseCase responder,
            RequestLessonReportChangeUseCase alterar,
            RetryLessonReportUseCase tentar,
            ApproveLessonReportUseCase aprovar,
            CancelLessonReportUseCase cancelar,
            GetOpenReportUseCase obterEmAberto) {
        this.recepcao = recepcao;
        this.registrar = registrar;
        this.obter = obter;
        this.responder = responder;
        this.alterar = alterar;
        this.tentar = tentar;
        this.aprovar = aprovar;
        this.cancelar = cancelar;
        this.obterEmAberto = obterEmAberto;
    }

    @PostMapping(
            path = "/alunos/{alunoId}/relatorios-aula",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto registrar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID alunoId,
            @RequestPart("audio") MultipartFile audio) {
        TemporaryFile arquivo = recepcao.receber(audio);
        return RelatorioAulaMapeador.referencia(
                registrar.run(professor.id(), alunoId, arquivo, Channel.WEB));
    }

    /** Relatório em aberto do aluno (FR-034): 200 com o relatório ou 204 se não houver. */
    @GetMapping("/alunos/{alunoId}/relatorio-em-aberto")
    public ResponseEntity<RelatorioEmAbertoDto> relatorioEmAberto(
            @AuthenticationPrincipal ProfessorAutenticado professor, @PathVariable UUID alunoId) {
        return obterEmAberto
                .run(professor.id(), alunoId)
                .map(InicioMapeador::emAberto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/relatorios-aula/{relatorioId}")
    public RelatorioAulaDto obter(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return RelatorioAulaMapeador.relatorio(obter.run(professor.id(), relatorioId));
    }

    @PostMapping(
            path = "/relatorios-aula/{relatorioId}/respostas",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto responderTexto(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @Valid @RequestBody RespostaTextoRequisicao requisicao) {
        return RelatorioAulaMapeador.referencia(
                responder.answerText(professor.id(), relatorioId, requisicao.texto(), Channel.WEB));
    }

    @PostMapping(
            path = "/relatorios-aula/{relatorioId}/respostas",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto responderAudio(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @RequestPart("audio") MultipartFile audio) {
        TemporaryFile arquivo = recepcao.receber(audio);
        return RelatorioAulaMapeador.referencia(
                responder.answerAudio(professor.id(), relatorioId, arquivo, Channel.WEB));
    }

    @PostMapping(
            path = "/relatorios-aula/{relatorioId}/alteracoes",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto alterarPorTexto(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @Valid @RequestBody AlteracaoTextoRequisicao requisicao) {
        return RelatorioAulaMapeador.referencia(
                alterar.changeByText(
                        professor.id(),
                        relatorioId,
                        requisicao.texto(),
                        requisicao.versaoBase(),
                        Channel.WEB));
    }

    @PostMapping(
            path = "/relatorios-aula/{relatorioId}/alteracoes",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto alterarPorAudio(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @RequestPart("audio") MultipartFile audio,
            @RequestParam("versaoBase") String versaoBase) {
        int base = versaoBase(versaoBase);
        TemporaryFile arquivo = recepcao.receber(audio);
        return RelatorioAulaMapeador.referencia(
                alterar.changeByAudio(professor.id(), relatorioId, arquivo, base, Channel.WEB));
    }

    @PostMapping("/relatorios-aula/{relatorioId}/nova-tentativa")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto novaTentativa(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return RelatorioAulaMapeador.referencia(
                tentar.run(professor.id(), relatorioId, Channel.WEB));
    }

    @PostMapping(
            path = "/relatorios-aula/{relatorioId}/aprovacao",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public AprovacaoDto aprovar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @Valid @RequestBody AprovacaoRequisicao requisicao) {
        return RelatorioAulaMapeador.aprovacao(
                aprovar.run(
                        professor.id(), relatorioId, requisicao.versaoConfirmada(), Channel.WEB));
    }

    @PostMapping("/relatorios-aula/{relatorioId}/cancelamento")
    public ResponseEntity<Void> cancelar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        cancelar.cancel(professor.id(), relatorioId);
        return ResponseEntity.noContent().build();
    }

    private static int versaoBase(String valor) {
        try {
            int versao = Integer.parseInt(valor.strip());
            if (versao < 1) {
                throw new DomainException(ErrorCode.DADOS_INVALIDOS);
            }
            return versao;
        } catch (NumberFormatException invalido) {
            throw new DomainException(ErrorCode.DADOS_INVALIDOS);
        }
    }
}
