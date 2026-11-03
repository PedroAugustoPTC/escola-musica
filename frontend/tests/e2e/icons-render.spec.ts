import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

/**
 * Glifos da fonte de ícones: o guardião `icons-included.test.ts` só confere que os nomes usados no código
 * constam em `included-icons.txt`; nada provava que o `.woff2` servido de fato tem cada glifo. Aqui, no
 * navegador, cada nome da lista vira um `<span class="icon">` com a fonte carregada: um nome com glifo
 * ocupa cerca de 1em (a ligadura vira um quadrado); um nome sem glifo cai em texto de letras e fica larga.
 */
const INCLUDED_ICONS = readFileSync(
  fileURLToPath(new URL('../../src/styles/fonts/included-icons.txt', import.meta.url)),
  'utf8',
)
  .split(',')
  .map((name) => name.trim())
  .filter(Boolean);

/** Nome que não existe na fonte: controle negativo, prova que a medição enxerga a falta de glifo. */
const MISSING_ICON = 'icon_missing_from_the_font';
/** Tolerância da largura de um glifo em relação a 1em. */
const MIN_EM = 0.85;
const MAX_EM = 1.15;

test('a lista de ícones incluídos não está vazia', () => {
  expect(INCLUDED_ICONS.length).toBeGreaterThan(10);
});

test('todo ícone incluído tem glifo na fonte servida (largura de 1em), e um nome sem glifo é largo', async ({
  page,
}) => {
  await page.goto('/sign-in');
  const widths = await page.evaluate(
    async ({ names, missing }) => {
      const host = document.createElement('div');
      host.style.cssText = 'position:absolute;left:0;top:0;visibility:hidden';
      for (const name of [...names, missing]) {
        const icon = document.createElement('span');
        icon.className = 'icon';
        icon.dataset.name = name;
        icon.textContent = name;
        host.appendChild(icon);
      }
      document.body.appendChild(host);
      // a fonte só baixa quando algum texto a usa: pede e espera a carga antes de medir
      await document.fonts.load('1em "Material Symbols Outlined"', names.join(' '));
      await document.fonts.ready;
      const result: Record<string, number> = {};
      for (const icon of Array.from(host.children) as HTMLElement[]) {
        const size = parseFloat(getComputedStyle(icon).fontSize);
        result[icon.dataset.name ?? ''] = icon.getBoundingClientRect().width / size;
      }
      const loaded = document.fonts.check('1em "Material Symbols Outlined"');
      host.remove();
      return { result, loaded };
    },
    { names: INCLUDED_ICONS, missing: MISSING_ICON },
  );

  expect(widths.loaded, 'a fonte "Material Symbols Outlined" não está carregada').toBe(true);
  const outOfRange = INCLUDED_ICONS.filter((name) => {
    const em = widths.result[name] ?? 0;
    return em < MIN_EM || em > MAX_EM;
  });
  expect(outOfRange, 'ícones da lista sem glifo na fonte (largura fora de ~1em)').toEqual([]);
  expect(
    widths.result[MISSING_ICON] ?? 0,
    'controle negativo: nome sem glifo deveria ser largo',
  ).toBeGreaterThan(MAX_EM * 3);
});
