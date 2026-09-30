package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;

/** Conversão entre as entidades JPA de cadastro e os objetos de domínio. */
final class CadastroMapeador {

    private CadastroMapeador() {}

    static Professor paraDominio(ProfessorEntidade e) {
        return new Professor(
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

    static ProfessorEntidade paraEntidade(Professor p) {
        return new ProfessorEntidade(
                p.id(),
                p.nome(),
                p.email(),
                p.ativo(),
                p.codigoAcessoHash(),
                p.codigoAcessoExpiraEm(),
                p.codigoAcessoRenovadoEm(),
                p.criadoEm(),
                p.atualizadoEm());
    }

    static Aluno paraDominio(AlunoEntidade e) {
        return new Aluno(
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

    static AlunoEntidade paraEntidade(Aluno a) {
        return new AlunoEntidade(
                a.id(),
                a.nome(),
                a.dataNascimento(),
                a.cpfCifrado(),
                a.cpfHash(),
                a.nomeResponsavel(),
                a.instrumento(),
                a.ativo(),
                a.criadoEm(),
                a.atualizadoEm());
    }

    static ProfessorAlunoEntidade paraEntidade(ProfessorAluno a) {
        return new ProfessorAlunoEntidade(a.id(), a.professorId(), a.alunoId(), a.criadoEm());
    }
}
