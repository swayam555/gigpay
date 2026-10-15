import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import clsx from 'clsx'
import { AnimatePresence, motion } from 'motion/react'
import { Check, Loader2, Undo2 } from 'lucide-react'
import { api, dateTime, friendlyReason, PAYOUT_LABEL, rupees, send, type Payout, type StatementLine, type Wallet, type Worker } from '../api'
import { useApi } from '../hooks'
import { Badge, Button, Card, Empty, ErrorNote, PAYOUT_TONE, Segmented, Skeleton } from '../components/ui'

const DONE = ['SUCCEEDED', 'FAILED']

export default function WalletPage() {
  const me = useApi<Worker>('/api/earnings/me')
  const wallet = useApi<Wallet>('/api/wallet', 5000)
  const payouts = useApi<Payout[]>('/api/wallet/payouts', 5000)
  const statement = useApi<StatementLine[]>('/api/wallet/statement?limit=40', 10000)
  const w = wallet.data
  const { reload: reloadWallet } = wallet, { reload: reloadPayouts } = payouts, { reload: reloadStatement } = statement
  const reloadAll = useCallback(() => { reloadWallet(); reloadPayouts(); reloadStatement() }, [reloadWallet, reloadPayouts, reloadStatement])

  const [amount, setAmount] = useState('')
  const [upi, setUpi] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [current, setCurrent] = useState<Payout | null>(null)
  const [tab, setTab] = useState<'payouts' | 'statement'>('payouts')
  // One key per withdrawal attempt: a double tap or a network retry can never pay twice.
  const idempotencyKey = useRef(crypto.randomUUID())

  useEffect(() => { if (me.data && !upi) setUpi(me.data.upiId) }, [me.data, upi])

  // Follow the payout until the bank confirms or rejects it.
  useEffect(() => {
    if (!current || DONE.includes(current.status)) return
    const id = setInterval(() => api<Payout>(`/api/wallet/payouts/${current.id}`).then(p => {
      setCurrent(p)
      if (DONE.includes(p.status)) reloadAll()
    }).catch(() => {}), 1000)
    return () => clearInterval(id)
  }, [current, reloadAll])

  const withdraw = async (e: FormEvent) => {
    e.preventDefault()
    setError(null); setBusy(true)
    try {
      const p = await send<Payout>('/api/wallet/withdrawals', 'POST',
        { amountPaise: Math.round(Number(amount) * 100), upiId: upi }, { 'Idempotency-Key': idempotencyKey.current })
      setCurrent(p)
      idempotencyKey.current = crypto.randomUUID()
      setAmount('')
      reloadAll()
    } catch (err) { setError((err as Error).message) }
    finally { setBusy(false) }
  }

  const quick = w ? [0.25, 0.5, 1].map(f => ({ f, v: Math.floor((w.withdrawableNow * f) / 10000) * 100 })).filter(q => q.v * 100 >= w.minPayout) : []

  return (
    <>
      <div>
        <h1 className="text-[26px] font-semibold tracking-[-0.03em] text-ink">Wallet</h1>
        <p className="mt-1 text-sm text-muted">Take money you've already earned, or let it go out free with the weekly payout.</p>
      </div>
      <ErrorNote error={wallet.error} />

      <div className="grid gap-5 lg:grid-cols-[1.35fr_1fr]">
        <Card title="Get paid now" subtitle="Sent to your UPI ID in a few seconds">
          <div className="grid grid-cols-2 gap-4 rounded-xl bg-paper p-4">
            <div>
              <div className="text-[13px] text-muted">Available to withdraw</div>
              <div className="num mt-1 text-[30px] font-semibold leading-none tracking-tight text-forest">{w ? rupees(w.withdrawableNow) : <Skeleton className="h-8 w-28" />}</div>
            </div>
            <div className="text-right">
              <div className="text-[13px] text-muted">Earned, not yet paid</div>
              <div className="num mt-1 text-lg font-semibold text-ink">{w ? rupees(w.owed, true) : '…'}</div>
            </div>
          </div>

          <form onSubmit={withdraw} className="mt-5 space-y-4">
            <div className="grid gap-4 sm:grid-cols-[0.8fr_1.2fr]">
              <label className="label">Amount
                <div className="mt-1.5 flex rounded-lg border border-line-2 bg-surface focus-within:border-forest focus-within:ring-4 focus-within:ring-forest/10">
                  <span className="flex items-center pl-3 text-muted">₹</span>
                  <input className="num w-full bg-transparent px-2 py-2.5 text-sm outline-none" inputMode="numeric" aria-label="Amount in rupees"
                    placeholder={w ? String(w.withdrawableNow / 100) : ''} value={amount} onChange={e => setAmount(e.target.value.replace(/\D/g, ''))} />
                </div>
              </label>
              <label className="label">To UPI ID<input className="input font-mono" value={upi} onChange={e => setUpi(e.target.value.trim())} /></label>
            </div>
            {quick.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {quick.map(({ f, v }) => (
                  <button type="button" key={f} onClick={() => setAmount(String(v))}
                    className={clsx('focus-ring rounded-full border px-3 py-1 text-xs font-medium transition', amount === String(v) ? 'border-forest bg-forest text-white' : 'border-line-2 bg-surface text-ink-2 hover:border-forest')}>
                    {f === 1 ? 'Max' : `${f * 100}%`} · ₹{v.toLocaleString('en-IN')}
                  </button>
                ))}
              </div>
            )}
            <ErrorNote error={error} />
            <Button type="submit" size="lg" disabled={busy || !amount} className="w-full">
              {busy ? <><Loader2 className="h-4 w-4 animate-spin" /> Sending…</> : <>Send {amount ? `₹${Number(amount).toLocaleString('en-IN')}` : ''} instantly{w ? ` · ₹${w.instantFee / 100} fee` : ''}</>}
            </Button>
          </form>
          {w && (
            <ul className="mt-5 grid gap-2 border-t border-line pt-4 text-[13px] text-muted sm:grid-cols-3">
              <li><span className="block text-ink">Not a loan</span>No interest, no credit check.</li>
              <li><span className="block text-ink">{w.withdrawablePercent}% available</span>The rest covers reversed trips.</li>
              <li><span className="block text-ink">{w.payoutsLeftToday} left today</span>{rupees(w.dailyLimitLeft)} of daily limit.</li>
            </ul>
          )}
        </Card>

        <Card title="Latest payout" subtitle="Live status from the bank">
          <AnimatePresence mode="wait">
            {current ? <PayoutProgress key={current.id} p={current} />
              : <motion.div key="empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }}><Empty title="Nothing in flight">Send a payout and follow it here, step by step.</Empty></motion.div>}
          </AnimatePresence>
        </Card>
      </div>

      <Card padded={false} title={tab === 'payouts' ? 'Payout history' : 'Statement'}
        subtitle={tab === 'payouts' ? 'Instant and weekly payouts' : 'Every rupee in and out, straight from the ledger'}
        action={<Segmented value={tab} onChange={setTab} options={[{ value: 'payouts', label: 'Payouts' }, { value: 'statement', label: 'Statement' }]} />}>
        {tab === 'payouts' ? (
          <ul className="mt-2 divide-y divide-line">
            {payouts.data?.length === 0 && <Empty title="No payouts yet" />}
            {(payouts.data ?? []).map(p => (
              <li key={p.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3.5">
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-ink">{p.kind === 'INSTANT' ? 'Instant payout' : 'Weekly payout'} <span className="font-normal text-muted">to {p.upiId}</span></div>
                  <div className="mt-0.5 text-xs text-muted">{dateTime(p.createdAt)}{p.providerRef && <> · UTR <span className="font-mono">{p.providerRef}</span></>}
                    {p.attempts > 1 && ` · ${p.attempts} attempts`}{p.failureReason && p.status === 'FAILED' && <span className="text-neg"> · {friendlyReason(p.failureReason)}</span>}</div>
                </div>
                <Badge tone={PAYOUT_TONE[p.status]} dot>{PAYOUT_LABEL[p.status] ?? p.status}</Badge>
                <div className="num w-28 text-right text-[15px] font-semibold text-ink">{rupees(p.amount, true)}</div>
              </li>
            ))}
          </ul>
        ) : (
          <ul className="mt-2 divide-y divide-line">
            {(statement.data ?? []).map(l => (
              <li key={l.entryId} className="flex items-center gap-4 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm text-ink">{l.description}</div>
                  <div className="mt-0.5 text-xs text-muted">{dateTime(l.createdAt)} · {l.kind.replaceAll('_', ' ').toLowerCase()}</div>
                </div>
                {/* A credit to the worker's payable (negative posting) means GigPay owes them more. */}
                <div className={clsx('num text-sm font-semibold', l.amount < 0 ? 'text-pos' : 'text-ink')}>{l.amount < 0 ? '+' : '−'}{rupees(Math.abs(l.amount), true)}</div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  )
}

function PayoutProgress({ p }: { p: Payout }) {
  const failed = p.status === 'FAILED'
  const steps = [
    { label: 'Requested', detail: dateTime(p.createdAt), done: true },
    { label: p.status === 'RETRY' ? 'Bank busy, retrying' : 'Sent to the bank', detail: p.attempts > 1 ? `${p.attempts} attempts` : 'UPI transfer', done: p.status !== 'CREATED' && p.status !== 'RETRY' },
    { label: failed ? 'Failed. Money returned to your wallet' : 'In your account', detail: p.providerRef ? `UTR ${p.providerRef}` : 'Waiting for the bank', done: DONE.includes(p.status) },
  ]
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
      <div className="flex items-baseline justify-between">
        <div className="num text-[30px] font-semibold tracking-tight text-ink">{rupees(p.amount)}</div>
        <Badge tone={PAYOUT_TONE[p.status]} dot>{PAYOUT_LABEL[p.status]}</Badge>
      </div>
      <div className="mt-1 text-sm text-muted">to <span className="font-mono">{p.upiId}</span> · fee {rupees(p.fee)}</div>
      <ol className="relative mt-6 space-y-5 pl-1">
        <span className="absolute bottom-3 left-[13px] top-3 w-px bg-line-2" />
        {steps.map((s, i) => {
          const active = !s.done && (i === 0 || steps[i - 1].done)
          const isFail = failed && i === 2
          return (
            <li key={i} className="relative flex gap-3">
              <span className={clsx('relative z-10 flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-full border-2 bg-surface transition-colors',
                isFail ? 'border-neg bg-neg text-white' : s.done ? 'border-forest bg-forest text-lime' : active ? 'border-forest' : 'border-line-2')}>
                {isFail ? <Undo2 className="h-3.5 w-3.5" /> : s.done ? <Check className="h-3.5 w-3.5" strokeWidth={3} /> : active ? <Loader2 className="h-3.5 w-3.5 animate-spin text-forest" /> : null}
              </span>
              <div>
                <div className={clsx('text-sm font-medium', s.done || active ? 'text-ink' : 'text-muted')}>{s.label}</div>
                <div className="text-xs text-muted">{s.detail}</div>
              </div>
            </li>
          )
        })}
      </ol>
    </motion.div>
  )
}
