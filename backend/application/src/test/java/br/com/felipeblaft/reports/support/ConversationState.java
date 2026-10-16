package br.com.felipeblaft.reports.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca o teste de conversa que exercita um dos 10 estados derivados de {@code
 * specs/001-fluxo-relatorios/contracts/telegram-conversa.md} §4 (rastreio da paridade pelo n8n,
 * feature 004, data-model §6.1). Só rotula: não altera nenhuma expectativa do teste. O meta-teste
 * {@code ConversationStateCoverageTest} reprova o build se algum estado ficar sem teste.
 *
 * <ol>
 *   <li>não vinculado
 *   <li>aguardando confirmação de ação
 *   <li>relatório em foco AWAITING_ANSWERS
 *   <li>relatório em foco AWAITING_REVIEW
 *   <li>relatório em processamento
 *   <li>relatório com processamento falho
 *   <li>áudio pendente de confirmação do aluno
 *   <li>fluxo SEMESTER
 *   <li>fluxo HISTORY
 *   <li>vinculado, sem foco
 * </ol>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ConversationState {

    int[] value();
}
