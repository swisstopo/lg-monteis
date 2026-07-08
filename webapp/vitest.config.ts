import angular from '@analogjs/vite-plugin-angular';
import { playwright } from '@vitest/browser-playwright';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [angular()],
  test: {
    include: ['src/**/*.spec.ts'],
    exclude: ['e2e/**', 'node_modules/**'],
    setupFiles: ['./vitest.setup.ts'],
    globals: true,
    browser: {
      provider: playwright(),
      enabled: true,
      instances: [{ browser: 'chromium' }],
    },
  },
});
