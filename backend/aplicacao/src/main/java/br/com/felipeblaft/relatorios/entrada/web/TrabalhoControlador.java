package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.trabalho.ConsultarTrabalhoCasoDeUso;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.TrabalhoDto;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Etapa e resultado de um processamento, consultados pela Web a cada 2 s (FR-021). */
@RestController
@RequestMapping("/api/v1/trabalhos")
public class TrabalhoControlador {

    private final ConsultarTrabalhoCasoDeUso consultar;

    public TrabalhoControlador(ConsultarTrabalhoCasoDeUso consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/{trabalhoId}")
    public TrabalhoDto trabalho(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID trabalhoId) {
        return RelatorioAulaMapeador.trabalho(consultar.executar(professor.id(), trabalhoId));
    }
}
