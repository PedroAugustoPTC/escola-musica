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

    record HomeSummary(int totalStudents, List<String> students) {}

    record SearchResult(String type, List<String> students) {}
}
