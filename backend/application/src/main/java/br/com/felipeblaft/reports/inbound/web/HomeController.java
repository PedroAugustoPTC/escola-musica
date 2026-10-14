package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do Painel (FR-012, FR-035). */
@RestController
public class HomeController {

    private final GetTeacherHomeUseCase getHome;
    private final int audioLimitMb;

    public HomeController(GetTeacherHomeUseCase getHome, ApplicationProperties properties) {
        this.getHome = getHome;
        this.audioLimitMb = properties.audio().limitMb();
    }

    @GetMapping("/api/v1/inicio")
    public HomeDto start(@AuthenticationPrincipal AuthenticatedTeacher teacher) {
        return HomeMapper.start(getHome.run(teacher.teacher()), audioLimitMb);
    }
}
