import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAdminStudents } from '../../src/services/admin/students';
import { useTeachers } from '../../src/services/admin/teachers';
import { useMyStudents } from '../../src/services/myStudents';
import { useStudents } from '../../src/services/students';
import { mockApi } from '../utils';
import { TEST_CPF, TEST_CPF_MASKED } from '../support/adminFixtures';

/** Defesa em profundidade: mesmo que uma tela deixe passar, o termo com forma de CPF não chega à API nem à chave. */
function setup() {
  const api = mockApi({});
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { api, client, wrapper };
}

async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 50));
}

function keysOf(client: QueryClient) {
  return JSON.stringify(
    client
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey),
  );
}

describe.each([TEST_CPF, TEST_CPF_MASKED, '111 444 777 35'])('termo com forma de CPF (%s)', (term) => {
  it('useTeachers não consulta e a chave não guarda o termo', async () => {
    const { api, client, wrapper } = setup();
    renderHook(() => useTeachers({ search: term, filter: 'all', page: 0 }), { wrapper });
    await settle();
    expect(api.calls).toEqual([]);
    expect(keysOf(client)).not.toMatch(/11144477735|111\.444|111 444/);
  });

  it('useAdminStudents não consulta e a chave não guarda o termo', async () => {
    const { api, client, wrapper } = setup();
    renderHook(() => useAdminStudents({ search: term, status: 'ACTIVE', page: 0 }), { wrapper });
    await settle();
    expect(api.calls).toEqual([]);
    expect(keysOf(client)).not.toMatch(/11144477735|111\.444|111 444/);
  });

  it('useMyStudents não consulta e a chave não guarda o termo', async () => {
    const { api, client, wrapper } = setup();
    renderHook(() => useMyStudents({ search: term, status: 'ACTIVE', page: 0 }), { wrapper });
    await settle();
    expect(api.calls).toEqual([]);
    expect(keysOf(client)).not.toMatch(/11144477735|111\.444|111 444/);
  });

  it('useStudents não consulta e a chave não guarda o termo', async () => {
    const { api, client, wrapper } = setup();
    renderHook(() => useStudents(term), { wrapper });
    await settle();
    expect(api.calls).toEqual([]);
    expect(keysOf(client)).not.toMatch(/11144477735|111\.444|111 444/);
  });
});

describe('termo comum', () => {
  it('useTeachers consulta normalmente', async () => {
    const api = mockApi({
      'GET /api/v1/admin/teachers': { body: { items: [], page: 0, size: 20, total: 0 } },
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    renderHook(() => useTeachers({ search: 'clara', filter: 'all', page: 0 }), { wrapper });
    await waitFor(() => expect(api.calls.some((call) => call.url.includes('search=clara'))).toBe(true));
  });
});
