import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { Button } from '../components/Button';
import { LoadingState } from '../components/LoadingState';
import { ErrorAlert, ErrorState } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { ProcessingSteps } from '../components/ProcessingSteps';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { lessonReports } from '../services/lessonReports';
import { useJobPolling, type JobDto } from '../services/jobs';

/** Estado passado à próxima tela para mostrar o resultado (texto entendido, código). */
export interface NavigationResult {
  result: string | null;
  understoodText: string | null;
}

/** Para onde levar o professor quando o trabalho termina com sucesso. */
export function resultDestination(job: JobDto): string | null {
  if (!job.reportId) return null;
  const base =
    job.reportType === 'SEMESTER' ? `/semester-reports/${job.reportId}` : `/lesson-reports/${job.reportId}`;
  switch (job.resultCode) {
    case 'PENDING_QUESTIONS':
    case 'ANSWER_FAILED':
      return `${base}/questions`;
    case 'STRUCTURING_FAILED':
    case 'CONSOLIDATION_FAILED':
    case 'AUDIO_NOT_PROCESSABLE':
      return null;
    default:
      return base;
  }
}

function stepTitle(job: JobDto): string {
  const step = msg(`STEP_${job.step}`);
  return `${step}…`;
}

/** Tela 06 — Processando (06b enviando, 06c falha, aviso de demora após 3 min). */
export function ProcessingScreen() {
  const { jobId } = useParams();
  const navigate = useNavigate();
  const local = useLocation();
  const studentId = (local.state as { studentId?: string } | null)?.studentId;
  const job = useJobPolling(jobId);
  const [action, setAction] = useState<'retry' | 'cancel' | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);

  const data = job.data;

  useEffect(() => {
    if (!data || !job.finalized) return;
    if (data.status === 'DISCARDED') {
      navigate('/', { replace: true });
      return;
    }
    const destination = resultDestination(data);
    if (destination) {
      const state: NavigationResult = {
        result: data.resultCode ?? null,
        understoodText: data.understoodText ?? null,
      };
      navigate(destination, { replace: true, state });
    }
  }, [data, job.finalized, navigate]);

  const context = { kicker: 'Relatório de aula', title: 'Processando', backTo: '/' };

  if (job.isPending) {
    return (
      <Layout activeNav="students" context={context}>
        <LoadingState label="Consultando o processamento" />
      </Layout>
    );
  }
  if (job.isError) {
    return (
      <Layout activeNav="students" context={context}>
        <ErrorState error={job.error} onRetry={() => void job.refetch()} />
      </Layout>
    );
  }
  if (!data) return null;

  const failed =
    job.finalized &&
    (data.resultCode === 'STRUCTURING_FAILED' || data.resultCode === 'AUDIO_NOT_PROCESSABLE');

  const tryAgain = async () => {
    if (!data.reportId) return;
    setAction('retry');
    setActionError(null);
    try {
      const { jobId: nextValue } = await lessonReports.newAttempt(data.reportId);
      navigate(`/jobs/${nextValue}`, { replace: true, state: { studentId } });
    } catch (failure) {
      setActionError(failure);
    } finally {
      setAction(null);
    }
  };

  const cancel = async () => {
    if (!data.reportId) return;
    setAction('cancel');
    setActionError(null);
    try {
      await lessonReports.cancel(data.reportId);
      navigate('/', { replace: true });
    } catch (failure) {
      setActionError(failure);
      setAction(null);
    }
  };

  if (failed) {
    const noReport = data.resultCode === 'AUDIO_NOT_PROCESSABLE';
    return (
      <Layout activeNav="students" context={context}>
        <div className="page page--center">
          <div role="alert" className="card card--failure">
            <span className="icon-circle icon-circle--error">
              <Icon name="error" />
            </span>
            <div>
              <h1 className="page-title page-title--smaller">
                {noReport ? 'Não foi possível entender o áudio.' : 'Não foi possível processar o áudio.'}
              </h1>
              <p className="subtitle">
                {noReport ? msg('AUDIO_NOT_PROCESSABLE') : msg('PROCESSING_FAILURE_OPTIONS')}
              </p>
            </div>
            {actionError ? <ErrorAlert error={actionError} /> : null}
            <div className="actions-stack">
              {noReport ? (
                <Button
                  size="large"
                  icon="mic"
                  onClick={() =>
                    navigate(studentId ? `/students/${studentId}/new-lesson` : '/students?register=1')
                  }
                >
                  Gravar novamente
                </Button>
              ) : (
                <>
                  <Button
                    size="large"
                    icon="refresh"
                    loading={action === 'retry'}
                    disabled={action !== null}
                    onClick={() => void tryAgain()}
                  >
                    {msg('RETRY_BUTTON')}
                  </Button>
                  <Button
                    variant="link-danger"
                    loading={action === 'cancel'}
                    disabled={action !== null}
                    onClick={() => void cancel()}
                  >
                    {msg('CANCEL_REPORT_BUTTON')}
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout activeNav="students" context={context}>
      <div className="page page--center">
        <div aria-live="polite">
          <div className="kicker">
            {data.type === 'LESSON_REGISTRATION' ? 'Novo relatório de aula' : 'Relatório de aula'}
          </div>
          <h1 className="page-title">{stepTitle(data)}</h1>
          <p className="subtitle">
            Isso pode levar alguns segundos. Mantenha esta tela aberta — avisaremos assim que o relatório
            estiver pronto.
          </p>
        </div>
        <ProcessingSteps job={data} />
        {data.delayed || job.limitExceeded ? (
          <div role="status" className="alert alert--notice">
            <Icon name="hourglass_top" />
            <div className="alert__text">{msg('PROCESSING_DELAYED')}</div>
            <Button variant="notice" size="compact" onClick={() => navigate('/')}>
              Voltar ao Painel
            </Button>
          </div>
        ) : null}
      </div>
    </Layout>
  );
}
