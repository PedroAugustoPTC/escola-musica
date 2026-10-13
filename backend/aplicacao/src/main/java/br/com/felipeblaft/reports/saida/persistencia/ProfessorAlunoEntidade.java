package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "professor_aluno")
public class ProfessorAlunoEntidade {

    @Id private UUID id;

    @Column(name = "professor_id", nullable = false)
    private UUID professorId;

    @Column(name = "aluno_id", nullable = false)
    private UUID alunoId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected ProfessorAlunoEntidade() {}

    ProfessorAlunoEntidade(UUID id, UUID professorId, UUID alunoId, Instant criadoEm) {
        this.id = id;
        this.professorId = professorId;
        this.alunoId = alunoId;
        this.criadoEm = criadoEm;
    }

    UUID getId() {
        return id;
    }

    UUID getProfessorId() {
        return professorId;
    }

    UUID getAlunoId() {
        return alunoId;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }
}
