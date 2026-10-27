import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Button } from '../../components/Button';
import { ConfirmationDialog } from '../../components/ConfirmationDialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorState';
import { Icon } from '../../components/Icon';
import { LoadingState } from '../../components/LoadingState';
import { OneTimeSecretPanel } from '../../components/admin/OneTimeSecretPanel';
import { TeacherForm } from '../../components/admin/TeacherForm';
import { Pagination, SegmentedFilter, StatusBadge } from '../../components/admin/ui';
import { apiErrorText } from '../../components/admin/fieldErrors';
import { formatSchoolDate } from '../../components/admin/dates';
import { msg } from '../../messages/messages';
import {
  useAdministrators,
  useCreateAdministrator,
  useInactivateAdministrator,
  useReactivateAdministrator,
  type Administrator,
  type AdministratorWithActivationCode,
} from '../../services/admin/administrators';
import type { TeacherActiveFilter } from '../../services/admin/teachers';

const FILTERS: { value: TeacherActiveFilter; label: string }[] = [
  { value: 'all', label: 'Todos' },
  { value: 'active', label: 'Ativos' },
  { value: 'inactive', label: 'Inativos' },
];

function filterOf(value: string | null): TeacherActiveFilter {
  return value === 'active' || value === 'inactive' ? value : 'all';
}

/** A09 — Administradores: lista, criar (código de ativação exibido uma vez), inativar e reativar. */
export function AdministratorsScreen() {
  const [params, setParams] = useSearchParams();
  const filter = filterOf(params.get('status'));
  const page = Math.max(0, Number(params.get('page')) || 0);
  const administrators = useAdministrators({ filter, page });
  const create = useCreateAdministrator();
  const inactivate = useInactivateAdministrator();
  const reactivate = useReactivateAdministrator();
  const [creating, setCreating] = useState(false);
  const [issued, setIssued] = useState<{ result: AdministratorWithActivationCode; emission: number } | null>(
    null,
  );
  const emissions = useRef(0);
  const [target, setTarget] = useState<Administrator | null>(null);
  const data = administrators.data;

  /** Cada emissão tem a própria `key`: um código novo nunca reaproveita o painel (e o valor) do anterior. */
  const showIssued = (result: AdministratorWithActivationCode) => {
    emissions.current += 1;
    setIssued({ result, emission: emissions.current });
  };

  const update = (changes: { status?: TeacherActiveFilter; page?: number }) => {
    const next = new URLSearchParams(params);
    const nextStatus = changes.status ?? filter;
    const nextPage = changes.page ?? 0;
    if (nextStatus !== 'all') next.set('status', nextStatus);
    else next.delete('status');
    if (nextPage > 0) next.set('page', String(nextPage));
    else next.delete('page');
    setParams(next);
  };

  return (
    <div className="page page--wide">
      <div className="admin-page-head">
        <div>
          <div className="kicker">Acesso</div>
          <h1 className="page-title">Administradores</h1>
        </div>
        {!creating && !issued ? (
          <Button icon="person_add" onClick={() => setCreating(true)}>
            Criar administrador
          </Button>
        ) : null}
      </div>

      {issued ? (
        <OneTimeSecretPanel
          key={`${issued.result.administrator.id}:${issued.result.validUntil}:${issued.emission}`}
          kind="activation-code"
          secret={issued.result.activationCode}
          recipientName={issued.result.administrator.name}
          validUntil={issued.result.validUntil}
          onAcknowledge={() => setIssued(null)}
        />
      ) : null}

      {creating && !issued ? (
        <section className="admin-section" aria-labelledby="create-administrator">
          <h2 id="create-administrator" className="card-title">
            Criar administrador
          </h2>
          <TeacherForm
            submitLabel="Criar"
            submitting={create.isPending}
            error={create.error}
            onSubmit={(values) =>
              create.mutate(values, {
                onSuccess: (result) => {
                  showIssued(result);
                  setCreating(false);
                  create.reset();
                },
              })
            }
            secondaryAction={
              <Button
                variant="secondary"
                size="large"
                onClick={() => {
                  create.reset();
                  setCreating(false);
                }}
              >
                Cancelar
              </Button>
            }
          />
        </section>
      ) : null}

      <SegmentedFilter
        label="Situação"
        value={filter}
        options={FILTERS}
        onChange={(value) => update({ status: value })}
      />

      {administrators.isPending ? <LoadingState label="Carregando administradores" /> : null}
      {administrators.isError ? (
        <ErrorAlert error={administrators.error} onRetry={() => void administrators.refetch()} />
      ) : null}

      {data && data.items.length === 0 ? (
        <EmptyState icon="admin_panel_settings" text="Nenhum administrador encontrado." />
      ) : null}

      {data && data.items.length > 0 ? (
        <>
          <ul className="admin-list" aria-busy={administrators.isFetching || undefined}>
            {data.items.map((administrator) => (
              <li key={administrator.id} className="admin-list__item">
                <div className="admin-list__main">
                  <span className="admin-list__title">{administrator.name}</span>
                  <div className="admin-list__meta">
                    <span className="admin-list__meta-item">{administrator.email}</span>
                    <StatusBadge active={administrator.active} />
                    {administrator.activationState === 'PENDING' ? (
                      <span className="status-badge status-badge--pending">
                        <Icon name="hourglass_top" size="small" />
                        {msg('ADMIN_ACTIVATION_PENDING')}
                      </span>
                    ) : administrator.activatedAt ? (
                      <span className="admin-list__meta-item">
                        <Icon name="verified_user" size="small" />
                        {msg('ADMIN_ACTIVATED_AT', { date: formatSchoolDate(administrator.activatedAt) })}
                      </span>
                    ) : null}
                  </div>
                </div>
                {administrator.active ? (
                  <Button
                    variant="danger"
                    size="compact"
                    aria-label={`Inativar ${administrator.name}`}
                    onClick={() => setTarget(administrator)}
                  >
                    Inativar
                  </Button>
                ) : (
                  <Button
                    variant="secondary"
                    size="compact"
                    aria-label={`Reativar ${administrator.name}`}
                    loading={reactivate.isPending && reactivate.variables === administrator.id}
                    // com o código na tela, nenhuma outra emissão: ele só aparece uma vez
                    disabled={issued !== null}
                    onClick={() =>
                      reactivate.mutate(administrator.id, {
                        onSuccess: (result) => {
                          showIssued(result);
                          setCreating(false);
                          reactivate.reset();
                        },
                      })
                    }
                  >
                    Reativar
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <Pagination
            page={data.page}
            size={data.size}
            total={data.total}
            onPage={(next) => update({ page: next })}
          >
            administrador(es)
          </Pagination>
        </>
      ) : null}

      {reactivate.isError ? (
        <div role="alert" className="alert alert--error">
          <Icon name="error" />
          <div className="alert__text">{apiErrorText(reactivate.error)}</div>
        </div>
      ) : null}

      <ConfirmationDialog
        open={target !== null}
        title="Inativar administrador"
        text={target ? msg('ADMIN_CONFIRM_INACTIVATE_ADMINISTRATOR', { name: target.name }) : ''}
        icon="person_off"
        danger
        confirmLabel="Inativar"
        loading={inactivate.isPending}
        error={inactivate.isError ? apiErrorText(inactivate.error) : undefined}
        onConfirm={() => {
          if (target) inactivate.mutate(target.id, { onSuccess: () => setTarget(null) });
        }}
        onClose={() => {
          inactivate.reset();
          setTarget(null);
        }}
      />
    </div>
  );
}
