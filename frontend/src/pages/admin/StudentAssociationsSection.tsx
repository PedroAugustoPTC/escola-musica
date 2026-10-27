import { useId, useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { Icon } from '../../components/Icon';
import { StatusBadge } from '../../components/admin/ui';
import { apiErrorText } from '../../components/admin/fieldErrors';
import { msg } from '../../messages/messages';
import {
  useAssignTeacher,
  usePreviewAssignmentRemoval,
  useRemoveTeacher,
} from '../../services/admin/assignments';
import type { AdminStudent } from '../../services/admin/students';
import { useActiveTeacherOptions } from '../../services/admin/teachers';

interface Removal {
  teacherId: string;
  teacherName: string;
  frozenReports: number;
}

/**
 * Professores associados ao aluno (A12): incluir professor ativo e retirar a associação. A retirada mostra
 * quantos relatórios em aberto ficam congelados (vindo da prévia do servidor) e nunca é bloqueada.
 */
export function StudentAssociationsSection({ student }: { student: AdminStudent }) {
  const selectId = useId();
  const heading = useRef<HTMLHeadingElement>(null);
  const options = useActiveTeacherOptions();
  const preview = usePreviewAssignmentRemoval(student.id);
  const assign = useAssignTeacher(student.id);
  const remove = useRemoveTeacher(student.id);
  const [removal, setRemoval] = useState<Removal | null>(null);
  const [chosen, setChosen] = useState('');
  const [notice, setNotice] = useState('');

  const teachers = student.teachers ?? [];
  const associated = new Set(teachers.map((teacher) => teacher.id));
  // só professores ativos podem ser incluídos; a decisão final é do servidor (409 TEACHER_INACTIVE)
  const available = (options.data?.items ?? []).filter(
    (teacher) => teacher.active && !associated.has(teacher.id),
  );

  const askRemoval = (teacherId: string, teacherName: string) => {
    setNotice('');
    preview.mutate(teacherId, {
      onSuccess: (result) => setRemoval({ teacherId, teacherName, frozenReports: result.frozenReports }),
    });
  };

  const closeRemoval = () => {
    remove.reset();
    setRemoval(null);
  };

  return (
    <section className="admin-section" aria-labelledby="student-teachers">
      <h2 id="student-teachers" className="card-title" ref={heading} tabIndex={-1}>
        Professores do aluno
      </h2>

      {teachers.length === 0 ? (
        <p className="support-text">Este aluno não tem professores associados. Só o administrador o vê.</p>
      ) : (
        <ul className="admin-list">
          {teachers.map((teacher) => (
            <li key={teacher.id} className="admin-list__item">
              <div className="admin-list__main">
                <span className="admin-list__title">{teacher.name}</span>
                <div className="admin-list__meta">
                  <StatusBadge active={teacher.active} />
                </div>
              </div>
              <Button
                variant="danger"
                size="compact"
                aria-label={`Retirar ${teacher.name}`}
                loading={preview.isPending && preview.variables === teacher.id}
                onClick={() => askRemoval(teacher.id, teacher.name)}
              >
                Retirar
              </Button>
            </li>
          ))}
        </ul>
      )}

      {preview.isError ? (
        <div role="alert" className="alert alert--error">
          <Icon name="error" />
          <div className="alert__text">{apiErrorText(preview.error)}</div>
        </div>
      ) : null}

      <div className="field">
        <label htmlFor={selectId} className="field__label">
          Incluir professor
        </label>
        <div className="admin-search">
          <select
            id={selectId}
            className="input"
            value={chosen}
            onChange={(event) => setChosen(event.target.value)}
            disabled={options.isPending || available.length === 0}
          >
            <option value="">Escolha um professor</option>
            {available.map((teacher) => (
              <option key={teacher.id} value={teacher.id}>
                {teacher.name}
              </option>
            ))}
          </select>
          <Button
            variant="secondary"
            disabled={chosen === ''}
            loading={assign.isPending}
            onClick={() => {
              setNotice('');
              assign.mutate(chosen, {
                onSuccess: () => {
                  setChosen('');
                  setNotice('Professor incluído no aluno.');
                },
              });
            }}
          >
            Incluir
          </Button>
        </div>
        {options.data && available.length === 0 ? (
          <div className="field__help">Todos os professores ativos já estão associados.</div>
        ) : null}
      </div>
      {options.isError ? (
        <div role="alert" className="alert alert--error">
          <Icon name="error" />
          <div className="alert__text">{apiErrorText(options.error)}</div>
        </div>
      ) : null}
      {assign.isError ? (
        <div role="alert" className="alert alert--error">
          <Icon name="error" />
          <div className="alert__text">{apiErrorText(assign.error)}</div>
        </div>
      ) : null}

      <div role="status" className="support-text">
        {notice}
      </div>

      <ConfirmationDialog
        open={removal !== null}
        title="Retirar professor do aluno"
        text={
          removal
            ? msg('ADMIN_CONFIRM_REMOVE_ASSIGNMENT', {
                teacher: removal.teacherName,
                student: student.name,
                frozen: removal.frozenReports,
              })
            : ''
        }
        icon="person_remove"
        danger
        confirmLabel="Retirar"
        loading={remove.isPending}
        error={remove.isError ? apiErrorText(remove.error) : undefined}
        onConfirm={() => {
          if (!removal) return;
          remove.mutate(removal.teacherId, {
            onSuccess: () => {
              setRemoval(null);
              setNotice('Professor retirado do aluno.');
              // o botão da linha some: o foco vai para o título da seção
              window.setTimeout(() => heading.current?.focus(), 0);
            },
          });
        }}
        onClose={closeRemoval}
      />
    </section>
  );
}
