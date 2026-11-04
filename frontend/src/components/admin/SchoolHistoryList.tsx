import { useState } from 'react';
import { Button } from '../Button';
import { msg } from '../../messages/messages';
import { instantDate, instantTime } from '../../services/formatting';
import { downloadPdf, openPdf } from '../../services/pdf';
import {
  schoolReportPdf,
  type PersonSummary,
  type SchoolHistoryItem,
  type SchoolPdfMode,
} from '../../services/admin/schoolHistory';
import { InactiveMark } from './ui';
import { useCompactLayout } from './useCompactLayout';

function Person({ person }: { person: PersonSummary }) {
  return (
    <span className="school-history__person">
      {person.name}
      <InactiveMark active={person.active} />
    </span>
  );
}

function approvedAtText(item: SchoolHistoryItem): string {
  return `${instantDate(item.approvedAt)} ${instantTime(item.approvedAt)}`;
}

function Version({ value }: { value: number }) {
  return (
    <>
      <span aria-hidden="true">v{value}</span>
      <span className="visually-hidden">versão {value}</span>
    </>
  );
}

interface SchoolHistoryListProps {
  items: SchoolHistoryItem[];
  /** Falso quando há filtro de aluno (a coluna Aluno some). */
  showStudent: boolean;
  /** Paginação ("1–20 de N"); sem ela a lista aparece sem navegação de páginas. */
  pagination?: { page: number; size: number; total: number; onPage: (page: number) => void };
}

/** Lista dos relatórios aprovados: cartões abaixo de 640 px, tabela acima; abre e baixa o PDF oficial. */
export function SchoolHistoryList({ items, showStudent, pagination }: SchoolHistoryListProps) {
  const cards = useCompactLayout();
  const [pending, setPending] = useState<string[]>([]);
  const [notice, setNotice] = useState<{ kind: 'error' | 'info'; text: string } | null>(null);
  const caption = msg('SCHOOL_HISTORY_SUBTITLE');

  const run = async (item: SchoolHistoryItem, mode: SchoolPdfMode) => {
    const key = `${item.reportId}:${mode}`;
    const { path, fileName } = schoolReportPdf(item, mode);
    setNotice(null);
    setPending((current) => [...current, key]);
    try {
      if (mode === 'open') {
        const opened = await openPdf(path, fileName);
        if (!opened) setNotice({ kind: 'info', text: msg('PDF_BLOCKED_BY_BROWSER') });
      } else {
        await downloadPdf(path, fileName);
      }
    } catch {
      setNotice({ kind: 'error', text: msg('SCHOOL_HISTORY_PDF_FAILED') });
    } finally {
      setPending((current) => current.filter((value) => value !== key));
    }
  };

  const actions = (item: SchoolHistoryItem) => {
    const suffix = (
      <span className="visually-hidden">
        {' '}
        — {item.label}, {item.student.name}
      </span>
    );
    return (
      <div className="admin-actions">
        <Button
          variant="secondary"
          size="compact"
          icon="open_in_new"
          loading={pending.includes(`${item.reportId}:open`)}
          onClick={() => void run(item, 'open')}
        >
          {msg('SCHOOL_HISTORY_OPEN_PDF')}
          {suffix}
        </Button>
        <Button
          variant="secondary"
          size="compact"
          icon="download"
          loading={pending.includes(`${item.reportId}:download`)}
          onClick={() => void run(item, 'download')}
        >
          {msg('SCHOOL_HISTORY_DOWNLOAD_PDF')}
          {suffix}
        </Button>
      </div>
    );
  };

  return (
    <>
      {notice ? (
        <div
          role={notice.kind === 'error' ? 'alert' : 'status'}
          className={`alert alert--${notice.kind === 'error' ? 'error' : 'info'}`}
        >
          <div className="alert__text">{notice.text}</div>
        </div>
      ) : null}
      {cards ? (
        <ul className="admin-list" aria-label={caption}>
          {items.map((item) => (
            <li key={`${item.type}-${item.reportId}`} className="admin-list__item">
              <div className="admin-list__main">
                <div className="admin-list__title">{item.label}</div>
                <div className="admin-list__meta">
                  {showStudent ? (
                    <span className="admin-list__meta-item">
                      {msg('SCHOOL_HISTORY_COLUMN_STUDENT')}: <Person person={item.student} />
                    </span>
                  ) : null}
                  <span className="admin-list__meta-item">
                    {msg('SCHOOL_HISTORY_COLUMN_TEACHER')}: <Person person={item.teacher} />
                  </span>
                  <span className="admin-list__meta-item">
                    {msg('SCHOOL_HISTORY_COLUMN_APPROVED_AT')} {approvedAtText(item)}
                  </span>
                  <span className="admin-list__meta-item">
                    <Version value={item.approvedVersion} />
                  </span>
                </div>
              </div>
              {actions(item)}
            </li>
          ))}
        </ul>
      ) : (
        <table className="school-history__table">
          <caption className="school-history__caption">{caption}</caption>
          <thead>
            <tr>
              <th scope="col">{msg('SCHOOL_HISTORY_COLUMN_REPORT')}</th>
              {showStudent ? <th scope="col">{msg('SCHOOL_HISTORY_COLUMN_STUDENT')}</th> : null}
              <th scope="col">{msg('SCHOOL_HISTORY_COLUMN_TEACHER')}</th>
              <th scope="col">{msg('SCHOOL_HISTORY_COLUMN_APPROVED_AT')}</th>
              <th scope="col">{msg('SCHOOL_HISTORY_COLUMN_VERSION')}</th>
              <th scope="col">PDF</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={`${item.type}-${item.reportId}`}>
                <td>{item.label}</td>
                {showStudent ? (
                  <td>
                    <Person person={item.student} />
                  </td>
                ) : null}
                <td>
                  <Person person={item.teacher} />
                </td>
                <td>{approvedAtText(item)}</td>
                <td>
                  <Version value={item.approvedVersion} />
                </td>
                <td>{actions(item)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {pagination && pagination.total > pagination.size ? (
        <nav aria-label="Paginação" className="admin-pagination">
          <span className="support-text">
            {pagination.page * pagination.size + 1}–
            {Math.min((pagination.page + 1) * pagination.size, pagination.total)} de {pagination.total}
          </span>
          <div className="admin-actions">
            <Button
              variant="secondary"
              size="compact"
              disabled={pagination.page <= 0}
              onClick={() => pagination.onPage(pagination.page - 1)}
            >
              Anterior
            </Button>
            <Button
              variant="secondary"
              size="compact"
              disabled={(pagination.page + 1) * pagination.size >= pagination.total}
              onClick={() => pagination.onPage(pagination.page + 1)}
            >
              Próxima
            </Button>
          </div>
        </nav>
      ) : null}
    </>
  );
}
