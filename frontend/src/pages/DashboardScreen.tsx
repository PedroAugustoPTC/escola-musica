import { useNavigate } from 'react-router';
import { studentDescription } from '../components/StudentCard';
import { initials } from '../components/Header';
import { Button } from '../components/Button';
import { LoadingState } from '../components/LoadingState';
import { ErrorAlert } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { Layout } from '../components/Layout';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { instantDate, instantTime, firstName } from '../services/formatting';
import { useHome, type OpenReportDto } from '../services/home';

/** Destino de "Continuar" para um relatório em aberto (US3). */
export function continueRoute(report: OpenReportDto): string {
  if (report.type === 'SEMESTER' && report.reportId) {
    return `/semester-reports/${report.reportId}`;
  }
  return `/students/${report.studentId}/open-report`;
}

function reportStatus(report: OpenReportDto): string {
  return report.status ? msg(`STATUS_${report.status}`) : msg('STATUS_PROCESSING');
}

/** Tela 02 — Painel (02b vazio, 02c menu, 02d modo escuro, relatórios em aberto com "Continuar"). */
export function DashboardScreen() {
  const start = useHome();
  const navigate = useNavigate();

  if (start.isPending) {
    return (
      <Layout activeNav="dashboard" bottomNav>
        <LoadingState label="Carregando o painel" />
      </Layout>
    );
  }
  if (start.isError) {
    return (
      <Layout activeNav="dashboard" bottomNav>
        <div className="page page--wide">
          <ErrorAlert error={start.error} onRetry={() => start.refetch()} />
        </div>
      </Layout>
    );
  }

  const data = start.data;
  const noStudents = data.totalStudents === 0;

  return (
    <Layout activeNav="dashboard" bottomNav>
      <div className="page page--wide">
        <div>
          <div className="kicker">Painel</div>
          <h1 className="page-title">{msg('GREETING', { name: firstName(data.teacher.name) })}</h1>
          <p className="subtitle dashboard__subtitle">{msg('HOME_SUBTITLE')}</p>
        </div>
        <div>
          <Button variant="secondary" icon="group" onClick={() => navigate('/my-students')}>
            {msg('HOME_MY_STUDENTS')}
          </Button>
        </div>
        {noStudents ? (
          <EmptyState
            monogram
            solid
            title="Nenhum aluno por aqui"
            text={msg('NO_STUDENTS')}
            action={
              <Button icon="person_add" onClick={() => navigate('/my-students/new')}>
                Cadastrar o primeiro aluno
              </Button>
            }
          />
        ) : (
          <>
            {data.openReports.map((report) => (
              <div
                key={`${report.reportId ?? report.jobId}`}
                role="status"
                className="alert alert--notice dashboard__open-report"
              >
                <Icon name="edit_note" />
                <div className="alert__text">
                  <strong>{msg('HOME_OPEN_REPORTS')}</strong>
                  <div>
                    {report.studentName} · {msg(`TYPE_${report.type}`)} · {reportStatus(report)} · última
                    alteração em {instantDate(report.updatedAt)} {instantTime(report.updatedAt)}
                  </div>
                </div>
                <Button
                  variant="notice"
                  size="compact"
                  aria-label={`${msg('CONTINUE_REPORT_BUTTON', {
                    student: report.studentName,
                    data: instantDate(report.updatedAt),
                  })}`}
                  onClick={() => navigate(continueRoute(report))}
                >
                  Retomar relatório
                </Button>
              </div>
            ))}
            <div className="dashboard__highlights">
              <button
                type="button"
                className="dashboard__register"
                onClick={() => navigate('/students?register=1')}
              >
                <span className="dashboard__register-icon">
                  <Icon name="mic" size="large" />
                </span>
                <span className="dashboard__register-text">
                  <span className="dashboard__register-title">{msg('REGISTER_LESSON_BUTTON')}</span>
                  <span className="dashboard__register-support">
                    Grave um áudio contando como foi a aula.
                  </span>
                </span>
              </button>
              <div className="card dashboard__total">
                <div className="section-label dashboard__total-label">Seus alunos</div>
                <div className="dashboard__total-number">
                  <span className="dashboard__total-value">{data.totalStudents}</span>
                  <span className="subtitle">alunos associados</span>
                </div>
                <Button variant="link" onClick={() => navigate('/students')}>
                  Ver todos os alunos
                </Button>
              </div>
            </div>
            <section aria-labelledby="h-students" className="dashboard__students">
              <h2 id="h-students" className="section-label">
                Primeiros alunos
              </h2>
              <div className="student-list">
                {data.students.map((student) => (
                  <button
                    key={student.id}
                    type="button"
                    className="student-list__item"
                    onClick={() => navigate(`/students/${student.id}/history`)}
                  >
                    <span className="initials initials--large" aria-hidden="true">
                      {initials(student.name)}
                    </span>
                    <span className="student-list__text">
                      <span className="student-list__name">{student.name}</span>
                      <span className="support-text">{studentDescription(student)}</span>
                    </span>
                    {student.hasOpenReport ? (
                      <span className="chip chip--notice">{msg('OPEN_REPORT_MARK')}</span>
                    ) : null}
                    <Icon name="chevron_right" />
                  </button>
                ))}
              </div>
            </section>
          </>
        )}
      </div>
    </Layout>
  );
}
