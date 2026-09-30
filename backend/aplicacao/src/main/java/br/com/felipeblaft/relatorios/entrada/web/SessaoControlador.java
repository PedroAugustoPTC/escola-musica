package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.acesso.ConsultarVinculoTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EncerrarSessaoWebCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EntrarPortalCasoDeUso;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.CookieSessao;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.OrigemRequisicaoResolvedor;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
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

    private final EntrarPortalCasoDeUso entrar;
    private final EncerrarSessaoWebCasoDeUso encerrar;
    private final ConsultarVinculoTelegramCasoDeUso consultarVinculo;
    private final CookieSessao cookie;
    private final OrigemRequisicaoResolvedor origem;

    public SessaoControlador(
            EntrarPortalCasoDeUso entrar,
            EncerrarSessaoWebCasoDeUso encerrar,
            ConsultarVinculoTelegramCasoDeUso consultarVinculo,
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
        EntrarPortalCasoDeUso.Resultado resultado =
                entrar.executar(requisicao.codigo(), origem.resolver(http));
        SessaoDto corpo =
                new SessaoDto(
                        InicioMapeador.professor(resultado.professor()),
                        resultado.sessao().expiraEm(),
                        resultado.telegramVinculado());
        return ResponseEntity.ok()
                .header(CookieSessao.cabecalho(), cookie.criar(resultado.token()))
                .body(corpo);
    }

    @GetMapping
    public SessaoDto atual(@AuthenticationPrincipal ProfessorAutenticado professor) {
        return new SessaoDto(
                InicioMapeador.professor(professor.professor()),
                professor.sessaoExpiraEm(),
                consultarVinculo.executar(professor.id()));
    }

    @DeleteMapping
    public ResponseEntity<Void> encerrar(HttpServletRequest http) {
        encerrar.executar(cookie.ler(http));
        return ResponseEntity.noContent()
                .header(CookieSessao.cabecalho(), cookie.remover())
                .build();
    }
}
