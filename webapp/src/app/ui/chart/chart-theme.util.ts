import { ChartThemePalette } from './chart.types';

function resolveColorScheme(element: Element): 'light' | 'dark' {
  const scheme = getComputedStyle(element).colorScheme;
  if (scheme === 'dark') return 'dark';
  if (scheme === 'light') return 'light';
  if (typeof matchMedia !== 'function') return 'light';
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/**
 * Splits `light-dark(<light>, <dark>)` into its two arguments, respecting nested parentheses
 * (e.g. `light-dark(rgb(0, 0, 0), rgb(255, 255, 255))`) rather than naively splitting on the
 * first comma.
 */
function parseLightDark(value: string): [light: string, dark: string] | undefined {
  const prefix = 'light-dark(';
  if (!value.startsWith(prefix) || !value.endsWith(')')) return undefined;

  const args = value.slice(prefix.length, -1);
  let depth = 0;
  for (let i = 0; i < args.length; i++) {
    const char = args[i];
    if (char === '(') depth++;
    else if (char === ')') depth--;
    else if (char === ',' && depth === 0) {
      return [args.slice(0, i).trim(), args.slice(i + 1).trim()];
    }
  }
  return undefined;
}

function resolveCssVariableColor(element: HTMLElement, variableName: string): string | undefined {
  // `--mat-sys-*` tokens are defined as `light-dark(<light>, <dark>)`. Reading a custom property's
  // computed value returns that raw text as-is (functions inside custom properties are only
  // evaluated once consumed by an actual property), so we parse it and pick the matching branch
  // ourselves based on the resolved `color-scheme`.
  const raw = getComputedStyle(element).getPropertyValue(variableName).trim();
  if (!raw) return undefined;

  const parsed = parseLightDark(raw);
  if (!parsed) return raw;

  const [lightValue, darkValue] = parsed;
  return resolveColorScheme(element) === 'dark' ? darkValue : lightValue;
}

export function resolveThemePalette(element: HTMLElement): ChartThemePalette {
  return {
    textColor: resolveCssVariableColor(element, '--mat-sys-on-surface'),
    gridColor: resolveCssVariableColor(element, '--mat-sys-surface-container-highest'),

    // Build the array by resolving the CSS variables we defined in SCSS
    seriesColors: [
      resolveCssVariableColor(element, '--chart-series-1') || '#4285F4',
      resolveCssVariableColor(element, '--chart-series-2') || '#34A853',
      resolveCssVariableColor(element, '--chart-series-3') || '#FBBC04',
      resolveCssVariableColor(element, '--chart-series-4') || '#EA4335',
      resolveCssVariableColor(element, '--chart-series-5') || '#990099',
      resolveCssVariableColor(element, '--chart-series-6') || '#00BCD4',
      resolveCssVariableColor(element, '--chart-series-7') || '#FF6D00',
      resolveCssVariableColor(element, '--chart-series-8') || '#E91E63',
    ],
  };
}
