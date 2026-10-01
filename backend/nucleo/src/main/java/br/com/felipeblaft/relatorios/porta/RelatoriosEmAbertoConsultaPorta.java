package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consultas de leitura sobre relatórios em aberto e aulas (FR-012, FR-034, FR-035). */
public interface RelatoriosEmAbertoConsultaPorta {

    /** Relatórios em aberto do professor, do mais recente ao mais antigo. */
    List<RelatorioEmAberto> listarDoProfessor(UUID professorId);

    /** Relatório de aula (ou registro em transcrição) em aberto do professor para o aluno. */
    Optional<RelatorioEmAberto> buscarDoAluno(UUID professorId, UUID alunoId);

    /** Data da última aula de cada aluno do professor. */
    Map<UUID, LocalDate> ultimaAulaPorAluno(UUID professorId);
}
