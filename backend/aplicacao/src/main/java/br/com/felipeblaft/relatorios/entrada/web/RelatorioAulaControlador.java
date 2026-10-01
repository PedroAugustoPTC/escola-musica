package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.CancelarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioEmAbertoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.RegistrarAulaPorAudioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ResponderPerguntasCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.SolicitarAlteracaoRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.TentarNovamenteRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.AlteracaoTextoRequisicao;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.AprovacaoDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.AprovacaoRequisicao;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.RelatorioAulaDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.RespostaTextoRequisicao;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.TrabalhoRefDto;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
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
    private final RegistrarAulaPorAudioCasoDeUso registrar;
    private final ObterRelatorioAulaCasoDeUso obter;
    private final ResponderPerguntasCasoDeUso responder;
    private final SolicitarAlteracaoRelatorioAulaCasoDeUso alterar;
    private final TentarNovamenteRelatorioAulaCasoDeUso tentar;
    private final AprovarRelatorioAulaCasoDeUso aprovar;
    private final CancelarRelatorioAulaCasoDeUso cancelar;
    private final ObterRelatorioEmAbertoCasoDeUso obterEmAberto;

    @SuppressWarnings("java:S107")
    public RelatorioAulaControlador(
            RecepcaoAudioWeb recepcao,
            RegistrarAulaPorAudioCasoDeUso registrar,
            ObterRelatorioAulaCasoDeUso obter,
            ResponderPerguntasCasoDeUso responder,
            SolicitarAlteracaoRelatorioAulaCasoDeUso alterar,
            TentarNovamenteRelatorioAulaCasoDeUso tentar,
            AprovarRelatorioAulaCasoDeUso aprovar,
            CancelarRelatorioAulaCasoDeUso cancelar,
            ObterRelatorioEmAbertoCasoDeUso obterEmAberto) {
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
        ArquivoTemporario arquivo = recepcao.receber(audio);
        return RelatorioAulaMapeador.referencia(
                registrar.executar(professor.id(), alunoId, arquivo, Canal.WEB));
    }

    /** Relatório em aberto do aluno (FR-034): 200 com o relatório ou 204 se não houver. */
    @GetMapping("/alunos/{alunoId}/relatorio-em-aberto")
    public ResponseEntity<RelatorioEmAbertoDto> relatorioEmAberto(
            @AuthenticationPrincipal ProfessorAutenticado professor, @PathVariable UUID alunoId) {
        return obterEmAberto
                .executar(professor.id(), alunoId)
                .map(InicioMapeador::emAberto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/relatorios-aula/{relatorioId}")
    public RelatorioAulaDto obter(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return RelatorioAulaMapeador.relatorio(obter.executar(professor.id(), relatorioId));
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
                responder.responderTexto(
                        professor.id(), relatorioId, requisicao.texto(), Canal.WEB));
    }

    @PostMapping(
            path = "/relatorios-aula/{relatorioId}/respostas",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto responderAudio(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @RequestPart("audio") MultipartFile audio) {
        ArquivoTemporario arquivo = recepcao.receber(audio);
        return RelatorioAulaMapeador.referencia(
                responder.responderAudio(professor.id(), relatorioId, arquivo, Canal.WEB));
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
                alterar.alterarPorTexto(
                        professor.id(),
                        relatorioId,
                        requisicao.texto(),
                        requisicao.versaoBase(),
                        Canal.WEB));
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
        ArquivoTemporario arquivo = recepcao.receber(audio);
        return RelatorioAulaMapeador.referencia(
                alterar.alterarPorAudio(professor.id(), relatorioId, arquivo, base, Canal.WEB));
    }

    @PostMapping("/relatorios-aula/{relatorioId}/nova-tentativa")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto novaTentativa(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return RelatorioAulaMapeador.referencia(
                tentar.executar(professor.id(), relatorioId, Canal.WEB));
    }

    @PostMapping(
            path = "/relatorios-aula/{relatorioId}/aprovacao",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public AprovacaoDto aprovar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @Valid @RequestBody AprovacaoRequisicao requisicao) {
        return RelatorioAulaMapeador.aprovacao(
                aprovar.executar(
                        professor.id(), relatorioId, requisicao.versaoConfirmada(), Canal.WEB));
    }

    @PostMapping("/relatorios-aula/{relatorioId}/cancelamento")
    public ResponseEntity<Void> cancelar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        cancelar.cancelar(professor.id(), relatorioId);
        return ResponseEntity.noContent().build();
    }

    private static int versaoBase(String valor) {
        try {
            int versao = Integer.parseInt(valor.strip());
            if (versao < 1) {
                throw new ExcecaoDominio(CodigoErro.DADOS_INVALIDOS);
            }
            return versao;
        } catch (NumberFormatException invalido) {
            throw new ExcecaoDominio(CodigoErro.DADOS_INVALIDOS);
        }
    }
}
