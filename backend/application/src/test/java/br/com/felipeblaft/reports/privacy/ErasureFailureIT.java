package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Falha na remoção de arquivo (feature 007, US4): o banco já está sem dado pessoal, o pedido fica
 * {@code IN_PROGRESS} e uma nova prévia com nova confirmação conclui.
 */
class ErasureFailureIT extends SchoolHistoryBase {

    @Autowired RegisterErasureRequestUseCase register;
    @Autowired PreviewErasureUseCase preview;
    @Autowired ConfirmErasureUseCase confirm;
    @Autowired ErasureRequestRepository erasures;
    @Autowired StudentRepository studentRepository;
    @MockitoSpyBean DocumentStoragePort storage;

    @Test
    void fileFailureKeepsTheRequestInProgressAndTheSecondConfirmationCompletes() {
        Actor admin = Actor.administrator(insertAdministrator().administrator().id(), false);
        Teacher clara = scenario.clara();
        Student student = scenario.student(clara, "Aluno Falha Um", "Violão");
        scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Escalas");

        ErasureRequest request =
                register.run(
                        admin,
                        new RegisterErasureRequestUseCase.Command(
                                student.id(),
                                "MOTHER",
                                "Responsável Fictício",
                                LocalDate.ofInstant(clock.now(), ZoneOffset.UTC),
                                "EMAIL",
                                "PROT-2",
                                UUID.randomUUID().toString()));

        doThrow(new IllegalStateException("simulated I/O failure")).when(storage).delete(anyString());
        PreviewErasureUseCase.Preview first = preview.run(admin, request.id(), correlation());
        confirm.run(
                admin,
                new ConfirmErasureUseCase.Command(
                        request.id(), first.confirmationToken(), "ELIMINAR", correlation()));

        assertThat(erasures.findById(request.id()).orElseThrow().status())
                .isEqualTo(ErasureStatus.IN_PROGRESS);
        assertThat(studentRepository.searchById(student.id())).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student where id = ? and name is null",
                                Integer.class,
                                student.id()))
                .isOne();

        doCallRealMethod().when(storage).delete(anyString());
        PreviewErasureUseCase.Preview second = preview.run(admin, request.id(), correlation());
        confirm.run(
                admin,
                new ConfirmErasureUseCase.Command(
                        request.id(), second.confirmationToken(), "ELIMINAR", correlation()));

        assertThat(erasures.findById(request.id()).orElseThrow().status())
                .isEqualTo(ErasureStatus.COMPLETED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pdf_document where file_removed_at is null",
                                Integer.class))
                .isZero();
    }

    private static String correlation() {
        return UUID.randomUUID().toString();
    }
}
