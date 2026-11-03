import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import type { RouteObject } from 'react-router';
import { routes } from '../../src/app/routes';
import { adminRoutes } from '../../src/app/adminRoutes';
import { myStudentsRoutes } from '../../src/app/myStudentsRoutes';
import { mockApi, renderAsAdministrator, renderAsTeacher, renderRoutes } from '../utils';

/**
 * SC-007 e FR-043: o portal só tem dois papéis (professor e administrador). Aluno e responsável são
 * dados cadastrados, nunca usuários: nenhuma rota, tela, link ou aviso os trata como quem entra.
 * Os alunos aparecem apenas como assunto das telas do professor (`/students`, `/my-students`) e do
 * administrador (`/admin/students`).
 */

/** Todos os caminhos declarados, descendo pelos filhos. */
function pathsOf(list: RouteObject[]): string[] {
  return list.flatMap((route) => [
    ...(route.path ? [route.path] : []),
    ...(route.children ? pathsOf(route.children) : []),
  ]);
}

/** Caminhos que não estão atrás de `SessionGuard` nem de `AdminSessionGuard`. */
function unguardedPathsOf(list: RouteObject[], guarded = false): string[] {
  return list.flatMap((route) => {
    const element = route.element as { type?: { name?: string } } | undefined;
    const name = element?.type?.name;
    const here = guarded || name === 'SessionGuard' || name === 'AdminSessionGuard';
    return [
      ...(route.path && !here ? [route.path] : []),
      ...(route.children ? unguardedPathsOf(route.children, here) : []),
    ];
  });
}

/** Raízes de caminho que existem: professor, "Meus alunos" e administração. */
const ALLOWED_ROOTS = new Set([
  '',
  'sign-in',
  'students',
  'jobs',
  'lesson-reports',
  'semester-reports',
  'my-students',
  'admin',
  '*',
]);

/** Segmento que daria entrada a aluno ou responsável como usuário. */
const USER_SEGMENT = /^(student|guardian|responsible|parent|parents|family|pupil|learner|pupils)$/i;

/** Segmento de acesso (entrar, ativar, conta, perfil) que não pode vir depois de "students". */
const ACCESS_SEGMENT = /^(sign-in|sign-up|login|register|activate|activation|account|profile|portal|home)$/i;

/** Frases de aviso ou convite dirigidas a aluno ou responsável como usuário. */
const AUDIENCE_PHRASES = [
  /(área|portal|acesso|entrada|login)\s+(do|da|de|para)\s+(o\s+|a\s+)?(aluno|aluna|responsável|responsáveis|pais|família)/i,
  /sou\s+(o\s+|a\s+)?(aluno|aluna|responsável)/i,
  /(aluno|aluna|responsável|pais)\s+(pode|podem|deve|devem)\s+(entrar|acessar|usar|consultar)/i,
  /(entrar|acessar|entre|acesse)\s+(como|na\s+área\s+d[oa])\s+(aluno|aluna|responsável)/i,
  /student\s+(portal|sign-in|login)|guardian\s+(portal|sign-in|login)/i,
];

function pageSource(file: string): string {
  return readFileSync(resolve(process.cwd(), 'src', file), 'utf8');
}

describe('nenhuma rota, tela ou aviso para aluno ou responsável como usuário (SC-007)', () => {
  const declared = [...pathsOf(routes), ...pathsOf(adminRoutes), ...pathsOf(myStudentsRoutes)];

  it('todo caminho começa por uma raiz conhecida do professor ou do administrador', () => {
    expect(declared.length).toBeGreaterThan(20);
    for (const path of declared) {
      const root = path.replace(/^\//, '').split('/')[0] ?? '';
      expect(ALLOWED_ROOTS.has(root), `raiz inesperada em ${path}`).toBe(true);
    }
  });

  it('nenhum caminho tem segmento de usuário aluno ou responsável', () => {
    for (const path of declared) {
      for (const segment of path.split('/').filter(Boolean)) {
        expect(USER_SEGMENT.test(segment), `segmento de usuário em ${path}`).toBe(false);
      }
    }
  });

  it('os alunos só aparecem como assunto: nenhum caminho de entrada ou conta depois de "students"', () => {
    for (const path of declared) {
      const segments = path.split('/').filter(Boolean);
      segments.forEach((segment, index) => {
        if (segment === 'students' || segment === 'my-students') {
          const next = segments[index + 1];
          if (next && !next.startsWith(':')) {
            expect(ACCESS_SEGMENT.test(next), `acesso de aluno em ${path}`).toBe(false);
          }
        }
      });
    }
  });

  it('as rotas sem guarda de sessão são só as de entrada e ativação', () => {
    const unguarded = [
      ...unguardedPathsOf(routes),
      ...unguardedPathsOf(adminRoutes),
      ...unguardedPathsOf(myStudentsRoutes),
    ];
    expect(new Set(unguarded)).toEqual(new Set(['/sign-in', '/admin/sign-in', '/admin/activate']));
  });

  it('o código-fonte das rotas não importa tela de aluno ou responsável como usuário', () => {
    const sources = ['app/routes.tsx', 'app/adminRoutes.tsx', 'app/myStudentsRoutes.tsx']
      .map(pageSource)
      .join('\n');
    const imported = [...sources.matchAll(/import\s+\{\s*(\w+)\s*\}\s+from\s+'([^']+)'/g)];
    const screens = imported.map((match) => ({ name: match[1]!, from: match[2]! }));
    expect(screens.length).toBeGreaterThan(15);
    for (const { name, from } of screens) {
      expect(name, `componente ${name}`).not.toMatch(/Guardian|Responsible|Parent|Family|Pupil|Learner/i);
      expect(name, `componente ${name}`).not.toMatch(
        /Student(SignIn|Login|Portal|Area|Account|Profile|Activate)/,
      );
      expect(from, `origem ${from}`).not.toMatch(/guardian|responsible|parent|family|pupil|learner/i);
    }
    for (const phrase of AUDIENCE_PHRASES) {
      expect(sources).not.toMatch(phrase);
    }
  });

  it('as telas de entrada não oferecem link nem aviso para aluno ou responsável', async () => {
    mockApi({});
    for (const path of ['/sign-in', '/admin/sign-in', '/admin/activate']) {
      const { container, unmount } = renderRoutes([...routes], path);
      await waitFor(() => expect(container.querySelector('main, form, h1')).not.toBeNull());
      const links = [...container.querySelectorAll('a[href]')].map((a) => a.getAttribute('href') ?? '');
      for (const href of links) {
        for (const segment of href.split(/[/?#]/).filter(Boolean)) {
          expect(USER_SEGMENT.test(segment), `link ${href} em ${path}`).toBe(false);
        }
      }
      const text = container.textContent ?? '';
      for (const phrase of AUDIENCE_PHRASES) {
        expect(text, `aviso em ${path}`).not.toMatch(phrase);
      }
      unmount();
    }
  });

  it('a navegação do professor e a do administrador não têm link para aluno como usuário', async () => {
    const guard = (links: string[], where: string) => {
      for (const href of links) {
        for (const segment of href.split(/[/?#]/).filter(Boolean)) {
          expect(USER_SEGMENT.test(segment), `link ${href} em ${where}`).toBe(false);
        }
      }
    };

    const teacher = renderAsTeacher(routes, '/', {
      'GET /api/v1/home': {
        body: {
          teacher: { id: '1', name: 'Clara Souza' },
          totalStudents: 0,
          students: [],
          openReports: [],
          audioLimitMb: 50,
        },
      },
    });
    await waitFor(() => expect(teacher.container.querySelector('nav, header, main')).not.toBeNull());
    guard(
      [...teacher.container.querySelectorAll('a[href]')].map((a) => a.getAttribute('href') ?? ''),
      'navegação do professor',
    );
    for (const phrase of AUDIENCE_PHRASES) {
      expect(teacher.container.textContent ?? '').not.toMatch(phrase);
    }
    teacher.unmount();

    const administrator = renderAsAdministrator(routes, '/admin');
    await waitFor(() => expect(administrator.container.querySelector('nav, header, main')).not.toBeNull());
    await screen.findAllByRole('link');
    guard(
      [...administrator.container.querySelectorAll('a[href]')].map((a) => a.getAttribute('href') ?? ''),
      'navegação do administrador',
    );
    for (const phrase of AUDIENCE_PHRASES) {
      expect(administrator.container.textContent ?? '').not.toMatch(phrase);
    }
  });
});
