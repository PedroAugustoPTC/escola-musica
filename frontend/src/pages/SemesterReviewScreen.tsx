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
import { SemesterSections } from '../components/SemesterSections';
import { PdfViewer } from '../components/PdfViewer';
import { msg } from '../messages/messages';
import { ApiError } from '../services/apiClient';
import { useHome } from '../services/home';
import type { SelectedAudio } from '../services/lessonReports';
import {
  semesterPdfPath,
  semesterReportKey,
  semesterSections,
  semester,
  useSemesterReport,
} from '../services/semester';
import { useJobPolling } from '../services/jobs';
import { resultNotice } from './ReviewScreen';
import type { NavigationResult } from './ProcessingScreen';

type Notice = NonNullable<ReturnType<typeof resultNotice>>;

/**
 * Tela 11 — Conferência semestral: os mesmos estados da 08 (PDF sendo gerado, aplicando alteração,
 * versão desatualizada), mais "N relatórios de aula foram considerados…", o aviso de relatórios em
 * aberto e as Informações Gerais somente leitura.
 */
export function SemesterReviewScreen() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const local = useLocation();
  const client = useQueryClient();
  const mobile = useIsMobile();
  const report = useSemesterReport(reportId);
  const start = useHome();
  const adjustmentId = useId();
  const initialState = local.state as NavigationResult | null;

  const [tab, setTab] = useState<'text' | 'pdf'>('text');
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
  const notice =
    currentNotice !== undefined
      ? currentNotice
      : data && initialState?.result
        ? resultNotice(initialState.result, initialState.understoodText, data.currentVersion)
        : null;

  useEffect(() => {
    if (!data || activeJob) return;
    if (data.status === 'APPROVED') {
      navigate(`/semester-reports/${data.reportId}/approved`, { replace: true });
    } else if (data.status === 'CANCELLED') {
      navigate('/', { replace: true });
    }
  }, [data, activeJob, navigate]);

  const completed = polling.finalized ? polling.data : undefined;
  useEffect(() => {
    if (!completed || !activeJob || !reportId) return;
    void (async () => {
      const updated = await client.fetchQuery({
        queryKey: semesterReportKey(reportId),
        queryFn: () => semester.get(reportId),
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
    kicker: 'Relatório semestral',
    title: data?.generalInformation.name ?? '',
    backTo: '/',
  };

  if (report.isPending) {
    return (
      <Layout activeNav="students" context={context}>
        <LoadingState label="Carregando o relatório semestral" />
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
        ? await semester.changeAudio(reportId, audio, version)
        : await semester.changeText(reportId, adjustment.trim(), version);
      setChangeJob(jobId);
      setAdjustment('');
      setAudio(null);
      setRecorderKey((c) => c + 1);
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
        const approval = await semester.approve(reportId, version);
        await client.invalidateQueries({ queryKey: semesterReportKey(reportId) });
        navigate(`/semester-reports/${reportId}/approved`, { replace: true, state: approval });
      } else {
        await semester.cancel(reportId);
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
          <div className="kicker">Relatório semestral · {data.generalInformation.periodLabel}</div>
          <h1 className="page-title">{msg('REVIEW_TITLE')}</h1>
          <div className="review__chips">
            <span className="chip chip--notice">
              <Icon name="schedule" size="small" />
              {msg('STATUS_AWAITING_REVIEW')}
            </span>
            <span className="chip chip--outline">{msg('REVIEW_VERSION', { version })}</span>
          </div>
        </div>
        <div className="consolidation">
          <span className="consolidation__number">{data.generalInformation.consideredLessonCount}</span>{' '}
          <span>{msg('CONSIDERED_LESSONS', { quantity: '' }).trim()}</span>
        </div>
        {data.openReportCount > 0 ? (
          <div role="status" className="alert alert--notice">
            <Icon name="warning" />
            <div className="alert__text">{msg('OPEN_REPORTS_EXIST', { quantity: data.openReportCount })}</div>
          </div>
        ) : null}
        {stale ? (
          <div role="alert" className="alert alert--notice">
            <Icon name="sync_problem" />
            <div className="alert__text">
              <strong>Esta versão está desatualizada.</strong> O relatório foi alterado em outro lugar.
              Recarregue para conferir a versão atual antes de aprovar.
            </div>
            <Button
              variant="notice"
              size="compact"
              onClick={() => {
                setStale(false);
                void report.refetch();
              }}
            >
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
              aria-selected={tab === 'text'}
              className="tabs__aba"
              onClick={() => setTab('text')}
            >
              Texto
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'pdf'}
              className="tabs__aba"
              onClick={() => setTab('pdf')}
            >
              Ver PDF
            </button>
          </div>
        ) : null}
        <div className={`review${mobile ? '' : ' review--side-by-side'}`}>
          {showText ? (
            <div className="review__text">
              <SemesterSections
                sections={semesterSections(data.generalInformation, data.content)}
                faded={applying}
              />
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
                  placeholder="Ex.: incluir no parecer final a participação no recital de junho."
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
              {adjustmentError ? <ErrorAlert error={adjustmentError} /> : null}
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
            <PdfViewer
              path={semesterPdfPath(data.reportId, version)}
              version={version}
              available={Boolean(data.pdfAvailable) && !applying}
              fileName={`relatorio-semestral-${data.periodKey}-v${version}.pdf`}
            />
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
