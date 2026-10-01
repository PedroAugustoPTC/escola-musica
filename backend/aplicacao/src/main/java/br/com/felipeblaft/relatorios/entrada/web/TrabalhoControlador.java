package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.trabalho.ConsultarTrabalhoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.DescartarRegistroEmProcessamentoCasoDeUso;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.TrabalhoDto;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Etapa e resultado de um processamento, consultados pela Web a cada 2 s (FR-021). */
@RestController
@RequestMapping("/api/v1/trabalhos")
public class TrabalhoControlador {

    private final ConsultarTrabalhoCasoDeUso consultar;
    private final DescartarRegistroEmProcessamentoCasoDeUso descartar;

    public TrabalhoControlador(
            ConsultarTrabalhoCasoDeUso consultar,
            DescartarRegistroEmProcessamentoCasoDeUso descartar) {
        this.consultar = consultar;
        this.descartar = descartar;
    }

    /** "Descartar e começar um novo" para um registro ainda em transcrição (retomada, US3). */
    @PostMapping("/{trabalhoId}/descarte")
    public ResponseEntity<Void> descartar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID trabalhoId) {
        descartar.executar(professor.id(), trabalhoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{trabalhoId}")
    public TrabalhoDto trabalho(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID trabalhoId) {
        return RelatorioAulaMapeador.trabalho(consultar.executar(professor.id(), trabalhoId));
    }
}
