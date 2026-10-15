// End-to-end browser tests against a running stack:  cd e2e && npm install && npx playwright install chromium && npm test
// Every step is something a real user does; console errors and 5xx responses fail the run.
import { expect, test, type Page } from '@playwright/test'

const B = process.env.BASE_URL ?? 'http://localhost:3003'
const section = async (p, name) => { await p.getByRole('button', { name, exact: true }).first().click(); await expect(p.getByRole('heading', { level: 1 })).toContainText(name === 'Bank fault injection' ? 'Bank fault injection' : name === 'Journal' ? 'General journal' : name) }
type Step = [string, (p: Page) => Promise<void>]

const steps: Step[] = [
  ['landing renders with phone demo', async p => { await p.goto(B); await expect(p.getByRole('heading', { level: 1 })).toContainText('Get paid the day'); await expect(p.getByText('Earned and waiting').first()).toBeVisible() }],
  ['landing sections reveal', async p => { for (const id of ['how', 'ledger', 'coach', 'architecture']) { await p.locator('#' + id).scrollIntoViewIfNeeded(); await p.waitForTimeout(400) } await expect(p.getByText("Money can't appear or vanish.")).toBeVisible(); await expect(p.getByText('Σ all balances')).toBeVisible() }],
  ['finance console asks for staff sign-in', async p => { await p.evaluate(() => scrollTo(0, 0)); await p.getByRole('link', { name: 'Finance console' }).first().click(); await expect(p.getByRole('heading', { name: 'Finance console' })).toBeVisible() }],
  ['wrong staff password is rejected', async p => { await p.locator('input[type=password]').fill('nope'); await p.getByRole('button', { name: 'Sign in' }).click(); await expect(p.getByRole('alert')).toBeVisible() }],
  ['finance signs in: books balance', async p => { await p.locator('input[type=password]').fill('gigpay'); await p.getByRole('button', { name: 'Sign in' }).click(); await expect(p.getByRole('heading', { name: 'Overview' })).toBeVisible(); await expect(p.getByText('Books balance').first()).toBeVisible() }],
  // The smoke test leaves the simulator running, so only start it if it isn't already.
  ['start trips: back-fill then live', async p => { const start = p.getByRole('button', { name: 'Start trips' }); await expect(start.or(p.getByRole('button', { name: 'Pause trips' }))).toBeVisible(); if (await start.isVisible()) await start.click(); await expect(p.getByText(/Live · 15 trips\/min/)).toBeVisible({ timeout: 90000 }) }],
  ['worker signs in', async p => {
    // Each worker gets 3 instant payouts a day: use the first worker (same pool as the smoke test) who still has one
    // left, so the suite can be re-run against the same stack. Demo accounts sign in by button, the rest by phone.
    const phones = ['9000000001', '9000000002', '9177777868', '9549008934', '9232931336', '9242995371', '9832294821']
    let chosen = { phone: phones[0], name: 'Ravi Kumar' }
    for (const phone of phones) {
      const s = await (await p.request.post(B + '/api/auth/worker', { data: { phone, pin: '1234' } })).json()
      const w = await (await p.request.get(B + '/api/wallet', { headers: { Authorization: 'Bearer ' + s.accessToken } })).json()
      if (w.payoutsLeftToday > 0 && w.withdrawableNow >= 20000) { chosen = { phone, name: s.name }; break }
    }
    await p.goto(B + '/login')
    const demo = p.getByRole('button', { name: new RegExp(chosen.name) })
    if (await demo.count()) await demo.click(); else await p.getByLabel('Mobile number').fill(chosen.phone)
    await p.getByRole('button', { name: /Sign in/ }).click()
    await expect(p).toHaveURL(/\/app$/); await expect(p.getByRole('heading', { level: 1 })).toContainText(chosen.name.split(' ')[0])
  }],
  ['overview: balance, stats, 7-day chart', async p => { await expect(p.getByText('Earned and waiting for you')).toBeVisible(); await expect(p.locator('.recharts-area').first()).toBeVisible({ timeout: 10000 }); await expect(p.getByText(/\d+ working days?/)).toBeVisible() }],
  ['trips: lowest paid, open a breakdown', async p => { await p.getByRole('link', { name: 'Trips' }).first().click(); await p.getByRole('button', { name: 'Lowest paid' }).click(); await p.locator('ul button[aria-expanded]').first().click(); await expect(p.getByText('How this trip was paid')).toBeVisible(); await expect(p.getByText('You earned').first()).toBeVisible() }],
  ['wallet: below-minimum amount is refused', async p => { await p.getByRole('link', { name: 'Wallet' }).first().click(); await p.getByLabel('Amount in rupees').fill('50'); await p.getByRole('button', { name: /instantly/ }).click(); await expect(p.getByRole('alert')).toContainText('Minimum instant payout') }],
  ['wallet: invalid UPI ID is refused', async p => { await p.waitForTimeout(1500); await p.getByLabel('Amount in rupees').fill('100'); const upi = p.locator('input.font-mono'); const real = await upi.inputValue(); await upi.fill('not-a-upi'); await p.getByRole('button', { name: /instantly/ }).click(); await expect(p.getByRole('alert')).toContainText('valid UPI'); await upi.fill(real) }],
  ['wallet: instant payout reaches the account', async p => { await p.waitForTimeout(1500); await p.getByRole('button', { name: /^50% ·/ }).click(); await p.getByRole('button', { name: /instantly/ }).click(); await expect(p.getByText('In your account')).toBeVisible({ timeout: 10000 }); await expect(p.getByText(/UTR UTR/).first()).toBeVisible({ timeout: 30000 }) }],
  ['wallet: history and ledger statement', async p => { await expect(p.getByText('Instant payout').first()).toBeVisible(); await p.getByRole('button', { name: 'Statement', exact: true }).click(); await expect(p.getByText(/payout requested/).first()).toBeVisible() }],
  ['coach: suggested question uses your data', async p => { await p.getByRole('link', { name: 'Coach' }).first().click(); await p.getByRole('button', { name: 'Why are my earnings less today?' }).click(); await expect(p.getByText('Read your data:')).toBeVisible({ timeout: 15000 }) }],
  ['coach: typed policy question cites sources', async p => { await p.getByLabel('Your question').fill('Do I need to pay income tax?'); await p.getByRole('button', { name: 'Send' }).click(); await expect(p.getByText(/Income Tax Basics/).first()).toBeVisible({ timeout: 15000 }) }],
  ['worker can\'t open finance data', async p => { const r = await p.evaluate(async () => (await fetch('/api/ledger/admin/overview', { headers: { Authorization: 'Bearer ' + JSON.parse(localStorage.getItem('gigpay.session.WORKER')).accessToken } })).status); if (r !== 403) throw new Error('got ' + r) }],
  ['worker signs out', async p => { await p.getByTitle('Sign out').click(); await expect(p).toHaveURL(/\/login$/) }],
  ['finance: payouts table', async p => { await p.goto(B + '/finance'); await section(p, 'Payouts'); await expect(p.locator('tbody tr').first()).toBeVisible() }],
  ['finance: journal entries balance', async p => { await section(p, 'Journal'); await expect(p.getByText(/^Dr /).first()).toBeVisible(); await expect(p.getByText(/^Cr /).first()).toBeVisible() }],
  ['finance: break the bank, run weekly payout', async p => { await section(p, 'Bank fault injection'); const rates = p.locator('input[type=range][max="1"]'); await expect(rates).toHaveCount(4); for (const s of await rates.all()) await s.fill('0.4'); await p.getByRole('button', { name: 'Apply' }).click(); await p.getByRole('button', { name: 'Run weekly payout' }).click(); await expect(p.getByText('Weekly payouts started')).toBeVisible() }],
  ['finance: everything settles, reconciliation clean', async p => { await p.waitForTimeout(75000); await section(p, 'Reconciliation'); await p.getByRole('button', { name: 'Run now' }).click(); await expect(p.getByText(/Every bank transfer matches/)).toBeVisible({ timeout: 15000 }) }],
  ['finance: books still balance', async p => { await section(p, 'Overview'); await expect(p.getByText('Books balance').first()).toBeVisible(); await expect(p.getByText(/Σ = 0/)).toBeVisible() }],
  ['finance: reset faults, pause trips', async p => { await section(p, 'Bank fault injection'); await p.getByRole('button', { name: 'Reset to defaults' }).click(); await p.getByRole('button', { name: 'Pause trips' }).click(); await expect(p.getByRole('button', { name: 'Start trips' })).toBeVisible({ timeout: 10000 }) }],
]

test('worker and finance journey', async ({ page }) => {
  const problems: string[] = []
  page.on('pageerror', e => problems.push(`page error: ${e.message}`))
  page.on('response', r => { if (r.status() >= 500) problems.push(`HTTP ${r.status()} ${r.request().method()} ${r.url()}`) })
  for (const [label, fn] of steps) await test.step(label, () => fn(page))
  expect(problems).toEqual([])
})

test.describe('phone width', () => {
  test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

  test('no page scrolls sideways', async ({ page, request }) => {
    const paths = await (async () => {
  const w = await (await request.post('/api/auth/worker', { data: { phone: '9000000002', pin: '1234' } })).json()
  const a = await (await request.post('/api/auth/admin', { data: { username: 'finance', password: 'gigpay' } })).json()
  await page.addInitScript(([w, a]) => {
    localStorage.setItem('gigpay.session.WORKER', w); localStorage.setItem('gigpay.session.ADMIN', a)
  }, [JSON.stringify(w), JSON.stringify(a)])
  return ['/', '/app', '/app/trips', '/app/wallet', '/app/coach', '/finance']
    })()
    for (const path of paths) {
      await page.goto(path)
      await page.waitForTimeout(1500)
      const width = await page.evaluate(() => document.documentElement.scrollWidth)
      expect(width, `${path} is wider than the screen`).toBeLessThanOrEqual(391)
    }
  })
})
