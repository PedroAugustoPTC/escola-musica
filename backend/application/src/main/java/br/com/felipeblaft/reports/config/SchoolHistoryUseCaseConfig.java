package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.port.ApprovedReportQueryPort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.service.OfficialDocumentService;
import br.com.felipeblaft.reports.usecase.document.GetSchoolReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso da visão da escola (feature 006, somente Web): o histórico de relatórios aprovados
 * de qualquer professor e aluno e a entrega do PDF oficial da versão aprovada. Só o administrador
 * os alcança; a decisão é do núcleo, pela matriz de permissões.
 */
@Configuration(proxyBeanMethods = false)
public class SchoolHistoryUseCaseConfig {

    @Bean
    OfficialDocumentService officialDocumentService(
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            DocumentGenerationService generation) {
        return new OfficialDocumentService(versions, documents, generation);
    }

    @Bean
    QuerySchoolHistoryUseCase querySchoolHistoryUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            ApprovedReportQueryPort approvedReports,
            AuthorshipService authorship,
            TransactionPort transaction) {
        return new QuerySchoolHistoryUseCase(
                authorization, students, approvedReports, authorship, transaction);
    }

    @Bean
    GetSchoolReportDocumentUseCase getSchoolReportDocumentUseCase(
            ActorAuthorizationService authorization,
            LessonReportRepository lessonReports,
            SemesterReportRepository semesterReports,
            OfficialDocumentService officialDocument,
            AuthorshipService authorship,
            TransactionPort transaction) {
        return new GetSchoolReportDocumentUseCase(
                authorization,
                lessonReports,
                semesterReports,
                officialDocument,
                authorship,
                transaction);
    }
}
