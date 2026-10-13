package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RelatorioSemestralJpa extends JpaRepository<RelatorioSemestralEntidade, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RelatorioSemestralEntidade r where r.id = :id")
    Optional<RelatorioSemestralEntidade> buscarParaAtualizar(UUID id);

    @Query(
            """
            select r from RelatorioSemestralEntidade r
            where r.professorId = :professorId and r.alunoId = :alunoId
              and r.periodoChave = :periodoChave and r.status = :status
            """)
    Optional<RelatorioSemestralEntidade> doTrio(
            UUID professorId, UUID alunoId, String periodoChave, String status);

    @Query(
            """
            select r from RelatorioSemestralEntidade r
            where r.professorId = :professorId and r.alunoId = :alunoId and r.status = 'APROVADO'
            order by r.periodoInicio desc
            """)
    List<RelatorioSemestralEntidade> aprovados(UUID professorId, UUID alunoId);

    @Query(
            """
            select r from RelatorioSemestralEntidade r
            where r.professorId = :professorId and r.status = 'AGUARDANDO_CONFERENCIA'
            order by r.atualizadoEm desc
            """)
    List<RelatorioSemestralEntidade> emAbertoDoProfessor(UUID professorId);
}
