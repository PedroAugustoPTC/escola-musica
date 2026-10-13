package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.entrada.web.RelatorioAulaDtos.TrabalhoDto;
import br.com.felipeblaft.reports.entrada.web.seguranca.ProfessorAutenticado;
import br.com.felipeblaft.reports.usecase.job.DiscardRegistrationInProcessingUseCase;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase;
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

    private final QueryJobUseCase consultar;
    private final DiscardRegistrationInProcessingUseCase descartar;

    public TrabalhoControlador(
            QueryJobUseCase consultar, DiscardRegistrationInProcessingUseCase descartar) {
        this.consultar = consultar;
        this.descartar = descartar;
    }

    /** "Descartar e começar um novo" para um registro ainda em transcrição (retomada, US3). */
    @PostMapping("/{trabalhoId}/descarte")
    public ResponseEntity<Void> descartar(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID trabalhoId) {
        descartar.run(professor.id(), trabalhoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{trabalhoId}")
    public TrabalhoDto trabalho(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID trabalhoId) {
        return RelatorioAulaMapeador.trabalho(consultar.run(professor.id(), trabalhoId));
    }
}
