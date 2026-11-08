import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { TextField } from '../../components/admin/ui';
import { msg } from '../../messages/messages';
import {
  useMonthlyCosts,
  type CostAggregate,
  type MonthlyCostReport,
} from '../../services/admin/operations';
import { todayAtSchool } from '../../services/formatting';

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const USD = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 4,
  maximumFractionDigits: 6,
});
const NUMBER = new Intl.NumberFormat('pt-BR');
const SECONDS = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

export function formatUsd(value: number): string {
  return USD.format(value);
}

export function formatDuration(ms: number): string {
  return `${SECONDS.format(ms / 1000)} s`;
}

type Row = { key: string; name: string; aggregate: CostAggregate };
type SortKey = 'name' | 'count' | 'total' | 'average' | 'time' | 'failed';

const COLUMNS: { key: SortKey; label: string; numeric: boolean }[] = [
  { key: 'name', label: 'COSTS_COLUMN_NAME', numeric: false },
  { key: 'count', label: 'COSTS_COLUMN_COUNT', numeric: true },
  { key: 'total', label: 'COSTS_COLUMN_TOTAL', numeric: true },
  { key: 'average', label: 'COSTS_COLUMN_AVERAGE', numeric: true },
  { key: 'time', label: 'COSTS_COLUMN_TIME', numeric: true },
  { key: 'failed', label: 'COSTS_COLUMN_FAILED', numeric: true },
];

function valueOf(row: Row, key: SortKey): string | number {
  const a = row.aggregate;
  switch (key) {
    case 'name':
      return row.name.toLocaleLowerCase('pt-BR');
    case 'count':
      return a.count;
    case 'total':
      return a.totalCostUsd;
    case 'average':
      return a.averageCostUsd;
    case 'time':
      return a.averageDurationMs;
    default:
      return a.failedCalls ?? 0;
  }
}

/** Tabela ordenável: o cabeçalho é um botão e a coluna ordenada leva `aria-sort`. */
function SortableTable({ id, title, rows }: { id: string; title: string; rows: Row[] }) {
  const [sort, setSort] = useState<{ key: SortKey; direction: 'ascending' | 'descending' }>({
    key: 'total',
    direction: 'descending',
  });
  const sorted = useMemo(() => {
    const factor = sort.direction === 'ascending' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const left = valueOf(a, sort.key);
      const right = valueOf(b, sort.key);
      if (left === right) return 0;
      return left > right ? factor : -factor;
    });
  }, [rows, sort]);

  const toggle = (key: SortKey) =>
    setSort((current) =>
      current.key === key
        ? { key, direction: current.direction === 'ascending' ? 'descending' : 'ascending' }
        : { key, direction: key === 'name' ? 'ascending' : 'descending' },
    );

  return (
    <section className="costs__section" aria-labelledby={id}>
      <h2 className="card-title" id={id}>
        {title}
      </h2>
      <div className="costs__table-wrap">
        <table className="school-history__table costs__table">
          <caption className="visually-hidden">{title}</caption>
          <thead>
            <tr>
              {COLUMNS.map((column) => {
                const active = sort.key === column.key;
                const label = msg(column.label);
                return (
                  <th
                    key={column.key}
                    scope="col"
                    aria-sort={active ? sort.direction : 'none'}
                    className={column.numeric ? 'costs__numeric' : undefined}
                  >
                    <button
                      type="button"
                      className="costs__sort"
                      aria-label={msg(
                        active && sort.direction === 'ascending'
                          ? 'COSTS_SORT_DESCENDING'
                          : 'COSTS_SORT_ASCENDING',
                        { column: label },
                      )}
                      onClick={() => toggle(column.key)}
                    >
                      {label}
                      {active ? <span aria-hidden="true">{sort.direction === 'ascending' ? ' ▲' : ' ▼'}</span> : null}
                    </button>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {sorted.map((row) => (
              <tr key={row.key}>
                <th scope="row">{row.name}</th>
                <td className="costs__numeric">{NUMBER.format(row.aggregate.count)}</td>
                <td className="costs__numeric">{formatUsd(row.aggregate.totalCostUsd)}</td>
                <td className="costs__numeric">{formatUsd(row.aggregate.averageCostUsd)}</td>
                <td className="costs__numeric">{formatDuration(row.aggregate.averageDurationMs)}</td>
                <td className="costs__numeric">{NUMBER.format(row.aggregate.failedCalls ?? 0)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Card({ title, aggregate, help }: { title: string; aggregate: CostAggregate; help?: string }) {
  return (
    <div className="costs__card">
      <h3 className="costs__card-title">{title}</h3>
      <p className="costs__card-value">{formatUsd(aggregate.totalCostUsd)}</p>
      <p className="support-text">{msg('COSTS_CARD_UNITS', { count: NUMBER.format(aggregate.count) })}</p>
      <p className="support-text">
        {msg('COSTS_CARD_AVERAGE', {
          cost: formatUsd(aggregate.averageCostUsd),
          time: formatDuration(aggregate.averageDurationMs),
        })}
      </p>
      {(aggregate.failedCalls ?? 0) > 0 ? (
        <p className="support-text">{msg('COSTS_CARD_FAILED', { count: aggregate.failedCalls ?? 0 })}</p>
      ) : null}
      {help ? <p className="support-text">{help}</p> : null}
    </div>
  );
}

function channelName(code: string): string {
  return code === 'WEB' ? msg('COSTS_CHANNEL_WEB') : code === 'TELEGRAM' ? msg('COSTS_CHANNEL_TELEGRAM') : code;
}

function rowsOf(report: MonthlyCostReport) {
  const channels: Row[] = Object.entries(report.byChannel).map(([code, aggregate]) => ({
    key: code,
    name: channelName(code),
    aggregate,
  }));
  const providers: Row[] = Object.entries(report.byProvider).map(([code, aggregate]) => ({
    key: code,
    name: code === 'unknown' ? msg('COSTS_UNKNOWN_PROVIDER') : code,
    aggregate,
  }));
  const students: Row[] = report.perStudent.map((student) => ({
    key: student.studentId,
    name: student.studentLabel ?? msg('COSTS_STUDENT_REMOVED'),
    aggregate: student,
  }));
  return { channels, providers, students };
}

/**
 * A17 — Tempo e custo do mês (FR-053): total (IA + infraestrutura) e médias por áudio, aula e
 * relatório semestral; por canal, provedor e aluno. Valores em USD. É a única tela que mostra o
 * nome do aluno nas métricas, só para o administrador.
 */
export function CostsScreen() {
  const [params, setParams] = useSearchParams();
  const requested = params.get('month') ?? '';
  const month = MONTH_PATTERN.test(requested) ? requested : todayAtSchool().slice(0, 7);
  const costs = useMonthlyCosts(month);
  const report = costs.data;

  const changeMonth = (value: string) => {
    const next = new URLSearchParams(params);
    if (MONTH_PATTERN.test(value)) next.set('month', value);
    else next.delete('month');
    setParams(next, { replace: true });
  };

  const rows = report ? rowsOf(report) : null;
  const empty = report != null && report.totals.count === 0;

  return (
    <div className="page page--wide">
      <div>
        <h1 className="page-title">{msg('COSTS_TITLE')}</h1>
        <p className="subtitle">{msg('COSTS_SUBTITLE')}</p>
      </div>

      <div className="costs__month">
        <TextField
          label={msg('COSTS_MONTH_LABEL')}
          type="month"
          value={month}
          onChange={(event) => changeMonth(event.target.value)}
        />
      </div>

      {costs.isPending ? <LoadingState label={msg('COSTS_LOADING')} /> : null}
      {costs.isError ? <ErrorAlert error={costs.error} onRetry={() => void costs.refetch()} /> : null}

      {report && !costs.isError ? (
        <div aria-busy={costs.isFetching || undefined}>
          <div className="costs__cards">
            <Card
              title={msg('COSTS_CARD_TOTAL')}
              aggregate={{ ...report.totals, totalCostUsd: report.grandTotalUsd }}
              help={msg('COSTS_CARD_TOTAL_HELP', {
                ai: formatUsd(report.totals.totalCostUsd),
                infrastructure: formatUsd(report.infrastructureUsd),
              })}
            />
            <Card title={msg('COSTS_CARD_AUDIO')} aggregate={report.perAudio} />
            <Card title={msg('COSTS_CARD_LESSON')} aggregate={report.perLesson} />
            <Card title={msg('COSTS_CARD_SEMESTER')} aggregate={report.perSemesterReport} />
          </div>

          {empty ? <EmptyState icon="info" text={msg('COSTS_EMPTY')} /> : null}

          {rows && !empty ? (
            <>
              <SortableTable id="costs-channel" title={msg('COSTS_BY_CHANNEL')} rows={rows.channels} />
              <SortableTable id="costs-provider" title={msg('COSTS_BY_PROVIDER')} rows={rows.providers} />
              <SortableTable id="costs-student" title={msg('COSTS_BY_STUDENT')} rows={rows.students} />
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
