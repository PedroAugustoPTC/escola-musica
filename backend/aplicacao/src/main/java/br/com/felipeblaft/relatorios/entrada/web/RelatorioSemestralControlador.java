package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.semestral.AprovarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.CancelarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.GerarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ListarSemestresDisponiveisCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ListarSemestresDisponiveisCasoDeUso.Semestre;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterPreviaSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterPreviaSemestralCasoDeUso.Previa;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterRelatorioSemestralCasoDeUso.VisaoRelatorioSemestral;
import br.com.felipeblaft.relatorios.casodeuso.semestral.SolicitarAlteracaoRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.AlteracaoTextoRequisicao;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.AprovacaoDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.AprovacaoRequisicao;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.InteracaoDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.TrabalhoRefDto;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
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
            ConteudoRelatorioSemestral conteudo,
            int quantidadeRelatoriosEmAberto,
            InteracaoDto ultimaInteracao,
            boolean pdfDisponivel,
            Instant aprovadoEm,
            Instant atualizadoEm) {}

    private final RecepcaoAudioWeb recepcao;
    private final ListarSemestresDisponiveisCasoDeUso listar;
    private final ObterPreviaSemestralCasoDeUso previa;
    private final GerarRelatorioSemestralCasoDeUso gerar;
    private final ObterRelatorioSemestralCasoDeUso obter;
    private final SolicitarAlteracaoRelatorioSemestralCasoDeUso alterar;
    private final AprovarRelatorioSemestralCasoDeUso aprovar;
    private final CancelarRelatorioSemestralCasoDeUso cancelar;
    private final SecoesRelatorioSemestral secoes;

    @SuppressWarnings("java:S107")
    public RelatorioSemestralControlador(
            RecepcaoAudioWeb recepcao,
            ListarSemestresDisponiveisCasoDeUso listar,
            ObterPreviaSemestralCasoDeUso previa,
            GerarRelatorioSemestralCasoDeUso gerar,
            ObterRelatorioSemestralCasoDeUso obter,
            SolicitarAlteracaoRelatorioSemestralCasoDeUso alterar,
            AprovarRelatorioSemestralCasoDeUso aprovar,
            CancelarRelatorioSemestralCasoDeUso cancelar,
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
        return listar.executar(professor.id(), alunoId).stream().map(this::semestre).toList();
    }

    private SemestreDto semestre(Semestre s) {
        return new SemestreDto(
                s.periodo().chave(),
                secoes.rotuloPeriodo(s.periodo().chave()),
                s.periodo().inicio(),
                s.periodo().fim(),
                s.emAndamento(),
                s.preSelecionado());
    }

    @GetMapping("/alunos/{alunoId}/semestres/{periodoChave}/previa")
    public PreviaSemestralDto previa(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID alunoId,
            @PathVariable String periodoChave) {
        Previa p = previa.executar(professor.id(), alunoId, periodoChave);
        return new PreviaSemestralDto(
                p.periodo().chave(),
                p.aulasAprovadas(),
                p.relatoriosAulaEmAberto(),
                p.semestralAprovadoId().orElse(null),
                p.semestralEmAberto().map(InicioMapeador::emAberto).orElse(null));
    }

    @PostMapping(
            path = "/alunos/{alunoId}/relatorios-semestrais",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrabalhoRefDto gerar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID alunoId,
            @Valid @RequestBody GerarSemestralRequisicao requisicao) {
        TrabalhoProcessamento trabalho =
                gerar.executar(
                        professor.id(),
                        alunoId,
                        requisicao.periodoChave(),
                        Boolean.TRUE.equals(requisicao.confirmarMesmoComEmAberto()),
                        Canal.WEB);
        return new TrabalhoRefDto(trabalho.id());
    }

    @GetMapping("/relatorios-semestrais/{relatorioId}")
    public RelatorioSemestralDto obter(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return relatorio(obter.executar(professor.id(), relatorioId));
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
                alterar.alterarPorTexto(
                                professor.id(),
                                relatorioId,
                                requisicao.texto(),
                                requisicao.versaoBase(),
                                Canal.WEB)
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
            throw new ExcecaoDominio(CodigoErro.DADOS_INVALIDOS);
        }
        ArquivoTemporario arquivo = recepcao.receber(audio);
        return new TrabalhoRefDto(
                alterar.alterarPorAudio(professor.id(), relatorioId, arquivo, base, Canal.WEB)
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
                aprovar.executar(
                        professor.id(), relatorioId, requisicao.versaoConfirmada(), Canal.WEB));
    }

    @PostMapping("/relatorios-semestrais/{relatorioId}/cancelamento")
    public ResponseEntity<Void> cancelar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        cancelar.cancelar(professor.id(), relatorioId);
        return ResponseEntity.noContent().build();
    }

    private RelatorioSemestralDto relatorio(VisaoRelatorioSemestral visao) {
        RelatorioSemestral r = visao.relatorio();
        InformacoesGerais info = r.informacoesGerais();
        String etapa =
                visao.trabalhoEmCurso()
                        .map(t -> t.etapa().name())
                        .orElse(r.etapa() == null ? null : r.etapa().name());
        return new RelatorioSemestralDto(
                r.id(),
                r.alunoId(),
                r.periodo().chave(),
                r.status().name(),
                etapa,
                visao.trabalhoEmCurso().map(TrabalhoProcessamento::id).orElse(null),
                r.versaoVigente(),
                r.canalOrigem().name(),
                new InformacoesGeraisDto(
                        info.nome(),
                        info.idade(),
                        info.instrumento(),
                        info.professor(),
                        secoes.rotuloPeriodo(info.periodoChave()),
                        info.periodoInicio(),
                        info.periodoFim(),
                        info.quantidadeAulasConsideradas()),
                r.conteudo(),
                r.quantidadeRelatoriosEmAberto(),
                visao.ultimaInteracao().map(RelatorioAulaMapeador::interacao).orElse(null),
                visao.pdfDisponivel(),
                r.aprovadoEm(),
                r.atualizadoEm());
    }
}
