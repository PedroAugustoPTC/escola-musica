package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RelatorioAulaJpa extends JpaRepository<RelatorioAulaEntidade, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RelatorioAulaEntidade r where r.id = :id")
    Optional<RelatorioAulaEntidade> buscarParaAtualizar(UUID id);

    @Query(
            """
            select r from RelatorioAulaEntidade r
            where r.professorId = :professorId and r.alunoId = :alunoId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
            """)
    Optional<RelatorioAulaEntidade> buscarEmAberto(UUID professorId, UUID alunoId);

    @Query(
            """
            select r from RelatorioAulaEntidade r
            where r.professorId = :professorId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
            order by r.atualizadoEm desc
            """)
    List<RelatorioAulaEntidade> listarEmAbertoDoProfessor(UUID professorId);
}
