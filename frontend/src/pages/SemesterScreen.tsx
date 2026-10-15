import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Button } from '../components/Button';
import { ConfirmationDialog } from '../components/ConfirmationDialog';
import { LoadingState } from '../components/LoadingState';
import { ErrorAlert, ErrorState, errorText } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { Layout } from '../components/Layout';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { useStudent } from '../services/students';
import { instantDate, formatDate, instantTime } from '../services/formatting';
import type { OpenReportDto } from '../services/home';
import { semester, useSemesterPreview, useSemesters, type SemesterDto } from '../services/semester';

function interval(semester: SemesterDto): string {
  return msg('SEMESTER_INTERVAL', {
    label: semester.label,
    start: formatDate(semester.start),
    end: formatDate(semester.end),
  });
}

function openReportText(open: OpenReportDto): string {
  return msg('REPORT_ALREADY_OPEN', {
    originChannel: msg(open.originChannel === 'TELEGRAM' ? 'TELEGRAM_ORIGIN_CHANNEL' : 'WEB_ORIGIN_CHANNEL'),
    data: instantDate(open.updatedAt),
    hora: instantTime(open.updatedAt),
  });
}

/**
 * Tela 10 — Semestral: período (o mais recente pré-selecionado) e prévia (10b sem aulas aprovadas,
 * 10c nenhum semestre, aviso de relatórios de aula em aberto, já aprovado, semestral em aberto).
 */
export function SemesterScreen() {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const student = useStudent(studentId);
  const semesters = useSemesters(studentId);
  const [chosen, setChosen] = useState<string | null>(null);
  const key = chosen ?? semesters.data?.find((s) => s.preselected)?.key ?? null;
  const preview = useSemesterPreview(studentId, key);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [discard, setDiscard] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  const [discardError, setDiscardError] = useState<unknown>(null);

  const context = {
    kicker: 'Relatório semestral',
    title: student.data?.name ?? '',
    backTo: '/students',
  };

  if (student.isPending || semesters.isPending) {
    return (
      <Layout activeNav="students" context={context}>
        <LoadingState label="Carregando os semestres" />
      </Layout>
    );
  }
  if (student.isError || semesters.isError) {
    return (
      <Layout activeNav="students" context={context}>
        <ErrorState error={student.error ?? semesters.error} onRetry={() => void semesters.refetch()} />
      </Layout>
    );
  }

  const list = semesters.data;
  const selected = list.find((s) => s.key === key) ?? null;
  const data = preview.data;
  const canGenerate =
    data !== undefined &&
    !data.approvedSemesterReportId &&
    !data.openSemesterReport &&
    data.approvedLessons > 0 &&
    data.openLessonReports === 0;

  const generate = async (despiteOpenReports: boolean) => {
    if (!studentId || !key) return;
    setGenerating(true);
    setError(null);
    try {
      const { jobId } = await semester.generate(studentId, key, despiteOpenReports);
      navigate(`/jobs/${jobId}`);
    } catch (failure) {
      setError(failure);
      setGenerating(false);
    }
  };

  const confirmDiscard = async () => {
    const open = data?.openSemesterReport;
    if (!open) return;
    setDiscarding(true);
    setDiscardError(null);
    try {
      if (open.reportId) {
        await semester.cancel(open.reportId);
      } else if (open.jobId) {
        await semester.discardConsolidation(open.jobId);
      }
      setDiscard(false);
      await preview.refetch();
    } catch (failure) {
      setDiscardError(failure);
    } finally {
      setDiscarding(false);
    }
  };

  return (
    <Layout
      activeNav="students"
      context={context}
      narrowBar
      actionsBar={
        list.length > 0 ? (
          <Button
            size="large"
            block
            icon="description"
            disabled={!canGenerate}
            loading={generating}
            onClick={() => void generate(false)}
          >
            {msg('GENERATE_SEMESTER_BUTTON')}
          </Button>
        ) : undefined
      }
    >
      <div className="page page--narrow">
        <div>
          <div className="kicker">Relatório semestral</div>
          <h1 className="page-title">{student.data.name}</h1>
          <p className="subtitle">
            Escolha o período. O relatório consolida todas as aulas aprovadas no semestre.
          </p>
        </div>
        {list.length === 0 ? (
          <EmptyState icon="event_busy" text={msg('NO_SEMESTERS')} />
        ) : (
          <>
            <fieldset className="periods">
              <legend className="section-label">Período</legend>
              <div role="radiogroup" aria-label="Período" className="periods__options">
                {list.map((semester) => (
                  <button
                    key={semester.key}
                    type="button"
                    role="radio"
                    aria-checked={semester.key === key}
                    className="periods__option"
                    onClick={() => {
                      setChosen(semester.key);
                      setError(null);
                    }}
                  >
                    <Icon name={semester.key === key ? 'check_circle' : 'calendar_month'} />
                    {semester.label}
                    {semester.inProgress ? (
                      <span className="chip chip--outline">{msg('SEMESTER_IN_PROGRESS')}</span>
                    ) : null}
                  </button>
                ))}
              </div>
              {selected ? <div className="field__help">{interval(selected)}</div> : null}
            </fieldset>
            {preview.isError ? (
              <ErrorAlert error={preview.error} onRetry={() => void preview.refetch()} />
            ) : !data ? (
              <LoadingState label="Consultando o período" />
            ) : data.approvedSemesterReportId ? (
              <div role="status" className="alert alert--info">
                <Icon name="verified" />
                <div className="alert__text">{msg('SEMESTER_REPORT_ALREADY_APPROVED')}</div>
                <Link className="button button--link" to={`/students/${studentId}/history`}>
                  {msg('HISTORY_BUTTON')}
                </Link>
              </div>
            ) : data.openSemesterReport ? (
              <div role="alert" className="alert alert--notice alert--column">
                <Icon name="edit_note" />
                <div className="alert__text">{openReportText(data.openSemesterReport)}</div>
                <div className="actions-stack">
                  <Button
                    variant="notice"
                    onClick={() =>
                      navigate(
                        data.openSemesterReport?.reportId
                          ? `/semester-reports/${data.openSemesterReport.reportId}`
                          : `/jobs/${data.openSemesterReport?.jobId}`,
                      )
                    }
                  >
                    {msg('CONTINUE_BUTTON')}
                  </Button>
                  <Button variant="danger" onClick={() => setDiscard(true)}>
                    {msg('DISCARD_BUTTON')}
                  </Button>
                </div>
              </div>
            ) : data.approvedLessons === 0 ? (
              <div role="alert" className="alert alert--error">
                <Icon name="error" />
                <div className="alert__text">{msg('NO_APPROVED_LESSONS_IN_PERIOD')}</div>
              </div>
            ) : (
              <>
                <div role="status" className="alert alert--info">
                  <Icon name="info" />
                  <div className="alert__text">
                    {msg('SEMESTER_PREVIEW', {
                      label: selected?.label ?? '',
                      quantity: data.approvedLessons,
                    })}
                  </div>
                </div>
                {data.openLessonReports > 0 ? (
                  <div role="alert" className="alert alert--notice alert--column">
                    <Icon name="warning" />
                    <div className="alert__text">
                      {msg('OPEN_REPORTS_EXIST', { quantity: data.openLessonReports })}
                    </div>
                    <div className="actions-stack">
                      <Button variant="notice" loading={generating} onClick={() => void generate(true)}>
                        {msg('GENERATE_ANYWAY_BUTTON')}
                      </Button>
                      <Button variant="secondary" onClick={() => navigate('/students')}>
                        {msg('BACK_BUTTON')}
                      </Button>
                    </div>
                  </div>
                ) : null}
              </>
            )}
            {error ? <ErrorAlert error={error} /> : null}
          </>
        )}
      </div>
      <ConfirmationDialog
        open={discard}
        danger
        icon="delete"
        title="Descartar o relatório em aberto?"
        text={msg('CONFIRM_DISCARD')}
        confirmLabel={msg('CONFIRM_DISCARD_BUTTON')}
        loading={discarding}
        error={discardError ? errorText(discardError) : undefined}
        onConfirm={() => void confirmDiscard()}
        onClose={() => {
          setDiscard(false);
          setDiscardError(null);
        }}
      />
    </Layout>
  );
}
