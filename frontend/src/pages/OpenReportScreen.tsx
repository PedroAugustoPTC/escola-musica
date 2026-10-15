import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Navigate, useNavigate, useParams } from 'react-router';
import { Button } from '../components/Button';
import { ConfirmationDialog } from '../components/ConfirmationDialog';
import { LoadingState } from '../components/LoadingState';
import { ErrorState, errorText } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { api } from '../services/apiClient';
import { instantDate, instantTime } from '../services/formatting';
import type { OpenReportDto } from '../services/home';
import { lessonReports } from '../services/lessonReports';

/** Para onde "Continuar de onde parei" leva, conforme o status e a etapa (US3). */
export function continuationRoute(open: OpenReportDto): string {
  if (!open.reportId) {
    return open.jobId ? `/jobs/${open.jobId}` : '/';
  }
  if (open.type === 'SEMESTER') return `/semester-reports/${open.reportId}`;
  if (open.status === 'AWAITING_ANSWERS') return `/lesson-reports/${open.reportId}/questions`;
  return `/lesson-reports/${open.reportId}`;
}

/** Tela 04 — Relatório em aberto (04b confirmação de descarte), FR-034. */
export function OpenReportScreen() {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const [confirm, setConfirm] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const query = useQuery<OpenReportDto | null>({
    queryKey: ['open-report', studentId],
    queryFn: async ({ signal }) =>
      (await api.get<OpenReportDto | undefined>(`/students/${studentId}/open-report`, signal)) ?? null,
    gcTime: 0,
    enabled: Boolean(studentId),
  });
  const context = { kicker: 'Relatório de aula', title: 'Em aberto', backTo: '/students' };

  if (query.isPending) {
    return (
      <Layout activeNav="students" context={context}>
        <LoadingState label="Consultando o relatório em aberto" />
      </Layout>
    );
  }
  if (query.isError) {
    return (
      <Layout activeNav="students" context={context}>
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      </Layout>
    );
  }
  const open = query.data;
  if (!open) {
    // nada em aberto (204): segue direto para a gravação
    return <Navigate to={`/students/${studentId}/new-lesson`} replace />;
  }

  const channel = msg(open.originChannel === 'TELEGRAM' ? 'TELEGRAM_ORIGIN_CHANNEL' : 'WEB_ORIGIN_CHANNEL');
  const when = `${instantDate(open.updatedAt)} às ${instantTime(open.updatedAt)}`;

  const discard = async () => {
    setDiscarding(true);
    setError(null);
    try {
      if (open.reportId) {
        await lessonReports.cancel(open.reportId);
      } else if (open.jobId) {
        await api.post<void>(`/jobs/${open.jobId}/discard`);
      }
      navigate(`/students/${studentId}/new-lesson`, { replace: true });
    } catch (failure) {
      setError(failure);
      setDiscarding(false);
    }
  };

  return (
    <Layout activeNav="students" context={context}>
      <div className="page page--narrow">
        <div className="card card--resumption">
          <span className="icon-circle icon-circle--notice">
            <Icon name="edit_note" />
          </span>
          <div>
            <div className="kicker">{open.studentName}</div>
            <h1 className="page-title page-title--smaller">Você tem um relatório em aberto</h1>
            <p className="subtitle">
              Este aluno já tem um relatório iniciado {channel} e ainda não concluído (última alteração em{' '}
              {when}).
            </p>
          </div>
          <div className="actions-stack">
            <Button size="large" onClick={() => navigate(continuationRoute(open))}>
              {msg('CONTINUE_BUTTON')}
            </Button>
            <Button variant="danger" size="large" onClick={() => setConfirm(true)}>
              {msg('DISCARD_BUTTON')}
            </Button>
          </div>
        </div>
      </div>
      <ConfirmationDialog
        open={confirm}
        danger
        icon="delete"
        title="Descartar o relatório em aberto?"
        text={msg('CONFIRM_DISCARD')}
        confirmLabel={msg('CONFIRM_DISCARD_BUTTON')}
        loading={discarding}
        error={error ? errorText(error) : undefined}
        onConfirm={() => void discard()}
        onClose={() => {
          setConfirm(false);
          setError(null);
        }}
      />
    </Layout>
  );
}
