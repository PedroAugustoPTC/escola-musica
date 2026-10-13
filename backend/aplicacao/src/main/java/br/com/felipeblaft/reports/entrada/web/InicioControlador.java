package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.entrada.web.seguranca.ProfessorAutenticado;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do Painel (FR-012, FR-035). */
@RestController
public class InicioControlador {

    private final GetTeacherHomeUseCase obterInicio;
    private final int limiteAudioMb;

    public InicioControlador(
            GetTeacherHomeUseCase obterInicio, PropriedadesAplicacao propriedades) {
        this.obterInicio = obterInicio;
        this.limiteAudioMb = propriedades.audio().limiteMb();
    }

    @GetMapping("/api/v1/inicio")
    public InicioDto inicio(@AuthenticationPrincipal ProfessorAutenticado professor) {
        return InicioMapeador.inicio(obterInicio.run(professor.professor()), limiteAudioMb);
    }
}
