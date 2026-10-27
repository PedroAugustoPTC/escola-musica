import { useNavigate } from 'react-router';
import { ErrorAlert } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { FormErrorSummary } from '../../components/admin/ui';
import { apiErrorText, fieldErrorsOf } from '../../components/admin/fieldErrors';
import { StudentForm } from '../../components/student/StudentForm';
import { useRegisterAdminStudent } from '../../services/admin/students';
import { useActiveTeacherOptions } from '../../services/admin/teachers';

/** A11 — Cadastrar aluno, com os professores escolhidos (multisseleção de professores ativos). */
export function StudentFormScreen() {
  const navigate = useNavigate();
  const teachers = useActiveTeacherOptions();
  const register = useRegisterAdminStudent();
  const fieldErrors = fieldErrorsOf(register.error);

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Cadastros</div>
        <h1 className="page-title">Cadastrar aluno</h1>
      </div>
      {teachers.isPending ? <LoadingState label="Carregando professores" rows={2} /> : null}
      {teachers.isError ? (
        <ErrorAlert error={teachers.error} onRetry={() => void teachers.refetch()} />
      ) : null}
      {teachers.data ? (
        <>
          {register.isError && fieldErrors.length === 0 ? (
            <FormErrorSummary messages={[apiErrorText(register.error)]} />
          ) : null}
          <StudentForm
            variant="admin"
            mode="create"
            teacherOptions={teachers.data.items.map((teacher) => ({ id: teacher.id, name: teacher.name }))}
            fieldErrors={fieldErrors}
            submitting={register.isPending}
            blockedSeconds={null}
            onSubmit={(values) =>
              register.mutate(values, {
                onSuccess: (student) => {
                  // o CPF digitado não fica em nenhum cache depois do envio
                  register.reset();
                  navigate(`/admin/students/${student.id}`, { state: { notice: 'created' } });
                },
              })
            }
            onCancel={() => navigate('/admin/students')}
          />
        </>
      ) : null}
    </div>
  );
}
