import { describe, expect, it } from 'vitest';
import catalog from '../../src/messages/messages.json';
import { msg, renderMessage } from '../../src/messages/messages';

const REFERENCE = 'K7M2QX';

/** Frase não quebrada: sem placeholder, sem lacuna dupla, sem espaço antes de pontuação nem nas pontas. */
function expectWellFormed(text: string) {
  expect(text).not.toMatch(/[{}]/);
  expect(text).not.toMatch(/\s{2,}/);
  expect(text).not.toMatch(/\s[.!?,;:]/);
  expect(text).toBe(text.trim());
}

describe('mensagens do catálogo com {reference}', () => {
  const withReference = Object.entries(catalog as Record<string, string>).filter(([, text]) =>
    text.includes('{reference}'),
  );

  it('o catálogo tem ao menos uma mensagem com o código de referência', () => {
    expect(withReference.length).toBeGreaterThan(0);
  });

  it.each(withReference)(
    '%s: sem referência, a frase do código some inteira e o resto fica íntegro',
    (code) => {
      const text = msg(code);
      expectWellFormed(text);
      expect(text).not.toContain('referência');
    },
  );

  it.each(withReference)('%s: com referência, o código aparece no texto', (code) => {
    const text = msg(code, { reference: REFERENCE });
    expect(text).toContain(REFERENCE);
    expectWellFormed(text);
  });
});

describe('renderMessage: qualquer texto que o backend vier a ter', () => {
  const MODELS: Record<string, [string, string]> = {
    'frase final com ponto': [
      'Algo deu errado. Informe o código de referência {reference} ao suporte.',
      'Algo deu errado.',
    ],
    'frase final sem pontuação': [
      'Algo deu errado. Informe o código de referência {reference} ao suporte',
      'Algo deu errado.',
    ],
    'frase final com exclamação': ['Falhou. Anote o código {reference} agora!', 'Falhou.'],
    'referência na primeira frase': ['Informe {reference} ao suporte. Tente de novo.', 'Tente de novo.'],
    'referência no meio': [
      'Falhou. Código {reference} para o suporte. Tente de novo.',
      'Falhou. Tente de novo.',
    ],
    'referência no fim da frase, sem pontuação final': ['Falhou. Código: {reference}', 'Falhou.'],
    'duas frases com referência': [
      'Erro {reference}. Tente de novo. Suporte: {reference}.',
      'Tente de novo.',
    ],
    'sem referência no modelo': ['Algo deu errado. Tente de novo.', 'Algo deu errado. Tente de novo.'],
  };

  it.each(Object.entries(MODELS))('%s: sem referência', (_name, [model, expected]) => {
    const text = renderMessage(model, {});
    expect(text).toBe(expected);
    expectWellFormed(text);
  });

  it.each(['', null, undefined])('referência %j conta como ausente', (reference) => {
    expect(renderMessage('Falhou. Código {reference}', { reference })).toBe('Falhou.');
  });

  it('com referência, mantém o texto e troca só o placeholder', () => {
    expect(renderMessage('Falhou. Código {reference} ao suporte.', { reference: REFERENCE })).toBe(
      `Falhou. Código ${REFERENCE} ao suporte.`,
    );
  });

  it('o texto todo só com a referência vira a mensagem genérica, nunca vazio', () => {
    const text = renderMessage('Código {reference}', {});
    expect(text.length).toBeGreaterThan(0);
    expectWellFormed(text);
  });
});

describe('msg com código fora do catálogo', () => {
  it('cai em INTERNAL_ERROR sem deixar o {reference} cru', () => {
    const without = msg('CODE_NOT_IN_CATALOG');
    expectWellFormed(without);
    expect(msg('CODE_NOT_IN_CATALOG', { reference: REFERENCE })).toContain(REFERENCE);
  });
});
