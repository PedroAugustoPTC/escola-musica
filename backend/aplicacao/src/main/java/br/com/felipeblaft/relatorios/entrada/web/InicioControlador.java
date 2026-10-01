package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterInicioProfessorCasoDeUso;
import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do Painel (FR-012, FR-035). */
@RestController
public class InicioControlador {

    private final ObterInicioProfessorCasoDeUso obterInicio;
    private final int limiteAudioMb;

    public InicioControlador(
            ObterInicioProfessorCasoDeUso obterInicio, PropriedadesAplicacao propriedades) {
        this.obterInicio = obterInicio;
        this.limiteAudioMb = propriedades.audio().limiteMb();
    }

    @GetMapping("/api/v1/inicio")
    public InicioDto inicio(@AuthenticationPrincipal ProfessorAutenticado professor) {
        return InicioMapeador.inicio(obterInicio.executar(professor.professor()), limiteAudioMb);
    }
}
