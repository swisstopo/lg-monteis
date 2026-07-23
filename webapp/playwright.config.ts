import { defineConfig, devices } from '@playwright/test';

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './e2e',
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: 'html',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('')`. */

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  webServer: [
    {
      command: 'SPRING_PROFILES_ACTIVE=e2e-test mvn spring-boot:test-run',
      cwd: '../core',
      // Without this, Playwright has no way to detect readiness and starts running tests
      // immediately, well before Postgres/Keycloak are actually up.
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: !process.env.CI,
      // Booting Postgres + a JVM-based Keycloak Testcontainer is slower than a plain backend start.
      timeout: 180_000,
    },
    {
      // Uses the `e2e` build configuration so it points at public/env.e2e.json (the
      // Testcontainer Keycloak on a fixed port distinct from local dev's), not public/env.json.
      command: 'pnpm run start:e2e',
      cwd: '.',
      url: 'http://localhost:4200',
      reuseExistingServer: !process.env.CI,
    },
  ],
});
