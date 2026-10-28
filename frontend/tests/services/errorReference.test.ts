import { describe, expect, it } from 'vitest';
import { ApiError } from '../../src/services/apiClient';
import { msg } from '../../src/messages/messages';

describe('código de referência da falha interna', () => {
  it('ApiError guarda o reference que o servidor devolve e mantém o texto pronto do corpo', () => {
    const error = new ApiError(500, {
      code: 'INTERNAL_ERROR',
      message: 'Algo deu errado. Informe o código de referência ABC123 ao suporte.',
      reference: 'ABC123',
    });
    expect(error.reference).toBe('ABC123');
    expect(error.message).toContain('ABC123');
  });

  it('sem corpo (proxy fora do ar, resposta não JSON) o texto não traz {reference} nem lacuna', () => {
    const error = new ApiError(502, null);
    expect(error.reference).toBeUndefined();
    expect(error.message).toBe('Algo deu errado.');
  });

  it('msg preenche {reference} quando há referência e a omite junto com a frase quando não há', () => {
    expect(msg('INTERNAL_ERROR', { reference: 'XYZ789' })).toBe(
      'Algo deu errado. Informe o código de referência XYZ789 ao suporte.',
    );
    for (const params of [{}, { reference: '' }, { reference: null }]) {
      const text = msg('INTERNAL_ERROR', params);
      expect(text).not.toContain('{');
      expect(text).not.toContain('  ');
      expect(text).toBe('Algo deu errado.');
    }
  });

  it('ApiError sem referência usa o texto do catálogo sem a frase da referência', () => {
    const error = new ApiError(500, { code: 'INTERNAL_ERROR' });
    expect(error.message).toBe('Algo deu errado.');
  });
});
