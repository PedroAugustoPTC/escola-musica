package br.com.felipeblaft.relatorios.paridade;

import java.util.List;

/**
 * Operações de negócio que o roteiro de paridade executa em um canal (R-16, SC-006). Cada método
 * devolve um resultado neutro (código do catálogo ou dados), para comparar Web e Telegram.
 */
public interface CanalDriver {

    String nome();

    /** Entra (Web) ou vincula (Telegram); devolve {@code SUCESSO} ou o código de erro. */
    String entrar(String codigo) throws Exception;

    /** Início: total de alunos e nomes dos primeiros alunos. */
    ResumoInicio inicio() throws Exception;

    /** Busca de aluno: tipo do resultado e nomes (para um único resultado, só o nome). */
    ResultadoBusca buscar(String texto) throws Exception;

    /** Encerra o acesso e confirma que ele deixou de valer. */
    String encerrar() throws Exception;

    record ResumoInicio(int totalAlunos, List<String> alunos) {}

    record ResultadoBusca(String tipo, List<String> alunos) {}
}
