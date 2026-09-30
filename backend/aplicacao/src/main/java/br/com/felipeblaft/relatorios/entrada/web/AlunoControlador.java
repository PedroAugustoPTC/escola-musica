package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterResumoAlunoCasoDeUso;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alunos associados, com busca aproximada (FR-013), e resumo de um aluno (FR-009). */
@RestController
@Validated
@RequestMapping("/api/v1/alunos")
public class AlunoControlador {

    private final BuscarAlunosCasoDeUso buscarAlunos;
    private final ObterResumoAlunoCasoDeUso obterResumo;

    public AlunoControlador(
            BuscarAlunosCasoDeUso buscarAlunos, ObterResumoAlunoCasoDeUso obterResumo) {
        this.buscarAlunos = buscarAlunos;
        this.obterResumo = obterResumo;
    }

    @GetMapping
    public BuscaAlunosDto alunos(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @RequestParam(required = false) @Size(max = 100) String busca) {
        return InicioMapeador.busca(buscarAlunos.executar(professor.id(), busca));
    }

    @GetMapping("/{alunoId}")
    public AlunoResumoDto aluno(
            @AuthenticationPrincipal ProfessorAutenticado professor, @PathVariable UUID alunoId) {
        return InicioMapeador.aluno(obterResumo.executar(professor.id(), alunoId));
    }
}
