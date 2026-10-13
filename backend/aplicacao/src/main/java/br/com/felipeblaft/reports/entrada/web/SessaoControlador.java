package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.entrada.web.seguranca.CookieSessao;
import br.com.felipeblaft.reports.entrada.web.seguranca.OrigemRequisicaoResolvedor;
import br.com.felipeblaft.reports.entrada.web.seguranca.ProfessorAutenticado;
import br.com.felipeblaft.reports.usecase.access.EndWebSessionUseCase;
import br.com.felipeblaft.reports.usecase.access.QueryTelegramLinkUseCase;
import br.com.felipeblaft.reports.usecase.access.SignInPortalUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entrar, consultar e encerrar a sessão do portal (FR-004, FR-007, FR-008). */
@RestController
@RequestMapping("/api/v1/sessao")
public class SessaoControlador {

    private final SignInPortalUseCase entrar;
    private final EndWebSessionUseCase encerrar;
    private final QueryTelegramLinkUseCase consultarVinculo;
    private final CookieSessao cookie;
    private final OrigemRequisicaoResolvedor origem;

    public SessaoControlador(
            SignInPortalUseCase entrar,
            EndWebSessionUseCase encerrar,
            QueryTelegramLinkUseCase consultarVinculo,
            CookieSessao cookie,
            OrigemRequisicaoResolvedor origem) {
        this.entrar = entrar;
        this.encerrar = encerrar;
        this.consultarVinculo = consultarVinculo;
        this.cookie = cookie;
        this.origem = origem;
    }

    @PostMapping
    public ResponseEntity<SessaoDto> entrar(
            @Valid @RequestBody EntrarRequisicao requisicao, HttpServletRequest http) {
        SignInPortalUseCase.Result resultado =
                entrar.run(requisicao.codigo(), origem.resolver(http));
        SessaoDto corpo =
                new SessaoDto(
                        InicioMapeador.professor(resultado.teacher()),
                        resultado.session().expiresAt(),
                        resultado.telegramLinked());
        return ResponseEntity.ok()
                .header(CookieSessao.cabecalho(), cookie.criar(resultado.token()))
                .body(corpo);
    }

    @GetMapping
    public SessaoDto atual(@AuthenticationPrincipal ProfessorAutenticado professor) {
        return new SessaoDto(
                InicioMapeador.professor(professor.professor()),
                professor.sessaoExpiraEm(),
                consultarVinculo.run(professor.id()));
    }

    @DeleteMapping
    public ResponseEntity<Void> encerrar(HttpServletRequest http) {
        encerrar.run(cookie.ler(http));
        return ResponseEntity.noContent()
                .header(CookieSessao.cabecalho(), cookie.remover())
                .build();
    }
}
