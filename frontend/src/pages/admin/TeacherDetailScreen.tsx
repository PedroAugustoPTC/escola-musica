import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { Button } from '../../components/Button';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { OneTimeSecretPanel } from '../../components/admin/OneTimeSecretPanel';
import { TeacherForm } from '../../components/admin/TeacherForm';
import { StatusBadge } from '../../components/admin/ui';
import { apiErrorText } from '../../components/admin/fieldErrors';
import { msg } from '../../messages/messages';
import {
  useInactivateTeacher,
  useReactivateTeacher,
  useReissueAccessCode,
  useTeacher,
  useUnlinkTeacherTelegram,
  useUpdateTeacher,
  type TeacherWithCode,
} from '../../services/admin/teachers';

/** A08 — Professor: editar, inativar/reativar (a reativação mostra o código novo uma única vez). */
export function TeacherDetailScreen() {
  const { teacherId = '' } = useParams();
  const teacher = useTeacher(teacherId);
  const update = useUpdateTeacher(teacherId);
  const inactivate = useInactivateTeacher(teacherId);
  const reactivate = useReactivateTeacher(teacherId);
  const reissue = useReissueAccessCode(teacherId);
  const unlink = useUnlinkTeacherTelegram(teacherId);
  const [confirmReissue, setConfirmReissue] = useState(false);
  const [confirmUnlink, setConfirmUnlink] = useState(false);
  const [endTelegramLink, setEndTelegramLink] = useState(false);
  const [notice, setNotice] = useState('');
  const [saved, setSaved] = useState(false);
  const [confirmInactivate, setConfirmInactivate] = useState(false);
  const [issued, setIssued] = useState<{ result: TeacherWithCode; emission: number } | null>(null);
  const emissions = useRef(0);
  const heading = useRef<HTMLHeadingElement>(null);
  const data = teacher.data;
  const active = data?.active;
  const firstRender = useRef(true);

  /** Cada emissão tem a própria `key`: um código novo nunca reaproveita o painel (e o valor) do anterior. */
  const showIssued = (result: TeacherWithCode) => {
    emissions.current += 1;
    setIssued({ result, emission: emissions.current });
  };

  // a ação de situação troca os botões: devolve o foco ao título para não o perder
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    heading.current?.focus();
  }, [active]);

  if (teacher.isPending) return <LoadingState label="Carregando professor" />;
  if (teacher.isError || !data) {
    return (
      <div className="page page--narrow">
        <ErrorAlert error={teacher.error} onRetry={() => void teacher.refetch()} />
        <Link to="/admin/teachers" className="button button--secondary">
          Voltar para os professores
        </Link>
      </div>
    );
  }

  return (
    <div className="page page--medium">
      <div>
        <div className="kicker">Professor</div>
        <h1 className="page-title" ref={heading} tabIndex={-1}>
          {data.name}
        </h1>
        <div className="admin-list__meta">
          <StatusBadge active={data.active} />
          <span className="admin-list__meta-item">
            <Icon name={data.telegramLinked ? 'link' : 'link_off'} size="small" />
            {data.telegramLinked ? 'Telegram vinculado' : 'Telegram não vinculado'}
          </span>
        </div>
      </div>

      {issued ? (
        <OneTimeSecretPanel
          key={`${issued.result.teacher.id}:${issued.result.validUntil}:${issued.emission}`}
          kind="access-code"
          secret={issued.result.accessCode}
          recipientName={issued.result.teacher.name}
          validUntil={issued.result.validUntil}
          onAcknowledge={() => setIssued(null)}
        />
      ) : null}

      <section className="admin-section" aria-labelledby="teacher-data">
        <h2 id="teacher-data" className="card-title">
          Dados
        </h2>
        <TeacherForm
          key={`${data.name}|${data.email}`}
          initial={{ name: data.name, email: data.email }}
          submitLabel="Salvar"
          submitting={update.isPending}
          error={update.error}
          onSubmit={(values) => {
            setSaved(false);
            update.mutate(values, { onSuccess: () => setSaved(true) });
          }}
        />
        <div role="status" className="support-text">
          {saved ? 'Dados salvos.' : ''}
        </div>
      </section>

      <section className="admin-section" aria-labelledby="teacher-access">
        <h2 id="teacher-access" className="card-title">
          Acesso
        </h2>
        <div className="admin-actions">
          {data.active ? (
            <>
              <Button
                variant="secondary"
                icon="key"
                // com o código na tela, nenhuma outra emissão: ele só aparece uma vez
                disabled={issued !== null}
                onClick={() => {
                  setEndTelegramLink(false);
                  setConfirmReissue(true);
                }}
              >
                Reemitir código
              </Button>
              {data.telegramLinked ? (
                <Button variant="secondary" icon="link_off" onClick={() => setConfirmUnlink(true)}>
                  Desvincular Telegram
                </Button>
              ) : null}
              <Button variant="danger" icon="person_off" onClick={() => setConfirmInactivate(true)}>
                Inativar
              </Button>
            </>
          ) : (
            <Button
              icon="person_check"
              loading={reactivate.isPending}
              disabled={issued !== null}
              onClick={() =>
                reactivate.mutate(undefined, {
                  onSuccess: (result) => {
                    showIssued(result);
                    reactivate.reset();
                  },
                })
              }
            >
              Reativar
            </Button>
          )}
        </div>
        {!data.active ? (
          <p className="support-text">
            Este professor está inativo. Reative-o para emitir um código de acesso novo.
          </p>
        ) : null}
        <div role="status" className="support-text">
          {notice}
        </div>
        {reactivate.isError ? (
          <div role="alert" className="alert alert--error">
            <Icon name="error" />
            <div className="alert__text">{apiErrorText(reactivate.error)}</div>
          </div>
        ) : null}
      </section>

      <ConfirmationDialog
        open={confirmInactivate}
        title="Inativar professor"
        text={msg('ADMIN_CONFIRM_INACTIVATE_TEACHER', { name: data.name })}
        icon="person_off"
        danger
        confirmLabel="Inativar"
        loading={inactivate.isPending}
        error={inactivate.isError ? apiErrorText(inactivate.error) : undefined}
        onConfirm={() => inactivate.mutate(undefined, { onSuccess: () => setConfirmInactivate(false) })}
        onClose={() => {
          inactivate.reset();
          setConfirmInactivate(false);
        }}
      />
      <ConfirmationDialog
        open={confirmReissue}
        title="Reemitir código de acesso"
        text={
          <>
            {msg('ADMIN_CONFIRM_REISSUE_CODE', { name: data.name })}
            {data.telegramLinked ? (
              <span className="checkbox-field">
                <input
                  id="end-telegram-link"
                  type="checkbox"
                  checked={endTelegramLink}
                  onChange={(event) => setEndTelegramLink(event.target.checked)}
                />
                <label htmlFor="end-telegram-link">{msg('ADMIN_END_TELEGRAM_OPTION')}</label>
              </span>
            ) : null}
          </>
        }
        icon="key"
        confirmLabel="Reemitir"
        loading={reissue.isPending}
        error={reissue.isError ? apiErrorText(reissue.error) : undefined}
        onConfirm={() =>
          reissue.mutate(
            { endTelegramLink: data.telegramLinked && endTelegramLink },
            {
              onSuccess: (result) => {
                setConfirmReissue(false);
                showIssued(result);
                setNotice('');
                reissue.reset();
              },
            },
          )
        }
        onClose={() => {
          reissue.reset();
          setConfirmReissue(false);
        }}
      />

      <ConfirmationDialog
        open={confirmUnlink}
        title="Desvincular Telegram"
        text={msg('ADMIN_CONFIRM_UNLINK_TELEGRAM', { name: data.name })}
        icon="link_off"
        danger
        confirmLabel="Desvincular"
        loading={unlink.isPending}
        error={unlink.isError ? apiErrorText(unlink.error) : undefined}
        onConfirm={() =>
          unlink.mutate(undefined, {
            onSuccess: () => {
              setConfirmUnlink(false);
              setNotice('Vínculo do Telegram encerrado.');
              // o botão some: o foco vai para o título
              window.setTimeout(() => heading.current?.focus(), 0);
            },
          })
        }
        onClose={() => {
          unlink.reset();
          setConfirmUnlink(false);
        }}
      />
    </div>
  );
}
