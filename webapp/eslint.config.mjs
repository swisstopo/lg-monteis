import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';
import angular from 'angular-eslint';

const TRANSLATE_SERVICES = /^(translateService|i18nService)$/;
const INSTANT_MESSAGE =
  'Avoid using static .instant() for translations. Use the reactive .translate()() signal or the TranslatePipe instead to ensure the UI updates automatically on language changes.';

export default defineConfig([
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tseslint.parser,
    },
    plugins: {
      '@typescript-eslint': tseslint.plugin,
    },
    processor: angular.processInlineTemplates,
    rules: {
      '@typescript-eslint/no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'chart.js',
              message:
                'Architecture Violation: Do not use Chart.js directly! Import the shared wrapper component from "@ui/chart" instead.',
            },
          ],
        },
      ],
      'no-restricted-syntax': [
        'error',
        {
          // this.translateService.instant(…) / this.#i18nService.instant(…)
          selector: `CallExpression[callee.property.name='instant'][callee.object.property.name=${TRANSLATE_SERVICES}]`,
          message: INSTANT_MESSAGE,
        },
        {
          // bare local: translateService.instant(…)
          selector: `CallExpression[callee.property.name='instant'][callee.object.name=${TRANSLATE_SERVICES}]`,
          message: INSTANT_MESSAGE,
        },
      ],
    },
  },
  {
    files: ['src/app/ui/chart/**/*.ts'],
    rules: {
      '@typescript-eslint/no-restricted-imports': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
