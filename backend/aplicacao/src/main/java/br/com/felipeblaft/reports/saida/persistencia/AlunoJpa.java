package br.com.felipeblaft.reports.saida.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AlunoJpa extends JpaRepository<AlunoEntidade, UUID> {

    Optional<AlunoEntidade> findByCpfHash(String cpfHash);

    @Query(
            """
            select a from AlunoEntidade a
            where a.id in (select pa.alunoId from ProfessorAlunoEntidade pa where pa.professorId = :professorId)
            order by lower(a.nome), a.id
            """)
    List<AlunoEntidade> listarDoProfessor(UUID professorId);
}
