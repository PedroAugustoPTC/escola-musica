import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { Button } from '../../components/Button';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { OneTimeSecretPanel } from '../../components/admin/OneTimeSecretPanel';
import { TeacherForm } from '../../components/admin/TeacherForm';
import { StatusBadge } from '../../components/admin/ui';
import { apiErrorText } from '../../components/admin/fieldErrors';
import { msg } from '../../messages/messages';
import {
  useInactivateTeacher,
  useReactivateTeacher,
  useTeacher,
  useUpdateTeacher,
  type TeacherWithCode,
} from '../../services/admin/teachers';

/** A08 — Professor: editar, inativar/reativar (a reativação mostra o código novo uma única vez). */
export function TeacherDetailScreen() {
  const { teacherId = '' } = useParams();
  const teacher = useTeacher(teacherId);
  const update = useUpdateTeacher(teacherId);
  const inactivate = useInactivateTeacher(teacherId);
  const reactivate = useReactivateTeacher(teacherId);
  const [saved, setSaved] = useState(false);
  const [confirmInactivate, setConfirmInactivate] = useState(false);
  const [issued, setIssued] = useState<TeacherWithCode | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const data = teacher.data;
  const active = data?.active;
  const firstRender = useRef(true);

  // a ação de situação troca os botões: devolve o foco ao título para não o perder
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    heading.current?.focus();
  }, [active]);

  if (teacher.isPending) return <LoadingState label="Carregando professor" />;
  if (teacher.isError || !data) {
    return (
      <div className="page page--narrow">
        <ErrorAlert error={teacher.error} onRetry={() => void teacher.refetch()} />
        <Link to="/admin/teachers" className="button button--secondary">
          Voltar para os professores
        </Link>
      </div>
    );
  }

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Professor</div>
        <h1 className="page-title" ref={heading} tabIndex={-1}>
          {data.name}
        </h1>
        <div className="admin-list__meta">
          <StatusBadge active={data.active} />
          <span className="admin-list__meta-item">
            <Icon name={data.telegramLinked ? 'link' : 'link_off'} size="small" />
            {data.telegramLinked ? 'Telegram vinculado' : 'Telegram não vinculado'}
          </span>
        </div>
      </div>

      {issued ? (
        <OneTimeSecretPanel
          kind="access-code"
          secret={issued.accessCode}
          recipientName={issued.teacher.name}
          validUntil={issued.validUntil}
          onAcknowledge={() => setIssued(null)}
        />
      ) : null}

      <section className="admin-section" aria-labelledby="teacher-data">
        <h2 id="teacher-data" className="card-title">
          Dados
        </h2>
        <TeacherForm
          key={`${data.name}|${data.email}`}
          initial={{ name: data.name, email: data.email }}
          submitLabel="Salvar"
          submitting={update.isPending}
          error={update.error}
          onSubmit={(values) => {
            setSaved(false);
            update.mutate(values, { onSuccess: () => setSaved(true) });
          }}
        />
        <div role="status" className="support-text">
          {saved ? 'Dados salvos.' : ''}
        </div>
      </section>

      <section className="admin-section" aria-labelledby="teacher-access">
        <h2 id="teacher-access" className="card-title">
          Acesso
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
                  onSuccess: (result) => {
                    setIssued(result);
                    reactivate.reset();
                  },
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
        title="Inativar professor"
        text={msg('ADMIN_CONFIRM_INACTIVATE_TEACHER', { name: data.name })}
        icon="person_off"
        danger
        confirmLabel="Inativar"
        loading={inactivate.isPending}
        error={inactivate.isError ? apiErrorText(inactivate.error) : undefined}
        onConfirm={() => inactivate.mutate(undefined, { onSuccess: () => setConfirmInactivate(false) })}
        onClose={() => {
          inactivate.reset();
          setConfirmInactivate(false);
        }}
      />
    </div>
  );
}
