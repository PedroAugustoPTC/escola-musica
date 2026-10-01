package br.com.felipeblaft.relatorios.saida.persistencia;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProfessorAlunoJpa extends JpaRepository<ProfessorAlunoEntidade, UUID> {

    boolean existsByProfessorIdAndAlunoId(UUID professorId, UUID alunoId);
}
