import { useEffect, useId, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate, useParams } from 'react-router';
import { useIsMobile } from '../app/useIsMobile';
import { Button } from '../components/Button';
import { ConfirmationDialog } from '../components/ConfirmationDialog';
import { LoadingState } from '../components/LoadingState';
import { ErrorAlert, ErrorState, errorText } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { AudioRecorder } from '../components/AudioRecorder';
import { Icon } from '../components/Icon';
import { ReportSections } from '../components/ReportSections';
import { PdfViewer } from '../components/PdfViewer';
import { msg } from '../messages/messages';
import { ApiError } from '../services/apiClient';
import { formatDate } from '../services/formatting';
import { useHome } from '../services/home';
import {
  reviewPdfPath,
  lessonReportKey,
  lessonReports,
  useLessonReport,
  type SelectedAudio,
} from '../services/lessonReports';
import { useJobPolling } from '../services/jobs';
import { stepRoute } from './QuestionsScreen';
import type { NavigationResult } from './ProcessingScreen';

type Tab = 'text' | 'pdf';

interface Notice {
  type: 'ok' | 'info' | 'error';
  rows: string[];
}

/** Mensagem mostrada depois de um processamento (contracts/mensagens.md). */
export function resultNotice(
  result: string | null,
  understoodText: string | null,
  version: number,
): Notice | null {
  const understood = understoodText ? [msg('UNDERSTOOD_TEXT', { text: understoodText })] : [];
  switch (result) {
    case 'NEW_VERSION':
      return { type: 'ok', rows: [msg('CHANGE_APPLIED', { version }), ...understood] };
    case 'NO_CONTENT_CHANGE':
    case 'ACTION_REQUEST_IGNORED':
      return { type: 'info', rows: [msg(result, { version }), ...understood] };
    case 'CHANGE_FAILED':
      return { type: 'error', rows: [msg('CHANGE_FAILED', { version }), ...understood] };
    case 'READY_FOR_REVIEW':
      return understoodText
        ? { type: 'ok', rows: [msg('ANSWER_INCORPORATED', { text: understoodText })] }
        : null;
    default:
      return null;
  }
}

/**
 * Tela 08 — Conferência (08b PDF sendo gerado, 08c aplicando alteração, 08d versão desatualizada).
 * Abas "Texto"/"Ver PDF" no celular e lado a lado no computador; a aprovação cita a versão
 * apresentada e nada navega sem resposta 2xx (FR-053).
 */
export function ReviewScreen() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const local = useLocation();
  const client = useQueryClient();
  const mobile = useIsMobile();
  const report = useLessonReport(reportId);
  const start = useHome();
  const adjustmentId = useId();
  const initialState = local.state as NavigationResult | null;

  const [tab, setTab] = useState<Tab>('text');
  const [adjustment, setAdjustment] = useState('');
  const [audio, setAudio] = useState<SelectedAudio | null>(null);
  const [recorderKey, setRecorderKey] = useState(0);
  const [changeJob, setChangeJob] = useState<string | null>(null);
  const [uploadingAdjustment, setUploadingAdjustment] = useState(false);
  const [adjustmentError, setAdjustmentError] = useState<unknown>(null);
  const [stale, setStale] = useState(false);
  const [currentNotice, setNotice] = useState<Notice | null | undefined>(undefined);
  const [dialog, setDialog] = useState<'approve' | 'cancel' | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [dialogError, setDialogError] = useState<unknown>(null);

  const data = report.data;
  const activeJob = changeJob ?? data?.activeJobId ?? null;
  const polling = useJobPolling(activeJob ?? undefined);

  // até haver um aviso desta tela, vale o resultado trazido do processamento (respostas incorporadas)
  const notice =
    currentNotice !== undefined
      ? currentNotice
      : data && initialState?.result
        ? resultNotice(initialState.result, initialState.understoodText, data.currentVersion)
        : null;

  // relatório que não está em conferência vai para a tela da etapa
  useEffect(() => {
    if (!data || activeJob) return;
    if (data.status !== 'AWAITING_REVIEW') {
      navigate(stepRoute(data), { replace: true });
    }
  }, [data, activeJob, navigate]);

  // alteração concluída: recarrega e mostra o resultado (ALTERACAO_APLICADA + TEXTO_ENTENDIDO etc.)
  const completed = polling.finalized ? polling.data : undefined;
  useEffect(() => {
    if (!completed || !activeJob) return;
    void (async () => {
      const updated = await client.fetchQuery({
        queryKey: lessonReportKey(reportId),
        queryFn: () => lessonReports.get(reportId!),
        staleTime: 0,
      });
      setNotice(
        resultNotice(completed.resultCode ?? null, completed.understoodText ?? null, updated.currentVersion),
      );
      setChangeJob(null);
      setStale(false);
    })();
  }, [completed, activeJob, client, reportId]);

  const context = {
    kicker: 'Relatório de aula',
    title: data?.studentName ?? '',
    backTo: '/',
  };

  if (report.isPending) {
    return (
      <Layout activeNav="students" context={context}>
        <LoadingState label="Carregando o relatório" />
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

  const applying = activeJob !== null;
  const version = data.currentVersion;
  const canAdjust = !applying && !uploadingAdjustment && (adjustment.trim() !== '' || audio !== null);

  const reload = async () => {
    setStale(false);
    await report.refetch();
  };

  const handleConflict = (failure: unknown): boolean => {
    if (failure instanceof ApiError && failure.code === 'STALE_VERSION') {
      setStale(true);
      void report.refetch();
      return true;
    }
    return false;
  };

  const requestChange = async () => {
    if (!reportId || !canAdjust) return;
    setUploadingAdjustment(true);
    setAdjustmentError(null);
    setNotice(null);
    try {
      const { jobId } = audio
        ? await lessonReports.changeAudio(reportId, audio, version)
        : await lessonReports.changeText(reportId, adjustment.trim(), version);
      setChangeJob(jobId);
      setAdjustment('');
      setAudio(null);
      setRecorderKey((key) => key + 1);
    } catch (failure) {
      if (!handleConflict(failure)) setAdjustmentError(failure);
    } finally {
      setUploadingAdjustment(false);
    }
  };

  const confirm = async () => {
    if (!reportId || !dialog) return;
    setConfirming(true);
    setDialogError(null);
    try {
      if (dialog === 'approve') {
        const approval = await lessonReports.approve(reportId, version);
        await client.invalidateQueries({ queryKey: lessonReportKey(reportId) });
        navigate(`/lesson-reports/${reportId}/approved`, { replace: true, state: approval });
      } else {
        await lessonReports.cancel(reportId);
        navigate('/', { replace: true });
      }
    } catch (failure) {
      if (handleConflict(failure)) {
        setDialog(null);
      } else {
        setDialogError(failure);
      }
      setConfirming(false);
    }
  };

  const showText = !mobile || tab === 'text';
  const showPdf = !mobile || tab === 'pdf';

  return (
    <Layout
      activeNav="students"
      context={context}
      actionsBar={
        <>
          <Button variant="link-danger" disabled={applying} onClick={() => setDialog('cancel')}>
            {msg('CANCEL_REPORT_BUTTON')}
          </Button>
          <Button
            size="large"
            icon="verified"
            disabled={applying || stale}
            onClick={() => setDialog('approve')}
          >
            {msg('APPROVE_BUTTON')} · versão {version}
          </Button>
        </>
      }
    >
      <div className="page page--review">
        <div>
          <div className="kicker">Relatório de aula</div>
          <h1 className="page-title">{msg('REVIEW_TITLE')}</h1>
          <div className="review__chips">
            <span className="chip chip--notice">
              <Icon name="schedule" size="small" />
              {msg('STATUS_AWAITING_REVIEW')}
            </span>
            <span className="chip chip--outline">{msg('REVIEW_VERSION', { version })}</span>
          </div>
        </div>
        <dl className="report-data">
          <div>
            <dt>ALUNO</dt>
            <dd>{data.studentName}</dd>
          </div>
          <div>
            <dt>PROFESSOR</dt>
            <dd>{data.teacherName}</dd>
          </div>
          <div>
            <dt>DATA DA AULA</dt>
            <dd>{formatDate(data.lessonDate)}</dd>
          </div>
        </dl>
        {stale ? (
          <div role="alert" className="alert alert--notice">
            <Icon name="sync_problem" />
            <div className="alert__text">
              <strong>Esta versão está desatualizada.</strong> O relatório foi alterado em outro lugar.
              Recarregue para conferir a versão atual antes de aprovar.
            </div>
            <Button variant="notice" size="compact" onClick={() => void reload()}>
              Recarregar
            </Button>
          </div>
        ) : null}
        {applying ? (
          <div role="status" className="alert alert--info">
            <span className="spinner" aria-hidden="true" />
            <div className="alert__text">
              <strong>{msg('CHANGE_BEING_APPLIED')}</strong> Uma nova versão será gerada em instantes.
            </div>
          </div>
        ) : null}
        {notice ? (
          <div role="status" className={`alert alert--${notice.type}`}>
            <Icon name={notice.type === 'error' ? 'error' : notice.type === 'ok' ? 'check_circle' : 'info'} />
            <div className="alert__text">
              {notice.rows.map((line) => (
                <div key={line}>{line}</div>
              ))}
            </div>
          </div>
        ) : null}
        {mobile ? (
          <div role="tablist" aria-label="Formato" className="tabs">
            <button
              type="button"
              role="tab"
              id="text-tab"
              aria-selected={tab === 'text'}
              aria-controls="text-panel"
              className="tabs__tab"
              onClick={() => setTab('text')}
            >
              Texto
            </button>
            <button
              type="button"
              role="tab"
              id="pdf-tab"
              aria-selected={tab === 'pdf'}
              aria-controls="pdf-panel"
              className="tabs__tab"
              onClick={() => setTab('pdf')}
            >
              Ver PDF
            </button>
          </div>
        ) : null}
        <div className={`review${mobile ? '' : ' review--side-by-side'}`}>
          {showText ? (
            <div
              id="text-panel"
              role={mobile ? 'tabpanel' : undefined}
              aria-labelledby={mobile ? 'text-tab' : undefined}
              className="review__text"
            >
              <ReportSections content={data.content} faded={applying} />
              <div className="field">
                <label htmlFor={adjustmentId} className="field__label">
                  {msg('CHANGE_REQUEST_LABEL')}
                </label>
                <textarea
                  id={adjustmentId}
                  className="text-area"
                  rows={3}
                  maxLength={4000}
                  value={adjustment}
                  disabled={applying || audio !== null}
                  placeholder="Ex.: o aluno teve mais facilidade em leitura do que consta nas dificuldades."
                  onChange={(event) => setAdjustment(event.target.value)}
                />
              </div>
              <AudioRecorder
                key={recorderKey}
                compact
                limitMb={start.data?.audioLimitMb ?? 50}
                onChange={setAudio}
                disabled={applying || uploadingAdjustment}
              />
              {adjustmentError ? <ErrorAlert error={adjustmentError} consentStudentId={data.studentId} /> : null}
              <Button
                variant="secondary"
                icon="edit"
                className="review__adjust"
                disabled={!canAdjust}
                loading={uploadingAdjustment}
                onClick={() => void requestChange()}
              >
                {msg('REQUEST_CHANGE_BUTTON')}
              </Button>
            </div>
          ) : null}
          {showPdf ? (
            <div
              id="pdf-panel"
              role={mobile ? 'tabpanel' : undefined}
              aria-labelledby={mobile ? 'pdf-tab' : undefined}
            >
              <PdfViewer
                path={reviewPdfPath(data.reportId, version)}
                version={version}
                available={Boolean(data.pdfAvailable) && !applying}
                fileName={`relatorio-aula-${data.lessonDate}-v${version}.pdf`}
              />
            </div>
          ) : null}
        </div>
      </div>
      <ConfirmationDialog
        open={dialog === 'approve'}
        icon="verified"
        title={`Aprovar a versão ${version}?`}
        text={msg('CONFIRM_APPROVAL', { version })}
        confirmLabel={msg('CONFIRM_APPROVAL_BUTTON', { version })}
        loading={confirming}
        error={dialogError ? errorText(dialogError) : undefined}
        onConfirm={() => void confirm()}
        onClose={() => {
          setDialog(null);
          setDialogError(null);
        }}
      />
      <ConfirmationDialog
        open={dialog === 'cancel'}
        danger
        icon="block"
        title="Cancelar este relatório?"
        text={msg('CONFIRM_CANCELLATION')}
        confirmLabel={msg('CONFIRM_CANCELLATION_BUTTON')}
        loading={confirming}
        error={dialogError ? errorText(dialogError) : undefined}
        onConfirm={() => void confirm()}
        onClose={() => {
          setDialog(null);
          setDialogError(null);
        }}
      />
    </Layout>
  );
}
