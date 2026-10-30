import angular from '@analogjs/vite-plugin-angular';
import { playwright } from '@vitest/browser-playwright';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [angular()],
  // Mirror the tsconfig path aliases for Vite/Vitest, which does not read
  // tsconfig `paths`. Scoped to the app's top-level dirs so npm scopes
  // (@angular, @ngx-translate, ...) keep resolving normally.
  resolve: {
    alias: [
      {
        find: /^@public\//,
        replacement: fileURLToPath(new URL('./public/', import.meta.url)),
      },
      {
        find: /^@(config|core|features|ui)\//,
        replacement: fileURLToPath(new URL('./src/app/$1/', import.meta.url)),
      },
    ],
  },
  // scan drops type-only imports, Angular plugin keeps them: found mid-run => re-bundle reloads open spec iframes
  optimizeDeps: { include: ['@angular/router'] },
  test: {
    include: ['src/**/*.spec.ts'],
    exclude: ['e2e/**', 'node_modules/**'],
    setupFiles: ['./vitest.setup.ts'],
    globals: true,
    // busy CI runner can need > 60s default to boot a spec iframe, read by @vitest/browser orchestrator
    env: { VITEST_BROWSER_IFRAME_TIMEOUT: '180000' },
    browser: {
      provider: playwright(),
      enabled: true,
      instances: [{ browser: 'chromium' }],
    },
  },
});
