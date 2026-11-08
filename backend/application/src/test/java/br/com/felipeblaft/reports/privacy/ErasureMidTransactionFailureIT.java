package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
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
 * Falha no meio da transação do banco (feature 007, US4, T101): se a gravação do aluno eliminado
 * falha depois de o aluno e os relatórios já terem sido anonimizados, a transação inteira desfaz.
 * Nada fica meio anonimizado e o pedido continua aguardando confirmação; a confirmação seguinte
 * conclui.
 */
class ErasureMidTransactionFailureIT extends SchoolHistoryBase {

    @Autowired RegisterErasureRequestUseCase register;
    @Autowired PreviewErasureUseCase preview;
    @Autowired ConfirmErasureUseCase confirm;
    @MockitoSpyBean ErasureRequestRepository erasures;

    @Test
    void failureAfterAnonymizingRollsBackEverythingAndALaterConfirmationCompletes() {
        Actor admin = Actor.administrator(insertAdministrator().administrator().id(), false);
        Teacher clara = scenario.clara();
        Student student = scenario.student(clara, "Mid Transaction Student", "Guitar");
        scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Scales");

        ErasureRequest request =
                register.run(
                        admin,
                        new RegisterErasureRequestUseCase.Command(
                                student.id(),
                                "MOTHER",
                                "Requester",
                                LocalDate.ofInstant(clock.now(), ZoneOffset.UTC),
                                "EMAIL",
                                "PROT-3",
                                UUID.randomUUID().toString()));
        PreviewErasureUseCase.Preview first =
                preview.run(admin, request.id(), UUID.randomUUID().toString());
        ErasureStatus before = erasures.findById(request.id()).orElseThrow().status();

        doThrow(new IllegalStateException("simulated database failure"))
                .when(erasures)
                .recordErased(any(ErasedStudent.class));
        assertThatThrownBy(
                        () ->
                                confirm.run(
                                        admin,
                                        new ConfirmErasureUseCase.Command(
                                                request.id(),
                                                first.confirmationToken(),
                                                "ELIMINAR",
                                                UUID.randomUUID().toString())))
                .isInstanceOf(RuntimeException.class);

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student where id = ? and name is not null",
                                Integer.class,
                                student.id()))
                .as("aluno continua inteiro")
                .isOne();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from lesson_report where student_id = ?"
                                        + " and anonymized_at is not null",
                                Integer.class,
                                student.id()))
                .as("nenhum relatório ficou anonimizado")
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from erased_student where student_id = ?",
                                Integer.class,
                                student.id()))
                .isZero();
        assertThat(erasures.findById(request.id()).orElseThrow().status()).isEqualTo(before);

        doCallRealMethod().when(erasures).recordErased(any(ErasedStudent.class));
        PreviewErasureUseCase.Preview second =
                preview.run(admin, request.id(), UUID.randomUUID().toString());
        confirm.run(
                admin,
                new ConfirmErasureUseCase.Command(
                        request.id(),
                        second.confirmationToken(),
                        "ELIMINAR",
                        UUID.randomUUID().toString()));

        assertThat(erasures.findById(request.id()).orElseThrow().status())
                .isEqualTo(ErasureStatus.COMPLETED);
        assertThat(erasures.isErased(student.id())).isTrue();
    }
}
