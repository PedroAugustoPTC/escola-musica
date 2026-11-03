package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.history.ApprovedReportCriteria;
import br.com.felipeblaft.reports.domain.history.ApprovedReportRow;
import java.util.List;

/**
 * Consulta única dos relatórios aprovados (aula e semestrais), de qualquer professor e aluno,
 * usada pelo histórico do professor e pela visão da escola. Só devolve relatório aprovado; os
 * filtros do critério são aplicados na própria consulta e a ordem é a de {@link
 * br.com.felipeblaft.reports.domain.history.HistoryOrder#NEWEST_FIRST}. Somente leitura.
 */
public interface ApprovedReportQueryPort {

    /** Uma página do resultado, com o total geral do mesmo critério. */
    PageResult<ApprovedReportRow> page(ApprovedReportCriteria criteria, PageRequest request);

    /** Todos os itens do critério, na mesma ordem (histórico do professor, sem paginação). */
    List<ApprovedReportRow> all(ApprovedReportCriteria criteria);
}
