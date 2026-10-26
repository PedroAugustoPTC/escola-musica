import { useId, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { Button } from '../components/Button';
import { ConfirmationDialog } from '../components/ConfirmationDialog';
import { ErrorAlert, ErrorState, catalogErrorText } from '../components/ErrorState';
import { Icon } from '../components/Icon';
import { Layout } from '../components/Layout';
import { LoadingState } from '../components/LoadingState';
import { SituationBadge } from '../components/student/SituationBadge';
import { StudentForm } from '../components/student/StudentForm';
import type { StudentFormFieldError } from '../components/student/studentFormTypes';
import { buildStudentUpdate } from '../components/student/studentPayload';
import { useBlockedCountdown } from '../components/student/useBlockedCountdown';
import { msg } from '../messages/messages';
import {
  fieldErrorsOf,
  useChangeMyStudentSituation,
  useMyStudent,
  useUpdateMyStudent,
} from '../services/myStudents';

const NO_FIELD_ERRORS: StudentFormFieldError[] = [];
const CONTEXT = { kicker: msg('MY_STUDENTS_TITLE'), title: 'Aluno', backTo: '/my-students' };

/** P03 — Aluno: editar os dados, inativar (com confirmação) e reativar. */
export function MyStudentDetailScreen() {
  const { studentId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const student = useMyStudent(studentId);
  const update = useUpdateMyStudent(studentId);
  const situation = useChangeMyStudentSituation(studentId);
  const countdown = useBlockedCountdown();
  const guidanceId = useId();
  const created = (location.state as { notice?: string } | null)?.notice === 'created';
  const [notice, setNotice] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [formVersion, setFormVersion] = useState(0);
  // a falha é copiada para o estado da tela e a mutação é zerada: as variáveis (com o CPF) não ficam em memória
  const [failure, setFailure] = useState<{ error: unknown; fieldErrors: StudentFormFieldError[] } | null>(
    null,
  );
  const fieldErrors = failure?.fieldErrors ?? NO_FIELD_ERRORS;

  if (student.isPending) {
    return (
      <Layout context={CONTEXT} bottomNav>
        <LoadingState label="Carregando o aluno" />
      </Layout>
    );
  }
  if (student.isError) {
    return (
      <Layout context={CONTEXT} bottomNav>
        <ErrorState error={student.error} onRetry={() => student.refetch()} fromCatalog />
      </Layout>
    );
  }

  const data = student.data;
  const shared = data.canInactivate === false;
  const shownNotice = notice ?? (created ? msg('REGISTRY_SAVED', { name: data.name }) : null);
  const updateGeneralError =
    failure !== null && fieldErrors.length === 0 && countdown.remainingSeconds === null;

  const changeSituation = (action: 'inactivation' | 'reactivation') => {
    situation.mutate(action, {
      onSuccess: (changed) => {
        setConfirming(false);
        setNotice(
          msg(action === 'inactivation' ? 'STUDENT_INACTIVATED' : 'STUDENT_REACTIVATED', {
            name: changed.name,
          }),
        );
      },
    });
  };

  return (
    <Layout context={CONTEXT} bottomNav>
      <div className="page page--narrow">
        <div>
          <div className="kicker">{msg('MY_STUDENTS_TITLE')}</div>
          <h1 className="page-title">{data.name}</h1>
          <SituationBadge active={data.active} />
        </div>
        {shownNotice ? (
          <div role="status" className="alert alert--ok">
            <Icon name="check_circle" />
            <div className="alert__text">{shownNotice}</div>
          </div>
        ) : null}
        {updateGeneralError ? <ErrorAlert error={failure.error} fromCatalog /> : null}
        <StudentForm
          key={`${data.id}:${formVersion}`}
          variant="teacher"
          mode="edit"
          student={data}
          fieldErrors={fieldErrors}
          submitting={update.isPending}
          blockedSeconds={countdown.remainingSeconds}
          onSubmit={(values) => {
            setNotice(null);
            setFailure(null);
            update.mutate(buildStudentUpdate(values, data), {
              onSuccess: (saved) => {
                update.reset();
                setNotice(msg('REGISTRY_UPDATED', { name: saved.name }));
                setFormVersion((version) => version + 1);
              },
              onError: (error) => {
                setFailure({ error, fieldErrors: fieldErrorsOf(error) });
                countdown.register(error);
                update.reset();
              },
            });
          }}
          onCancel={() => navigate('/my-students')}
        />
        <section className="card" aria-labelledby={`${guidanceId}-title`}>
          <h2 id={`${guidanceId}-title`} className="section-label">
            Situação do aluno
          </h2>
          {shared ? (
            <p id={guidanceId} className="support-text">
              {msg('SHARED_STUDENT_ACTION_DENIED')}
            </p>
          ) : null}
          {situation.isError && !confirming ? <ErrorAlert error={situation.error} fromCatalog /> : null}
          {data.active ? (
            <Button
              variant="danger"
              icon="person_off"
              disabled={shared}
              aria-describedby={shared ? guidanceId : undefined}
              onClick={() => {
                situation.reset();
                setConfirming(true);
              }}
            >
              Inativar aluno
            </Button>
          ) : (
            <Button
              icon="person_check"
              disabled={shared}
              loading={situation.isPending}
              aria-describedby={shared ? guidanceId : undefined}
              onClick={() => {
                setNotice(null);
                changeSituation('reactivation');
              }}
            >
              Reativar aluno
            </Button>
          )}
        </section>
      </div>
      <ConfirmationDialog
        open={confirming}
        icon="person_off"
        danger
        title="Inativar aluno"
        text={msg('MY_STUDENTS_CONFIRM_INACTIVATE', { name: data.name })}
        confirmLabel="Inativar"
        loading={situation.isPending}
        error={situation.isError ? catalogErrorText(situation.error) : undefined}
        onConfirm={() => {
          setNotice(null);
          changeSituation('inactivation');
        }}
        onClose={() => setConfirming(false)}
      />
    </Layout>
  );
}
