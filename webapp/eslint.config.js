// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
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
          // Targets any method call named "instant"
          selector: "CallExpression[callee.property.name='translateService.instant']",
          message:
            'Avoid using static .instant() for translations. Use the reactive .translate()() signal or the TranslatePipe instead to ensure the UI updates automatically on language changes.',
        },
        'error',
        {
          // Targets any method call named "instant"
          selector: "CallExpression[callee.property.name='i18nService.instant']",
          message:
            'Avoid using static .instant() for translations. Use the reactive .translate()() signal or the TranslatePipe instead to ensure the UI updates automatically on language changes.',
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
