import { Link } from 'react-router';
import { msg } from '../messages/messages';
import { useAlertSummary } from '../services/admin/operations';
import { Icon } from './Icon';

/**
 * Indicador de alertas ativos no cabeçalho da área administrativa (FR-056): ícone mais texto
 * ("N alertas ativos"), nunca só cor, com link para a tela de alertas. A região é `aria-live`
 * polida, então a mudança da contagem é anunciada sem tirar o foco. Falha na consulta não mostra
 * nada: o indicador é um aviso, não pode atrapalhar o trabalho.
 */
export function AlertIndicator() {
  const summary = useAlertSummary();
  const count = summary.data?.activeCount;
  const text =
    count === undefined
      ? ''
      : count === 0
        ? msg('ALERT_INDICATOR_NONE')
        : count === 1
          ? msg('ALERT_INDICATOR_ONE')
          : msg('ALERT_INDICATOR_MANY', { count });

  return (
    <div className="alert-indicator" aria-live="polite">
      {count === undefined ? null : (
        <Link
          to="/admin/operations/alerts"
          className={`alert-indicator__link${count > 0 ? ' alert-indicator__link--active' : ''}`}
          title={msg('ALERT_INDICATOR_LINK')}
        >
          <Icon name={count > 0 ? 'warning' : 'check_circle'} size="small" />
          <span>{text}</span>
        </Link>
      )}
    </div>
  );
}
