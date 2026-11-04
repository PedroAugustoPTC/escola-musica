import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorAlert } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { StudentForm } from '../components/student/StudentForm';
import { useTeacherConsentTerm } from '../components/student/useConsentTerm';
import type { StudentFormFieldError } from '../components/student/studentFormTypes';
import { buildStudentInput } from '../components/student/studentPayload';
import { useBlockedCountdown } from '../components/student/useBlockedCountdown';
import { msg } from '../messages/messages';
import { fieldErrorsOf, useRegisterMyStudent } from '../services/myStudents';

const NO_FIELD_ERRORS: StudentFormFieldError[] = [];
const CONTEXT = { kicker: msg('MY_STUDENTS_TITLE'), title: 'Cadastrar aluno', backTo: '/my-students' };

/** P02 — Cadastrar aluno: o aluno nasce associado ao professor da sessão. */
export function MyStudentFormScreen() {
  const navigate = useNavigate();
  const consentTerm = useTeacherConsentTerm();
  const register = useRegisterMyStudent();
  const countdown = useBlockedCountdown();
  // a falha é copiada para o estado da tela e a mutação é zerada: as variáveis (com o CPF) não ficam em memória
  const [failure, setFailure] = useState<{ error: unknown; fieldErrors: StudentFormFieldError[] } | null>(
    null,
  );
  const fieldErrors = failure?.fieldErrors ?? NO_FIELD_ERRORS;
  const generalError = failure !== null && fieldErrors.length === 0 && countdown.remainingSeconds === null;

  return (
    <Layout context={CONTEXT} bottomNav>
      <div className="page page--narrow">
        <div>
          <div className="kicker">{msg('MY_STUDENTS_TITLE')}</div>
          <h1 className="page-title">Cadastrar aluno</h1>
        </div>
        {generalError ? <ErrorAlert error={failure.error} fromCatalog /> : null}
        <StudentForm
          consentTerm={consentTerm}
          variant="teacher"
          mode="create"
          fieldErrors={fieldErrors}
          submitting={register.isPending}
          blockedSeconds={countdown.remainingSeconds}
          onSubmit={(values) => {
            setFailure(null);
            register.mutate(buildStudentInput(values), {
              onSuccess: (student) => {
                register.reset();
                navigate(`/my-students/${student.id}`, { state: { notice: 'created' } });
              },
              onError: (error) => {
                setFailure({ error, fieldErrors: fieldErrorsOf(error) });
                countdown.register(error);
                register.reset();
              },
            });
          }}
          onCancel={() => navigate('/my-students')}
        />
      </div>
    </Layout>
  );
}
