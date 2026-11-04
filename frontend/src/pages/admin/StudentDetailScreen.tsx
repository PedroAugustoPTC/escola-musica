import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { Button } from '../../components/Button';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { FormErrorSummary, StatusBadge } from '../../components/admin/ui';
import { apiErrorText, fieldErrorsOf } from '../../components/admin/fieldErrors';
import { StudentAssociationsSection } from './StudentAssociationsSection';
import { StudentForm } from '../../components/student/StudentForm';
import { StudentConsentSection } from '../../components/admin/StudentConsentSection';
import { useAdminConsentTerm } from '../../components/student/useConsentTerm';
import { msg } from '../../messages/messages';
import {
  useAdminStudent,
  useInactivateAdminStudent,
  useReactivateAdminStudent,
  useUpdateAdminStudent,
} from '../../services/admin/students';

/** A12 — Aluno: editar (CPF em branco mantém o atual), inativar e reativar. */
export function StudentDetailScreen() {
  const { studentId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const consentTerm = useAdminConsentTerm();
  const student = useAdminStudent(studentId);
  const update = useUpdateAdminStudent(studentId);
  const inactivate = useInactivateAdminStudent(studentId);
  const reactivate = useReactivateAdminStudent(studentId);
  const [notice, setNotice] = useState(
    (location.state as { notice?: string } | null)?.notice === 'created' ? 'Aluno cadastrado.' : '',
  );
  const [confirmInactivate, setConfirmInactivate] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const firstRender = useRef(true);
  const data = student.data;
  const active = data?.active;

  // a ação de situação troca os botões: devolve o foco ao título para não o perder
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    heading.current?.focus();
  }, [active]);

  if (student.isPending) return <LoadingState label="Carregando aluno" />;
  if (student.isError || !data) {
    return (
      <div className="page page--narrow">
        <ErrorAlert error={student.error} onRetry={() => void student.refetch()} />
        <Link to="/admin/students" className="button button--secondary">
          Voltar para os alunos
        </Link>
      </div>
    );
  }

  const fieldErrors = fieldErrorsOf(update.error);

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Aluno</div>
        <h1 className="page-title" ref={heading} tabIndex={-1}>
          {data.name}
        </h1>
        <div className="admin-list__meta">
          <StatusBadge active={data.active} />
          {data.age !== undefined ? <span className="admin-list__meta-item">{data.age} anos</span> : null}
          {data.instrument ? <span className="admin-list__meta-item">{data.instrument}</span> : null}
        </div>
        <div className="admin-actions">
          <Link to={`/admin/history?studentId=${data.id}`} className="button button--secondary">
            <Icon name="history" size="medium" />
            {msg('SCHOOL_HISTORY_STUDENT_SHORTCUT')}
          </Link>
          <Link to={`/admin/audit?targetId=${data.id}`} className="button button--secondary">
            <Icon name="shield_person" size="medium" />
            {msg('AUDIT_STUDENT_SHORTCUT')}
          </Link>
        </div>
      </div>

      <div role="status" className={notice ? 'alert alert--ok' : undefined}>
        {notice ? (
          <>
            <Icon name="check_circle" />
            <div className="alert__text">{notice}</div>
          </>
        ) : null}
      </div>

      <section className="admin-section" aria-labelledby="student-data">
        <h2 id="student-data" className="card-title">
          Dados
        </h2>
        {update.isError && fieldErrors.length === 0 ? (
          <FormErrorSummary messages={[apiErrorText(update.error)]} />
        ) : null}
        <StudentForm
            consentTerm={consentTerm}
          variant="admin"
          mode="edit"
          student={data}
          fieldErrors={fieldErrors}
          submitting={update.isPending}
          blockedSeconds={null}
          onSubmit={(values) => {
            setNotice('');
            update.mutate(values, {
              onSuccess: () => {
                // o CPF digitado não fica em nenhum cache depois do envio
                update.reset();
                setNotice('Dados salvos.');
              },
            });
          }}
          onCancel={() => navigate('/admin/students')}
        />
      </section>

      <StudentAssociationsSection student={data} />

      <StudentConsentSection studentId={data.id} />

      <section className="admin-section" aria-labelledby="student-status">
        <h2 id="student-status" className="card-title">
          Situação
        </h2>
        <div className="admin-actions">
          {data.active ? (
            <Button variant="danger" icon="person_off" onClick={() => setConfirmInactivate(true)}>
              Inativar
            </Button>
          ) : (
            <Button
              icon="person_check"
              loading={reactivate.isPending}
              onClick={() =>
                reactivate.mutate(undefined, {
                  onSuccess: (result) => setNotice(msg('STUDENT_REACTIVATED', { name: result.name })),
                })
              }
            >
              Reativar
            </Button>
          )}
        </div>
        {reactivate.isError ? (
          <div role="alert" className="alert alert--error">
            <Icon name="error" />
            <div className="alert__text">{apiErrorText(reactivate.error)}</div>
          </div>
        ) : null}
      </section>

      <ConfirmationDialog
        open={confirmInactivate}
        title="Inativar aluno"
        text={msg('CONFIRM_INACTIVATE_STUDENT', { name: data.name })}
        icon="person_off"
        danger
        confirmLabel="Inativar"
        loading={inactivate.isPending}
        error={inactivate.isError ? apiErrorText(inactivate.error) : undefined}
        onConfirm={() =>
          inactivate.mutate(undefined, {
            onSuccess: (result) => {
              setConfirmInactivate(false);
              setNotice(msg('STUDENT_INACTIVATED', { name: result.name }));
            },
          })
        }
        onClose={() => {
          inactivate.reset();
          setConfirmInactivate(false);
        }}
      />
    </div>
  );
}
