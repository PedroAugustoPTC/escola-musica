import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom não implementa matchMedia; o padrão dos testes é o layout móvel (390 px).
if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: query.includes('max-width')
        ? (globalThis as { __testWidth?: number }).__testWidth !== 1440
        : false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// jsdom não cria URLs de Blob; os testes só precisam de um identificador.
if (typeof URL.createObjectURL !== 'function') {
  Object.defineProperty(URL, 'createObjectURL', { writable: true, value: () => 'blob:test' });
  Object.defineProperty(URL, 'revokeObjectURL', { writable: true, value: () => undefined });
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  (globalThis as { __testWidth?: number }).__testWidth = undefined;
});
