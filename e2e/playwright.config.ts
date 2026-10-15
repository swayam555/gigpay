import { defineConfig } from '@playwright/test'

// BASE_URL points at the running stack (docker compose up). CHROME_PATH lets you use an installed
// Chrome instead of downloading Playwright's Chromium.
export default defineConfig({
  testDir: './tests',
  timeout: 15 * 60_000,
  expect: { timeout: 10_000 },
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:3003',
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    launchOptions: process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {},
  },
})
