import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SituationBadge } from '../../../src/components/student/SituationBadge';

describe('SituationBadge', () => {
  it('mostra ícone e texto para o aluno ativo', () => {
    const { container } = render(<SituationBadge active />);
    expect(screen.getByText('Ativo')).toBeInTheDocument();
    const icon = container.querySelector('.icon');
    expect(icon).not.toBeNull();
    expect(icon).toHaveAttribute('aria-hidden', 'true');
  });

  it('mostra ícone e texto para o aluno inativo', () => {
    const { container } = render(<SituationBadge active={false} />);
    expect(screen.getByText('Inativo')).toBeInTheDocument();
    expect(container.querySelector('.icon')).not.toBeNull();
  });

  it('nunca distingue a situação só pela cor: o ícone e o texto mudam juntos', () => {
    const active = render(<SituationBadge active />);
    const activeIcon = active.container.querySelector('.icon')!.textContent;
    const activeText = active.container.textContent;
    active.unmount();
    const inactive = render(<SituationBadge active={false} />);
    expect(inactive.container.querySelector('.icon')!.textContent).not.toBe(activeIcon);
    expect(inactive.container.textContent).not.toBe(activeText);
  });
});
