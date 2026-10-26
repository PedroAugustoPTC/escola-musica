import { Icon } from '../Icon';

/** Situação do aluno: ícone e texto juntos, nunca só cor (WCAG 1.4.1). */
export function SituationBadge({ active }: { active: boolean }) {
  return (
    <span className={`chip situation-badge situation-badge--${active ? 'active' : 'inactive'}`}>
      <Icon name={active ? 'check_circle' : 'pause_circle'} size="small" />
      {active ? 'Ativo' : 'Inativo'}
    </span>
  );
}
