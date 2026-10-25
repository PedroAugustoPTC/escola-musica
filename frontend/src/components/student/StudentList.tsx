import { Link } from 'react-router';
import { Icon } from '../Icon';
import { SituationBadge } from './SituationBadge';
import type { StudentFormVariant, StudentView } from './studentFormTypes';
import '../../styles/student-registry.css';

interface StudentListProps {
  students: StudentView[];
  /** Administrador vê os professores associados; professor nunca vê outros professores. */
  variant: StudentFormVariant;
  /** Rota do detalhe do aluno (`/my-students/:id` ou `/admin/students/:id`). */
  detailPath: (studentId: string) => string;
  /** Nome acessível da lista. */
  label?: string;
}

function description(student: StudentView): string {
  const parts: string[] = [];
  if (student.age !== undefined) parts.push(`${student.age} anos`);
  if (student.instrument) parts.push(student.instrument);
  return parts.join(' · ');
}

/** Lista de alunos em cartões empilhados (mobile-first), compartilhada por A10 e P01. */
export function StudentList({ students, variant, detailPath, label }: StudentListProps) {
  return (
    <ul className="student-registry" aria-label={label}>
      {students.map((student) => {
        const teachers = student.teachers ?? [];
        return (
          <li key={student.id} className="student-registry__item">
            <div className="student-registry__main">
              <Link className="student-registry__name" to={detailPath(student.id)}>
                {student.name}
              </Link>
              {description(student) ? <div className="support-text">{description(student)}</div> : null}
              {variant === 'admin' ? (
                <div className="support-text">
                  {teachers.length > 0
                    ? `Professores: ${teachers
                        .map((teacher) => (teacher.active ? teacher.name : `${teacher.name} (inativo)`))
                        .join(', ')}`
                    : 'Sem professor associado'}
                </div>
              ) : null}
            </div>
            <SituationBadge active={student.active} />
            <Icon name="chevron_right" className="student-registry__chevron" />
          </li>
        );
      })}
    </ul>
  );
}
