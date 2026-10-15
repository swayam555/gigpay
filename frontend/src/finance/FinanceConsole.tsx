import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import clsx from 'clsx'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowLeft, BookText, CheckCircle2, CircleAlert, FlaskConical, LayoutDashboard, ListChecks, LogOut, Pause, Play, RefreshCw, Send } from 'lucide-react'
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { dateTime, friendlyReason, PAYOUT_LABEL, platformName, rupees, send, type Payout, type Session } from '../api'
import { useAuth } from '../auth'
import { useApi } from '../hooks'
import { Badge, Button, Card, Empty, ErrorNote, Logo, PAYOUT_TONE, Skeleton } from '../components/ui'

interface TrialBalance { sumOfBalances: number; sumOfPostings: number; unbalancedEntries: number; accountsWithDrift: number; entries: number; balanced: boolean }
interface LedgerOverview {
  bankFloat: number; owedToWorkers: number; receivableFromPlatforms: number; inTransit: number; feeIncome: number; capital: number
  receivablesByPlatform: { platform: string; balance: number }[]
  payoutsByStatus: { status: string; kind: string; count: number; amount: number }[]
  medianSecondsToMoney: number; workersWithBalance: number; trialBalance: TrialBalance
}
interface Simulator { running: boolean; backfilling: boolean; tripsPerMinute: number; tripsSent: number }
interface EarningsOverview { lastHour: { platform: string; trips: number; net: number; workers: number }[]; tripsTotal: number; simulator: Simulator }
interface Report { ranAt: string; bankTransfers: number; bankSucceeded: number; bankPaidPaise: number; inFlightPaise: number; trialBalance: TrialBalance; issues: { type: string; reference: string; detail: string }[] }
interface Faults { failureRate: number; dropCallbackRate: number; duplicateCallbackRate: number; rateLimitRate: number; settleMillis: number }
interface JournalEntry { id: number; kind: string; description: string; createdAt: string; legs: { account: string; amount: number }[] }

type Section = 'overview' | 'payouts' | 'journal' | 'recon' | 'faults'
const SECTIONS: { id: Section; label: string; icon: typeof LayoutDashboard }[] = [
  { id: 'overview', label: 'Overview', icon: LayoutDashboard },
  { id: 'payouts', label: 'Payouts', icon: Send },
  { id: 'journal', label: 'Journal', icon: BookText },
  { id: 'recon', label: 'Reconciliation', icon: ListChecks },
  { id: 'faults', label: 'Bank fault injection', icon: FlaskConical },
]

export default function FinanceConsole() {
  return useAuth().admin ? <Dashboard /> : <AdminLogin />
}

function AdminLogin() {
  const auth = useAuth()
  const [username, setUsername] = useState('finance')
  const [password, setPassword] = useState('gigpay')
  const [error, setError] = useState<string | null>(null)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try { auth.signIn(await send<Session>('/api/auth/admin', 'POST', { username, password })) }
    catch (err) { setError((err as Error).message) }
  }
  return (
    <div className="flex min-h-screen flex-col bg-ink px-6 py-8">
      <Link to="/" className="inline-flex w-fit items-center gap-1.5 text-sm text-white/50 hover:text-white"><ArrowLeft className="h-4 w-4" /> Back to overview</Link>
      <motion.form onSubmit={submit} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
        className="m-auto w-full max-w-sm rounded-2xl border border-white/10 bg-surface p-7 shadow-2xl">
        <Logo />
        <h1 className="mt-6 text-xl font-semibold tracking-tight text-ink">Finance console</h1>
        <p className="mt-1 text-sm text-muted">Books, payouts and reconciliation. Staff only.</p>
        <div className="mt-6 space-y-4">
          <label className="label">Username<input className="input" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
          <label className="label">Password<input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>
          <ErrorNote error={error} />
          <Button type="submit" size="lg" className="w-full">Sign in</Button>
          <p className="text-center text-xs text-muted">Demo credentials are pre-filled: finance / gigpay</p>
        </div>
      </motion.form>
    </div>
  )
}

function Dashboard() {
  const auth = useAuth()
  const [section, setSection] = useState<Section>('overview')
  const ledger = useApi<LedgerOverview>('/api/ledger/admin/overview', 3000)
  const earnings = useApi<EarningsOverview>('/api/earnings/admin/overview', 3000)
  const tb = ledger.data?.trialBalance

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[248px_1fr] lg:bg-[linear-gradient(to_right,#ffffff_248px,transparent_248px)]">
      <aside className="sticky top-0 hidden h-screen flex-col border-r border-line bg-surface lg:flex">
        <div className="flex h-16 items-center gap-2 border-b border-line px-5"><Logo /><span className="rounded-md bg-ink/[0.06] px-1.5 py-0.5 text-[11px] font-medium text-ink-2">Finance</span></div>
        <nav className="flex-1 space-y-0.5 p-3">
          {SECTIONS.map(s => (
            <button key={s.id} onClick={() => setSection(s.id)}
              className={clsx('focus-ring relative flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition',
                section === s.id ? 'text-ink' : 'text-muted hover:bg-ink/[0.04] hover:text-ink')}>
              {section === s.id && <motion.span layoutId="side-active" className="absolute inset-0 rounded-lg bg-ink/[0.06]" transition={{ type: 'spring', stiffness: 500, damping: 40 }} />}
              <s.icon className="relative h-4 w-4" strokeWidth={1.75} /><span className="relative">{s.label}</span>
            </button>
          ))}
        </nav>
        <div className="m-3 rounded-xl border border-line bg-paper p-3.5">
          <div className="flex items-center gap-2 text-xs font-medium text-ink">
            {tb ? (tb.balanced ? <CheckCircle2 className="h-4 w-4 text-pos" /> : <CircleAlert className="h-4 w-4 text-neg" />) : <span className="h-4 w-4" />}
            {tb ? (tb.balanced ? 'Books balance' : 'Books do not balance') : 'Checking books…'}
          </div>
          <div className="num mt-1 text-xs text-muted">{tb ? `${tb.entries.toLocaleString('en-IN')} entries · Σ = ${tb.sumOfBalances}` : ' '}</div>
        </div>
        <div className="flex items-center justify-between border-t border-line px-5 py-4 text-sm">
          <span className="text-muted">Finance team</span>
          <button onClick={() => auth.signOut('ADMIN')} className="focus-ring rounded-md p-1.5 text-muted hover:bg-ink/5 hover:text-ink" title="Sign out"><LogOut className="h-4 w-4" /></button>
        </div>
      </aside>

      <div className="min-w-0">
        <SimulatorBar sim={earnings.data?.simulator} trips={earnings.data?.tripsTotal} onChange={() => { ledger.reload(); earnings.reload() }} />
        <div className="flex gap-1 overflow-x-auto border-b border-line bg-surface px-3 py-2 lg:hidden">
          {SECTIONS.map(s => (
            <button key={s.id} onClick={() => setSection(s.id)} className={clsx('whitespace-nowrap rounded-md px-3 py-1.5 text-sm', section === s.id ? 'bg-ink text-white' : 'text-muted')}>{s.label}</button>
          ))}
        </div>
        <main className="mx-auto max-w-[1200px] space-y-5 p-5 lg:p-8">
          <ErrorNote error={ledger.error ?? earnings.error} />
          <AnimatePresence mode="wait">
            <motion.div key={section} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }} className="space-y-5">
              {section === 'overview' && <Overview l={ledger.data} e={earnings.data} />}
              {section === 'payouts' && <Payouts />}
              {section === 'journal' && <Journal />}
              {section === 'recon' && <Reconciliation />}
              {section === 'faults' && <FaultPanel />}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  )
}

function PageTitle({ title, children, action }: { title: string; children?: React.ReactNode; action?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div><h1 className="text-[24px] font-semibold tracking-[-0.03em] text-ink">{title}</h1>{children && <p className="mt-1 max-w-2xl text-sm text-muted">{children}</p>}</div>
      {action}
    </div>
  )
}

function SimulatorBar({ sim, trips, onChange }: { sim?: Simulator; trips?: number; onChange: () => void }) {
  const [rate, setRate] = useState(15)
  const [busy, setBusy] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const act = async (name: string, path: string, body?: unknown, done?: string) => {
    setBusy(name); setMsg(null)
    try { await send(path, 'POST', body); onChange(); if (done) { setMsg(done); setTimeout(() => setMsg(null), 2500) } }
    catch (e) { setMsg((e as Error).message) }
    finally { setBusy(null) }
  }
  return (
    <div className="sticky top-0 z-10 flex flex-wrap items-center gap-3 border-b border-line bg-paper/90 px-5 py-3 backdrop-blur lg:px-8">
      <div className="flex items-center gap-2.5">
        <span className={clsx('relative flex h-2.5 w-2.5', sim?.running ? 'text-pos' : 'text-muted')}>
          {sim?.running && <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-current opacity-50" />}
          <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-current" />
        </span>
        <span className="text-sm font-medium text-ink">Partner platforms</span>
        <span className="text-sm text-muted">{!sim ? '…' : sim.backfilling ? 'Back-filling 6 days of trips…' : sim.running ? `Live · ${sim.tripsPerMinute} trips/min` : 'Paused'}</span>
        {trips !== undefined && <span className="num hidden text-xs text-muted xl:inline">· {trips.toLocaleString('en-IN')} trips stored</span>}
      </div>
      <AnimatePresence>{msg && <motion.span initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="text-xs text-pos">{msg}</motion.span>}</AnimatePresence>
      <div className="ml-auto flex flex-wrap items-center gap-2">
        {!sim?.running && (
          <label className="flex items-center gap-2 text-xs text-muted">Trips/min
            <input type="number" min={1} max={600} value={rate} onChange={e => setRate(Number(e.target.value))}
              className="num h-8 w-16 rounded-md border border-line-2 bg-surface px-2 text-sm text-ink outline-none focus:border-forest" /></label>
        )}
        {sim?.running
          ? <Button size="sm" variant="secondary" disabled={!!busy} onClick={() => act('sim', '/api/earnings/admin/simulator/stop')}><Pause className="h-3.5 w-3.5" /> Pause trips</Button>
          : <Button size="sm" disabled={!!busy} onClick={() => act('sim', '/api/earnings/admin/simulator/start', { tripsPerMinute: rate })}><Play className="h-3.5 w-3.5" /> Start trips</Button>}
        <Button size="sm" variant="secondary" disabled={!!busy} onClick={() => act('settle', '/api/ledger/admin/jobs/settle-platforms', undefined, 'Platforms settled')}>Settle platforms</Button>
        <Button size="sm" variant="secondary" disabled={!!busy} onClick={() => act('weekly', '/api/ledger/admin/jobs/weekly-payout', undefined, 'Weekly payouts started')}>Run weekly payout</Button>
      </div>
    </div>
  )
}

function Overview({ l, e }: { l?: LedgerOverview; e?: EarningsOverview }) {
  const kpis = [
    { label: 'Bank float', value: l && rupees(l.bankFloat), hint: 'Cash available for payouts' },
    { label: 'Owed to workers', value: l && rupees(l.owedToWorkers), hint: l && `${l.workersWithBalance} workers with a balance` },
    { label: 'Platforms owe us', value: l && rupees(l.receivableFromPlatforms), hint: 'Settled on schedule' },
    { label: 'In transit', value: l && rupees(l.inTransit), hint: 'Sent, awaiting the bank' },
    { label: 'Fee income', value: l && rupees(l.feeIncome), hint: '₹5 per instant payout' },
    { label: 'Median time to money', value: l && `${l.medianSecondsToMoney.toFixed(1)} s`, hint: 'Instant payouts, last hour' },
  ]
  const byStatus = (l?.payoutsByStatus ?? []).reduce<Record<string, number>>((m, p) => ({ ...m, [p.status]: (m[p.status] ?? 0) + p.count }), {})
  return (
    <>
      <PageTitle title="Overview">Where every rupee is right now. Figures refresh every 3 seconds.</PageTitle>
      <div className="grid grid-cols-2 gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line md:grid-cols-3">
        {kpis.map(k => (
          <div key={k.label} className="bg-surface p-5">
            <div className="text-[13px] text-muted">{k.label}</div>
            <div className="num mt-2 text-[26px] font-semibold leading-none tracking-tight text-ink">{k.value ?? <Skeleton className="h-7 w-28" />}</div>
            <div className="mt-2 text-xs text-muted">{k.hint ?? ' '}</div>
          </div>
        ))}
      </div>
      <div className="grid gap-5 xl:grid-cols-3">
        <Card title="Trial balance" subtitle="Debits equal credits in every entry">
          {l ? <TrialBalanceView tb={l.trialBalance} /> : <Skeleton className="h-36" />}
        </Card>
        <Card title="Receivable by platform" subtitle="Earnings fronted, not yet settled">
          <div className="h-40">
            {l && (
              <ResponsiveContainer>
                <BarChart data={l.receivablesByPlatform.map(r => ({ ...r, name: platformName(r.platform), rupees: r.balance / 100 }))}
                  layout="vertical" margin={{ left: 0, right: 12 }}>
                  <XAxis type="number" hide /><YAxis type="category" dataKey="name" width={84} fontSize={12} tickLine={false} axisLine={false} tick={{ fill: '#3b4640' }} />
                  <Tooltip cursor={{ fill: '#f6f5f1' }} formatter={v => [rupees(Number(v) * 100), 'Owed to GigPay']}
                    contentStyle={{ borderRadius: 10, border: '1px solid #e4e2da', fontSize: 13 }} />
                  <Bar dataKey="rupees" fill="#17402a" radius={[0, 6, 6, 0]} barSize={18} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>
        <Card title="Payouts, last hour" subtitle="Instant and weekly">
          {l ? (
            <div className="space-y-2.5">
              {(['SUCCEEDED', 'SUBMITTED', 'RETRY', 'FAILED'] as const).map(s => (
                <div key={s} className="flex items-center justify-between text-sm">
                  <Badge tone={PAYOUT_TONE[s]} dot>{PAYOUT_LABEL[s]}</Badge>
                  <span className="num font-medium text-ink">{byStatus[s] ?? 0}</span>
                </div>
              ))}
            </div>
          ) : <Skeleton className="h-36" />}
        </Card>
      </div>
      <Card title="Trip webhooks received, last hour" padded={false}>
        {e && e.lastHour.length === 0 && <Empty title="No trips in the last hour">Start the simulated platforms from the bar above.</Empty>}
        {e && e.lastHour.length > 0 && (
          <div className="mt-2 overflow-x-auto"><table className="w-full min-w-[480px] text-sm">
            <thead><tr className="border-b border-line text-left text-xs text-muted"><th className="px-5 py-2.5 font-medium">Platform</th><th className="px-5 py-2.5 text-right font-medium">Workers</th><th className="px-5 py-2.5 text-right font-medium">Trips</th><th className="px-5 py-2.5 text-right font-medium">Earned by workers</th></tr></thead>
            <tbody className="divide-y divide-line">
              {e.lastHour.map(p => (
                <tr key={p.platform}><td className="px-5 py-3 font-medium text-ink">{p.platform}</td><td className="num px-5 py-3 text-right text-ink-2">{p.workers}</td>
                  <td className="num px-5 py-3 text-right text-ink-2">{p.trips.toLocaleString('en-IN')}</td><td className="num px-5 py-3 text-right font-medium text-ink">{rupees(p.net)}</td></tr>
              ))}
            </tbody>
          </table></div>
        )}
        {!e && <div className="p-5"><Skeleton className="h-24" /></div>}
      </Card>
    </>
  )
}

function TrialBalanceView({ tb }: { tb: TrialBalance }) {
  return (
    <div>
      <div className={clsx('flex items-center gap-2 text-[15px] font-semibold', tb.balanced ? 'text-pos' : 'text-neg')}>
        {tb.balanced ? <CheckCircle2 className="h-5 w-5" /> : <CircleAlert className="h-5 w-5" />}{tb.balanced ? 'Books balance' : 'Books do NOT balance'}
      </div>
      <dl className="mt-4 space-y-2 text-sm">
        {[['Journal entries', tb.entries.toLocaleString('en-IN')], ['Sum of all balances', tb.sumOfBalances], ['Unbalanced entries', tb.unbalancedEntries], ['Accounts with drift', tb.accountsWithDrift]].map(([k, v]) => (
          <div key={k} className="flex justify-between"><dt className="text-muted">{k}</dt><dd className="num font-medium text-ink">{v}</dd></div>
        ))}
      </dl>
    </div>
  )
}

function Payouts() {
  const [status, setStatus] = useState('')
  const { data } = useApi<Payout[]>(`/api/ledger/admin/payouts?limit=100${status ? `&status=${status}` : ''}`, 3000)
  return (
    <>
      <PageTitle title="Payouts" action={
        <select value={status} onChange={e => setStatus(e.target.value)} aria-label="Filter by status"
          className="h-9 rounded-lg border border-line-2 bg-surface px-3 text-sm text-ink outline-none focus:border-forest">
          <option value="">All statuses</option>{Object.keys(PAYOUT_LABEL).map(s => <option key={s} value={s}>{PAYOUT_LABEL[s]}</option>)}
        </select>}>The most recent 100, refreshed every 3 seconds.</PageTitle>
      <Card padded={false}>
        {data?.length === 0 && <Empty title="No payouts match" />}
        {!data && <div className="space-y-2 p-5">{[0, 1, 2, 3].map(i => <Skeleton key={i} className="h-9" />)}</div>}
        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[920px] text-sm">
              <thead><tr className="border-b border-line text-left text-xs text-muted">
                <th className="px-5 py-3 font-medium">Payout</th><th className="px-3 py-3 font-medium">Worker</th><th className="px-3 py-3 font-medium">Type</th>
                <th className="px-3 py-3 text-right font-medium">Amount</th><th className="px-3 py-3 text-right font-medium">Fee</th><th className="px-3 py-3 font-medium">Status</th>
                <th className="px-3 py-3 text-right font-medium">Attempts</th><th className="px-3 py-3 font-medium">Bank reference</th><th className="px-5 py-3 text-right font-medium">Created</th>
              </tr></thead>
              <tbody className="divide-y divide-line">
                {data.map(p => (
                  <tr key={p.id} className="transition hover:bg-paper/70">
                    <td className="px-5 py-3 font-mono text-xs text-ink-2">{p.id}</td>
                    <td className="px-3 py-3 font-mono text-xs text-ink-2">{p.workerId}</td>
                    <td className="px-3 py-3 text-ink-2">{p.kind === 'INSTANT' ? 'Instant' : 'Weekly'}</td>
                    <td className="num px-3 py-3 text-right font-medium text-ink">{rupees(p.amount, true)}</td>
                    <td className="num px-3 py-3 text-right text-muted">{p.fee ? rupees(p.fee) : '-'}</td>
                    <td className="px-3 py-3"><Badge tone={PAYOUT_TONE[p.status]} dot>{PAYOUT_LABEL[p.status]}</Badge></td>
                    <td className="num px-3 py-3 text-right text-ink-2">{p.attempts}</td>
                    <td className="px-3 py-3 text-xs">{p.status === 'FAILED' && p.failureReason
                      ? <span className="text-neg">{friendlyReason(p.failureReason)}</span> : <span className="font-mono text-ink-2">{p.providerRef ?? '-'}</span>}</td>
                    <td className="px-5 py-3 text-right text-xs text-muted">{dateTime(p.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}

function Journal() {
  const { data } = useApi<JournalEntry[]>('/api/ledger/admin/journal?limit=60', 3000)
  return (
    <>
      <PageTitle title="General journal">Append-only. Debits are shown as Dr and credits as Cr; every entry sums to zero.</PageTitle>
      <Card padded={false}>
        {!data && <div className="space-y-2 p-5">{[0, 1, 2].map(i => <Skeleton key={i} className="h-16" />)}</div>}
        <ul className="divide-y divide-line">
          {(data ?? []).map(e => (
            <li key={e.id} className="grid gap-3 px-5 py-4 md:grid-cols-[1fr_1.1fr]">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="num font-mono text-xs text-muted">#{e.id}</span><Badge>{e.kind.replaceAll('_', ' ').toLowerCase()}</Badge>
                </div>
                <div className="mt-1.5 text-sm text-ink">{e.description}</div>
                <div className="mt-0.5 text-xs text-muted">{dateTime(e.createdAt)}</div>
              </div>
              <div className="space-y-1 rounded-lg bg-paper px-3.5 py-2.5 font-mono text-xs">
                {e.legs.map((leg, i) => (
                  <div key={i} className="flex justify-between gap-4">
                    <span className={clsx('truncate', leg.amount < 0 ? 'pl-5 text-muted' : 'text-ink-2')}>{leg.account}</span>
                    <span className="num shrink-0 text-ink">{leg.amount > 0 ? 'Dr' : 'Cr'} {rupees(Math.abs(leg.amount), true)}</span>
                  </div>
                ))}
              </div>
            </li>
          ))}
        </ul>
      </Card>
    </>
  )
}

function Reconciliation() {
  const { data, reload } = useApi<Report>('/api/ledger/admin/reconciliation')
  const [busy, setBusy] = useState(false)
  const run = async () => { setBusy(true); try { await send('/api/ledger/admin/reconciliation/run', 'POST'); reload() } finally { setBusy(false) } }
  return (
    <>
      <PageTitle title="Reconciliation" action={<Button variant="secondary" onClick={run} disabled={busy}><RefreshCw className={clsx('h-4 w-4', busy && 'animate-spin')} /> Run now</Button>}>
        The bank's own records against the ledger. Runs every minute; transfers younger than 30 seconds are skipped while callbacks arrive.
      </PageTitle>
      {!data ? <Skeleton className="h-40" /> : (
        <>
          <div className="grid grid-cols-2 gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line md:grid-cols-4">
            {[['Bank transfers', data.bankTransfers.toLocaleString('en-IN'), `${data.bankSucceeded} succeeded`], ['Paid per the bank', rupees(data.bankPaidPaise), 'successful transfers'],
              ['In flight', rupees(data.inFlightPaise), 'matches the clearing account'], ['Issues', String(data.issues.length), `ran ${dateTime(data.ranAt)}`]].map(([k, v, h]) => (
              <div key={k} className="bg-surface p-5"><div className="text-[13px] text-muted">{k}</div>
                <div className={clsx('num mt-2 text-[26px] font-semibold leading-none tracking-tight', k === 'Issues' ? (data.issues.length ? 'text-neg' : 'text-pos') : 'text-ink')}>{v}</div>
                <div className="mt-2 text-xs text-muted">{h}</div></div>
            ))}
          </div>
          <Card>
            {data.issues.length === 0
              ? <div className="flex items-center gap-2.5 text-sm text-ink"><CheckCircle2 className="h-5 w-5 text-pos" /> Every bank transfer matches a payout in the ledger, nothing was paid twice, and the trial balance is zero.</div>
              : <ul className="space-y-2">{data.issues.map((i, n) => (
                <li key={n} className="flex gap-3 rounded-lg border border-neg/20 bg-neg/[0.05] px-3.5 py-2.5 text-sm"><Badge tone="neg">{i.type.replaceAll('_', ' ').toLowerCase()}</Badge><span className="font-mono text-xs text-ink-2">{i.reference}</span><span className="text-ink">{i.detail}</span></li>))}</ul>}
          </Card>
        </>
      )}
    </>
  )
}

function FaultPanel() {
  const { data, reload } = useApi<Faults>('/api/ledger/admin/faults')
  const [f, setF] = useState<Faults | null>(null)
  const [saved, setSaved] = useState(false)
  useEffect(() => { if (data) setF(data) }, [data])
  const fields: [keyof Faults, string, string][] = [
    ['failureRate', 'Bank rejects the transfer', 'Retried under a new bank reference, then reversed'],
    ['dropCallbackRate', 'Confirmation webhook is lost', 'Recovered by polling the bank after 15 s'],
    ['duplicateCallbackRate', 'Confirmation webhook arrives twice', 'The second copy is ignored'],
    ['rateLimitRate', 'Bank rate-limits us (HTTP 429)', 'Retried with backoff under the same reference'],
  ]
  const save = async (next: Faults) => { await send('/api/ledger/admin/faults', 'PUT', next); setSaved(true); setTimeout(() => setSaved(false), 1500); reload() }
  return (
    <>
      <PageTitle title="Bank fault injection" action={f && <div className="flex gap-2">
        <Button variant="ghost" onClick={() => { const calm = { failureRate: 0.05, dropCallbackRate: 0.05, duplicateCallbackRate: 0.05, rateLimitRate: 0.02, settleMillis: 2500 }; setF(calm); save(calm) }}>Reset to defaults</Button>
        <Button onClick={() => save(f)}>{saved ? 'Applied' : 'Apply'}</Button></div>}>
        Make the mock payout bank misbehave, then run the weekly payout and watch the system cope. The books stay balanced throughout.
      </PageTitle>
      {!f ? <Skeleton className="h-64" /> : (
        <Card>
          <div className="grid gap-x-10 gap-y-7 md:grid-cols-2">
            {fields.map(([k, label, how]) => (
              <label key={k} className="block">
                <div className="flex items-baseline justify-between"><span className="text-sm font-medium text-ink">{label}</span><span className="num font-mono text-sm text-ink">{Math.round((f[k] as number) * 100)}%</span></div>
                <input type="range" min={0} max={1} step={0.05} value={f[k]} onChange={e => setF({ ...f, [k]: Number(e.target.value) })} className="mt-3 w-full accent-forest" />
                <div className="mt-1 text-xs text-muted">{how}</div>
              </label>
            ))}
            <label className="block">
              <div className="flex items-baseline justify-between"><span className="text-sm font-medium text-ink">Extra settlement delay</span><span className="num font-mono text-sm text-ink">{(f.settleMillis / 1000).toFixed(1)} s</span></div>
              <input type="range" min={0} max={20000} step={500} value={f.settleMillis} onChange={e => setF({ ...f, settleMillis: Number(e.target.value) })} className="mt-3 w-full accent-forest" />
              <div className="mt-1 text-xs text-muted">How long the bank takes to settle a transfer</div>
            </label>
          </div>
        </Card>
      )}
    </>
  )
}
