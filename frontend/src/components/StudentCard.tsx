import { useNavigate } from 'react-router';
import { msg } from '../messages/messages';
import type { StudentSummaryDto } from '../services/students';
import { formatDate } from '../services/formatting';
import { Button } from './Button';
import { Icon } from './Icon';

export function studentDescription(student: StudentSummaryDto): string {
  const parts: string[] = [];
  if (student.instrument) parts.push(student.instrument);
  if (student.lastLessonAt) parts.push(msg('LAST_LESSON', { data: formatDate(student.lastLessonAt) }));
  return parts.join(' · ');
}

/** Rota de "Registrar aula": com relatório em aberto, a tela 04 decide continuar ou descartar. */
export function registerLessonRoute(student: StudentSummaryDto): string {
  return student.hasOpenReport ? `/students/${student.id}/open-report` : `/students/${student.id}/new-lesson`;
}

/** Cartão de aluno da tela 03 com as ações Registrar aula, Relatório semestral e Histórico. */
export function StudentCard({ student }: { student: StudentSummaryDto }) {
  const navigate = useNavigate();
  const description = studentDescription(student);
  return (
    <article className="card">
      <div className="student-card__top">
        <div className="student-card__identity">
          <h2 className="card-title">{student.name}</h2>
          {description ? <div className="support-text">{description}</div> : null}
        </div>
        {student.hasOpenReport ? (
          <span className="chip chip--notice">
            <Icon name="edit_note" size="small" />
            {msg('OPEN_REPORT_MARK')}
          </span>
        ) : null}
      </div>
      <div className="student-card__actions">
        <Button
          className="student-card__main"
          icon="mic"
          aria-label={`${msg('REGISTER_LESSON_BUTTON')} — ${student.name}`}
          onClick={() => navigate(registerLessonRoute(student))}
        >
          {msg('REGISTER_LESSON_BUTTON')}
        </Button>
        <Button
          variant="secondary"
          size="compact"
          aria-label={`${msg('SEMESTER_REPORT_BUTTON')} — ${student.name}`}
          onClick={() => navigate(`/students/${student.id}/semester`)}
        >
          {msg('SEMESTER_REPORT_BUTTON')}
        </Button>
        <Button
          variant="secondary"
          size="compact"
          aria-label={`${msg('HISTORY_BUTTON')} — ${student.name}`}
          onClick={() => navigate(`/students/${student.id}/history`)}
        >
          {msg('HISTORY_BUTTON')}
        </Button>
      </div>
    </article>
  );
}
