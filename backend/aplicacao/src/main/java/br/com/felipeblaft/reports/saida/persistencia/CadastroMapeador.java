package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;

/** Conversão entre as entidades JPA de cadastro e os objetos de domínio. */
final class CadastroMapeador {

    private CadastroMapeador() {}

    static Teacher paraDominio(ProfessorEntidade e) {
        return new Teacher(
                e.getId(),
                e.getNome(),
                e.getEmail(),
                e.isAtivo(),
                e.getCodigoAcessoHash(),
                e.getCodigoAcessoExpiraEm(),
                e.getCodigoAcessoRenovadoEm(),
                e.getCriadoEm(),
                e.getAtualizadoEm());
    }

    static ProfessorEntidade paraEntidade(Teacher p) {
        return new ProfessorEntidade(
                p.id(),
                p.name(),
                p.email(),
                p.active(),
                p.accessCodeHash(),
                p.accessCodeExpiresAt(),
                p.accessCodeRenewedAt(),
                p.createdAt(),
                p.updatedAt());
    }

    static Student paraDominio(AlunoEntidade e) {
        return new Student(
                e.getId(),
                e.getNome(),
                e.getDataNascimento(),
                e.getCpfCifrado(),
                e.getCpfHash(),
                e.getNomeResponsavel(),
                e.getInstrumento(),
                e.isAtivo(),
                e.getCriadoEm(),
                e.getAtualizadoEm());
    }

    static AlunoEntidade paraEntidade(Student a) {
        return new AlunoEntidade(
                a.id(),
                a.name(),
                a.birthDate(),
                a.encryptedCpf(),
                a.cpfHash(),
                a.guardianName(),
                a.instrument(),
                a.active(),
                a.createdAt(),
                a.updatedAt());
    }

    static ProfessorAlunoEntidade paraEntidade(TeacherStudent a) {
        return new ProfessorAlunoEntidade(a.id(), a.teacherId(), a.studentId(), a.createdAt());
    }
}
