import { useNavigate, useParams } from 'react-router';
import { useTheme } from '../app/ThemeProvider';
import { Button } from '../components/Button';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { Layout } from '../components/Layout';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { instantDate, formatDate, instantTime } from '../services/formatting';
import { useLessonReport } from '../services/lessonReports';
import { useSemesterReport } from '../services/semester';

/** Tela 09 — Relatório aprovado (09b modo escuro): data e hora da aprovação e próximos passos. */
export function ApprovedScreen({ semester = false }: { semester?: boolean }) {
  const { reportId } = useParams();
  const lesson = useLessonReport(semester ? undefined : reportId);
  const semesterReport = useSemesterReport(semester ? reportId : undefined);
  const report = semester ? semesterReport : lesson;
  const navigate = useNavigate();
  const { goldInk } = useTheme();
  const context = {
    kicker: semester ? 'Relatório semestral' : 'Relatório de aula',
    title: 'Aprovado',
    backTo: '/',
  };

  if (report.isPending) {
    return (
      <Layout context={context}>
        <LoadingState label="Carregando o relatório" />
      </Layout>
    );
  }
  if (report.isError) {
    return (
      <Layout context={context}>
        <ErrorState error={report.error} />
      </Layout>
    );
  }

  const reportData = report.data;
  if (!reportData) return null;
  const studentName =
    'studentName' in reportData ? reportData.studentName : reportData.generalInformation.name;
  const approvedAt = reportData.approvedAt ?? reportData.updatedAt;
  const data = instantDate(approvedAt);
  const hora = instantTime(approvedAt);
  // como no protótipo: a aula pela data; o semestral pelo período
  const reference =
    'lessonDate' in reportData
      ? formatDate(reportData.lessonDate)
      : reportData.generalInformation.periodLabel;

  return (
    <Layout context={context}>
      <div className="page page--approved">
        <span className="icon-circle icon-circle--ok">
          <Icon name="check" size="large" />
        </span>
        <div className="kicker">
          {semester ? 'Relatório semestral' : 'Relatório de aula'} · {reference}
        </div>
        <h1 role="status" className="page-title page-title--larger">
          Relatório aprovado
        </h1>
        <img src={`/brand/fb-divider-${goldInk}.png`} alt="" className="approved__divider" />
        <p className="subtitle">{msg('REPORT_APPROVED', { data, hora })}</p>
        <div className="approved__data">
          <div>
            <div className="approved__label">ALUNO</div>
            <div className="approved__value">{studentName}</div>
          </div>
          <div>
            <div className="approved__label">APROVADO EM</div>
            <div className="approved__value">
              {data} {hora}
            </div>
          </div>
        </div>
        <div className="approved__actions">
          <Button size="large" block onClick={() => navigate('/students?register=1')}>
            {msg('REGISTER_OTHER_LESSON_BUTTON')}
          </Button>
          <div className="approved__secondary-actions">
            <Button
              variant="secondary"
              onClick={() => navigate(`/students/${reportData.studentId}/history`)}
            >
              Ver histórico
            </Button>
            <Button variant="secondary" onClick={() => navigate('/students')}>
              Voltar para alunos
            </Button>
          </div>
        </div>
      </div>
    </Layout>
  );
}
