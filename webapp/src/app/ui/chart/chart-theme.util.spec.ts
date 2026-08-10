import { afterEach, describe, expect, it } from 'vitest';
import { resolveThemePalette } from './chart-theme.util';

describe('resolveThemePalette', () => {
  const created: HTMLElement[] = [];

  function createElement(styles: Record<string, string>): HTMLElement {
    const element = document.createElement('div');
    for (const [property, value] of Object.entries(styles)) {
      element.style.setProperty(property, value);
    }
    document.body.appendChild(element);
    created.push(element);
    return element;
  }

  afterEach(() => {
    while (created.length) {
      created.pop()?.remove();
    }
  });

  it('picks the light branch of a light-dark() token when color-scheme is light', () => {
    const element = createElement({
      'color-scheme': 'light',
      '--mat-sys-on-surface': 'light-dark(#111111, #eeeeee)',
    });

    expect(resolveThemePalette(element).textColor).toBe('#111111');
  });

  it('picks the dark branch of a light-dark() token when color-scheme is dark', () => {
    const element = createElement({
      'color-scheme': 'dark',
      '--mat-sys-on-surface': 'light-dark(#111111, #eeeeee)',
    });

    expect(resolveThemePalette(element).textColor).toBe('#eeeeee');
  });

  it('splits on the top-level comma so nested rgb() values stay intact', () => {
    const element = createElement({
      'color-scheme': 'dark',
      '--mat-sys-on-surface': 'light-dark(rgb(0, 0, 0), rgb(255, 255, 255))',
    });

    expect(resolveThemePalette(element).textColor).toBe('rgb(255, 255, 255)');
  });

  it('returns a plain token value unchanged when it is not a light-dark() function', () => {
    const element = createElement({
      'color-scheme': 'light',
      '--mat-sys-surface-container-highest': '#abcdef',
    });

    expect(resolveThemePalette(element).gridColor).toBe('#abcdef');
  });

  it('leaves colors undefined when the token is not defined', () => {
    const element = createElement({ 'color-scheme': 'light' });

    expect(resolveThemePalette(element).textColor).toBeUndefined();
  });

  it('falls back to the built-in series colors when the chart tokens are absent', () => {
    const element = createElement({ 'color-scheme': 'light' });

    const { seriesColors } = resolveThemePalette(element);

    expect(seriesColors).toHaveLength(8);
    expect(seriesColors?.[0]).toBe('#4285F4');
  });

  it('prefers defined chart series tokens over the built-in fallbacks', () => {
    const element = createElement({
      'color-scheme': 'dark',
      '--chart-series-1': 'light-dark(#000001, #000002)',
    });

    expect(resolveThemePalette(element).seriesColors?.[0]).toBe('#000002');
  });
});
