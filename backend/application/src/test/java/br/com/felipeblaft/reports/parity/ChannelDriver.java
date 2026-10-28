package br.com.felipeblaft.reports.parity;

import java.util.List;

/**
 * Operações de negócio que o roteiro de paridade executa em um canal (R-16, SC-006). Cada método
 * devolve um resultado neutro (código do catálogo ou dados), para comparar Web e Telegram.
 */
public interface ChannelDriver {

    String name();

    /** Entra (Web) ou vincula (Telegram); devolve {@code SUCESSO} ou o código de erro. */
    String signIn(String code) throws Exception;

    /** Início: total de alunos e nomes dos primeiros alunos. */
    HomeSummary start() throws Exception;

    /** Busca de aluno: tipo do resultado e nomes (para um único resultado, só o nome). */
    SearchResult search(String text) throws Exception;

    /** Encerra o acesso e confirma que ele deixou de valer. */
    String end() throws Exception;

    // ---- "Meus alunos" (feature 005): resultado neutro, "SUCCESS" ou o código de erro

    /** Data de nascimento no formato digitado, {@code dd/MM/aaaa}; responsável só para menor. */
    record NewStudent(
            String name, String birthDate, String cpf, String instrument, Guardian guardian) {}

    record Guardian(String name, String phone, String email) {}

    default String registerStudent(NewStudent student) throws Exception {
        throw new UnsupportedOperationException(name() + " não cadastra aluno");
    }

    /** Nomes da lista de alunos {@code ACTIVE} ou {@code INACTIVE}, na ordem da lista. */
    default List<String> listStudents(String status) throws Exception {
        throw new UnsupportedOperationException(name() + " não lista aluno");
    }

    default List<String> searchStudents(String text) throws Exception {
        throw new UnsupportedOperationException(name() + " não busca aluno");
    }

    /** Cartão do aluno em forma neutra: {@code nome|idade|instrumento|situação}. */
    default String openStudent(String name) throws Exception {
        throw new UnsupportedOperationException(name() + " não abre aluno");
    }

    default String changeInstrument(String name, String instrument) throws Exception {
        throw new UnsupportedOperationException(name() + " não edita aluno");
    }

    default String inactivateStudent(String name) throws Exception {
        throw new UnsupportedOperationException(name() + " não inativa aluno");
    }

    default String reactivateStudent(String name) throws Exception {
        throw new UnsupportedOperationException(name() + " não reativa aluno");
    }

    record HomeSummary(int totalStudents, List<String> students) {}

    record SearchResult(String type, List<String> students) {}
}
