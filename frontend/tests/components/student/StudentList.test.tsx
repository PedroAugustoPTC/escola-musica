import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { StudentList } from '../../../src/components/student/StudentList';
import { renderWithProviders } from '../../utils';
import { administratorStudent } from '../../support/adminFixtures';
import { MY_STUDENT_ACTIVE, MY_STUDENT_INACTIVE } from '../../support/myStudentsFixtures';

const detailPath = (id: string) => `/my-students/${id}`;

describe('StudentList', () => {
  it('lista nome, idade, instrumento e situação, com o nome levando ao detalhe', () => {
    renderWithProviders(
      <StudentList
        variant="teacher"
        students={[MY_STUDENT_ACTIVE, MY_STUDENT_INACTIVE]}
        detailPath={detailPath}
      />,
    );
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);

    const first = within(items[0]!);
    expect(first.getByRole('link', { name: 'Olavo Torrente' })).toHaveAttribute(
      'href',
      `/my-students/${MY_STUDENT_ACTIVE.id}`,
    );
    expect(first.getByText(/16 anos/)).toBeInTheDocument();
    expect(first.getByText(/Violão/)).toBeInTheDocument();
    expect(first.getByText('Ativo')).toBeInTheDocument();

    const second = within(items[1]!);
    expect(second.getByRole('link', { name: 'Helena Prado' })).toBeInTheDocument();
    expect(second.getByText('Inativo')).toBeInTheDocument();
  });

  it('na variante do professor nunca mostra outros professores', () => {
    renderWithProviders(
      <StudentList
        variant="teacher"
        students={[{ ...MY_STUDENT_ACTIVE, teachers: [{ id: 't1', name: 'Outro Professor', active: true }] }]}
        detailPath={detailPath}
      />,
    );
    expect(screen.queryByText(/Outro Professor/)).not.toBeInTheDocument();
  });

  it('na variante do administrador mostra os professores associados e a situação deles', () => {
    renderWithProviders(
      <StudentList
        variant="admin"
        students={[
          administratorStudent({
            teachers: [
              { id: 't1', name: 'Clara Souza', active: true },
              { id: 't2', name: 'Bruno Lima', active: false },
            ],
          }),
        ]}
        detailPath={(id) => `/admin/students/${id}`}
      />,
    );
    const item = screen.getByRole('listitem');
    expect(within(item).getByText(/Clara Souza/)).toBeInTheDocument();
    expect(within(item).getByText(/Bruno Lima \(inativo\)/)).toBeInTheDocument();
    expect(within(item).getByRole('link', { name: 'Matteo Mafra' })).toHaveAttribute(
      'href',
      '/admin/students/0190a000-0000-7000-8000-0000000000d1',
    );
  });

  it('administrador sem professor associado vê a indicação de que não há ninguém', () => {
    renderWithProviders(
      <StudentList
        variant="admin"
        students={[administratorStudent({ teachers: [] })]}
        detailPath={(id) => `/admin/students/${id}`}
      />,
    );
    expect(screen.getByText('Sem professor associado')).toBeInTheDocument();
  });

  it('é uma lista com nome acessível e não mostra CPF', () => {
    renderWithProviders(
      <StudentList variant="teacher" students={[MY_STUDENT_ACTIVE]} detailPath={detailPath} label="Meus alunos" />,
    );
    expect(screen.getByRole('list', { name: 'Meus alunos' })).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/\d{3}\.?\d{3}\.?\d{3}-?\d{2}/);
  });
});
