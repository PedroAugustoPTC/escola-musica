import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useIsMobile } from '../app/useIsMobile';
import { Button } from '../components/Button';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { Layout } from '../components/Layout';
import { Icon } from '../components/Icon';
import { msg } from '../messages/messages';
import { pdfOfficial, itemSeal, approvedAtText, useHistory, type HistoryItemDto } from '../services/history';
import { openPdf } from '../services/pdf';

/**
 * Tela 12 — Histórico: relatórios aprovados do aluno, do mais recente ao mais antigo, com os mesmos
 * rótulos do bot; "Abrir PDF" abre o documento oficial numa nova aba (12b vazio, 12c carregando).
 */
export function HistoryScreen() {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const mobile = useIsMobile();
  const history = useHistory(studentId);
  const [opening, setOpening] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ type: 'info' | 'error'; text: string } | null>(null);

  const context = {
    kicker: 'Histórico',
    title: history.data?.student.name ?? '',
    backTo: '/students',
  };

  if (history.isPending) {
    return (
      <Layout activeNav="students" context={context} bottomNav>
        <LoadingState label="Carregando histórico" />
      </Layout>
    );
  }
  if (history.isError) {
    return (
      <Layout activeNav="students" context={context} bottomNav>
        <ErrorState error={history.error} onRetry={() => void history.refetch()} />
      </Layout>
    );
  }

  const { student, items } = history.data;

  const open = async (item: HistoryItemDto) => {
    const { path, fileName } = pdfOfficial(item);
    setNotice(null);
    setOpening(item.reportId);
    try {
      const opened = await openPdf(path, fileName);
      if (!opened) setNotice({ type: 'info', text: msg('PDF_BLOCKED_BY_BROWSER') });
    } catch (failure) {
      setNotice({ type: 'error', text: failure instanceof Error ? failure.message : msg('INTERNAL_ERROR') });
    } finally {
      setOpening(null);
    }
  };

  return (
    <Layout activeNav="students" context={context} bottomNav>
      <div className="page page--medium">
        <div>
          <div className="kicker">Histórico</div>
          <h1 className="page-title">{student.name}</h1>
          <p className="subtitle">Relatórios aprovados, do mais recente ao mais antigo.</p>
        </div>
        {notice ? (
          <div role={notice.type === 'error' ? 'alert' : 'status'} className={`alert alert--${notice.type}`}>
            <div className="alert__text">{notice.text}</div>
          </div>
        ) : null}
        {items.length === 0 ? (
          <EmptyState
            monogram
            text={msg('EMPTY_HISTORY')}
            action={
              <Button onClick={() => navigate(`/students/${student.id}/new-lesson`)}>
                {msg('REGISTER_LESSON_BUTTON')}
              </Button>
            }
          />
        ) : (
          <ol className="history" aria-label={msg('HISTORY_TITLE', { student: student.name })}>
            {items.map((item) => {
              const seal = itemSeal(item);
              return (
                <li key={`${item.type}-${item.reportId}`} className="history__item">
                  <div className="history__seal" aria-hidden="true">
                    <div className="history__day">{seal.day}</div>
                    <div className="history__month">{seal.month}</div>
                  </div>
                  <div className="history__body">
                    <div className="history__title">
                      <span>{item.label}</span>
                      {item.type === 'SEMESTER' ? <span className="history__label">Semestral</span> : null}
                    </div>
                    <div className="history__approved">
                      <Icon name="check_circle" />
                      {approvedAtText(item)}
                    </div>
                  </div>
                  <Button
                    variant="secondary"
                    icon="picture_as_pdf"
                    className="history__pdf"
                    aria-label={`${msg('OPEN_PDF_BUTTON')} — ${item.label}`}
                    loading={opening === item.reportId}
                    onClick={() => void open(item)}
                  >
                    {mobile ? 'PDF' : msg('OPEN_PDF_BUTTON')}
                  </Button>
                </li>
              );
            })}
          </ol>
        )}
      </div>
    </Layout>
  );
}
