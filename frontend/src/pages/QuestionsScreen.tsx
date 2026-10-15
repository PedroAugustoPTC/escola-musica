import { useEffect, useId, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { Button } from '../components/Button';
import { ConfirmationDialog } from '../components/ConfirmationDialog';
import { LoadingState } from '../components/LoadingState';
import { ErrorAlert, ErrorState, errorText } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { AudioRecorder } from '../components/AudioRecorder';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { formatDate } from '../services/formatting';
import { useHome } from '../services/home';
import { lessonReports, useLessonReport, type SelectedAudio } from '../services/lessonReports';
import type { NavigationResult } from './ProcessingScreen';

/** Rota da etapa atual de um relatório de aula (usada quando a tela aberta não é a da etapa). */
export function stepRoute(report: { reportId: string; status: string; activeJobId?: string | null }): string {
  if (report.activeJobId) return `/jobs/${report.activeJobId}`;
  switch (report.status) {
    case 'AWAITING_ANSWERS':
      return `/lesson-reports/${report.reportId}/questions`;
    case 'APPROVED':
      return `/lesson-reports/${report.reportId}/approved`;
    case 'CANCELLED':
      return '/';
    default:
      return `/lesson-reports/${report.reportId}`;
  }
}

/** Tela 07 — Perguntas pendentes: resposta única por texto ou por áudio (FR-024, FR-024a). */
export function QuestionsScreen() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const local = useLocation();
  const result = (local.state as NavigationResult | null)?.result ?? null;
  const report = useLessonReport(reportId);
  const start = useHome();
  const responseId = useId();
  const [text, setText] = useState('');
  const [audio, setAudio] = useState<SelectedAudio | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [confirmCancellation, setConfirmCancellation] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancellationError, setCancellationError] = useState<unknown>(null);

  const data = report.data;
  useEffect(() => {
    if (data && (data.status !== 'AWAITING_ANSWERS' || data.activeJobId)) {
      navigate(stepRoute(data), { replace: true });
    }
  }, [data, navigate]);

  const context = {
    kicker: 'Relatório de aula',
    title: data?.studentName ?? '',
    backTo: '/',
  };

  if (report.isPending) {
    return (
      <Layout activeNav="students" context={context}>
        <LoadingState label="Carregando as perguntas" />
      </Layout>
    );
  }
  if (report.isError) {
    return (
      <Layout activeNav="students" context={context}>
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      </Layout>
    );
  }
  if (!data) return null;

  const send = async () => {
    if (!reportId) return;
    if (!audio && !text.trim()) {
      setLocalError(msg('EMPTY_ANSWER'));
      return;
    }
    setLocalError(null);
    setError(null);
    setUploading(true);
    try {
      const { jobId } = audio
        ? await lessonReports.answerAudio(reportId, audio)
        : await lessonReports.answerText(reportId, text.trim());
      navigate(`/jobs/${jobId}`);
    } catch (failure) {
      setError(failure);
      setUploading(false);
    }
  };

  const cancel = async () => {
    if (!reportId) return;
    setCancelling(true);
    setCancellationError(null);
    try {
      await lessonReports.cancel(reportId);
      navigate('/', { replace: true });
    } catch (failure) {
      setCancellationError(failure);
      setCancelling(false);
    }
  };

  return (
    <Layout
      activeNav="students"
      context={context}
      narrowBar
      actionsBar={
        <>
          <Button variant="link-danger" onClick={() => setConfirmCancellation(true)} disabled={uploading}>
            {msg('CANCEL_REPORT_BUTTON')}
          </Button>
          <Button size="large" icon="send" loading={uploading} onClick={() => void send()}>
            Enviar resposta
          </Button>
        </>
      }
    >
      <div className="page page--narrow">
        <div>
          <div className="kicker">Relatório de aula · {formatDate(data.lessonDate)}</div>
          <h1 className="page-title">Faltam algumas informações</h1>
          <p className="subtitle">
            Faltam algumas informações para concluir o relatório de {data.studentName}. Responda com suas
            palavras, tudo em uma única mensagem.
          </p>
        </div>
        {result === 'ANSWER_FAILED' ? (
          <div role="alert" className="alert alert--error">
            <Icon name="error" />
            <div className="alert__text">{msg('ANSWER_FAILED')}</div>
          </div>
        ) : null}
        <ol className="questions">
          {data.pendingQuestions.map((question, index) => (
            <li key={`${index}-${question}`} className="questions__item">
              <span className="questions__number">{index + 1}</span>
              <span>{question}</span>
            </li>
          ))}
        </ol>
        <div className="field">
          <label htmlFor={responseId} className="field__label">
            Sua resposta
          </label>
          <textarea
            id={responseId}
            className="text-area"
            rows={5}
            value={text}
            maxLength={4000}
            disabled={uploading || audio !== null}
            aria-describedby={`${responseId}-help`}
            aria-invalid={localError ? true : undefined}
            placeholder="Ex.: Ele estudou em casa três vezes. Para a próxima aula, pedi a escala de Sol maior."
            onChange={(event) => setText(event.target.value)}
          />
          <div id={`${responseId}-help`} className="field__help">
            Você pode responder a todas as perguntas de uma vez, digitando ou gravando.
          </div>
          {localError ? (
            <div role="alert" className="field__error">
              <Icon name="error" />
              {localError}
            </div>
          ) : null}
        </div>
        <AudioRecorder
          compact
          limitMb={start.data?.audioLimitMb ?? 50}
          onChange={setAudio}
          disabled={uploading}
        />
        {error ? <ErrorAlert error={error} /> : null}
      </div>
      <ConfirmationDialog
        open={confirmCancellation}
        danger
        icon="block"
        title="Cancelar este relatório?"
        text={msg('CONFIRM_CANCELLATION')}
        confirmLabel={msg('CONFIRM_CANCELLATION_BUTTON')}
        loading={cancelling}
        error={cancellationError ? errorText(cancellationError) : undefined}
        onConfirm={() => void cancel()}
        onClose={() => {
          setConfirmCancellation(false);
          setCancellationError(null);
        }}
      />
    </Layout>
  );
}
