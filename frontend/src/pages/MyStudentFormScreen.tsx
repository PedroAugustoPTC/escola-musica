import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import { ErrorAlert } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { StudentForm } from '../components/student/StudentForm';
import { buildStudentInput } from '../components/student/studentPayload';
import { useBlockedCountdown } from '../components/student/useBlockedCountdown';
import { msg } from '../messages/messages';
import { fieldErrorsOf, useRegisterMyStudent } from '../services/myStudents';

const CONTEXT = { kicker: msg('MY_STUDENTS_TITLE'), title: 'Cadastrar aluno', backTo: '/my-students' };

/** P02 — Cadastrar aluno: o aluno nasce associado ao professor da sessão. */
export function MyStudentFormScreen() {
  const navigate = useNavigate();
  const register = useRegisterMyStudent();
  const countdown = useBlockedCountdown();
  const fieldErrors = useMemo(
    () => (register.isError ? fieldErrorsOf(register.error) : []),
    [register.isError, register.error],
  );
  const generalError = register.isError && fieldErrors.length === 0 && countdown.remainingSeconds === null;

  return (
    <Layout context={CONTEXT} bottomNav>
      <div className="page page--narrow">
        <div>
          <div className="kicker">{msg('MY_STUDENTS_TITLE')}</div>
          <h1 className="page-title">Cadastrar aluno</h1>
        </div>
        {generalError ? <ErrorAlert error={register.error} /> : null}
        <StudentForm
          variant="teacher"
          mode="create"
          fieldErrors={fieldErrors}
          submitting={register.isPending}
          blockedSeconds={countdown.remainingSeconds}
          onSubmit={(values) =>
            register.mutate(buildStudentInput(values), {
              onSuccess: (student) =>
                navigate(`/my-students/${student.id}`, { state: { notice: 'created' } }),
              onError: countdown.register,
            })
          }
          onCancel={() => navigate('/my-students')}
        />
      </div>
    </Layout>
  );
}
