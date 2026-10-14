package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.inbound.web.LessonReportDtos.JobDto;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
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
public class JobController {

    private final QueryJobUseCase query;
    private final DiscardRegistrationInProcessingUseCase discard;

    public JobController(QueryJobUseCase query, DiscardRegistrationInProcessingUseCase discard) {
        this.query = query;
        this.discard = discard;
    }

    /** "Descartar e começar um novo" para um registro ainda em transcrição (retomada, US3). */
    @PostMapping("/{trabalhoId}/descarte")
    public ResponseEntity<Void> discard(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID trabalhoId) {
        discard.run(teacher.id(), trabalhoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{trabalhoId}")
    public JobDto job(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID trabalhoId) {
        return LessonReportMapper.job(query.run(teacher.id(), trabalhoId));
    }
}
