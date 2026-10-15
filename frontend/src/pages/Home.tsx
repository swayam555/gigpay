import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { ArrowRight, ArrowUpRight } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { platformName, rupees, type Day, type Incentive, type Summary, type Wallet, type Worker } from '../api'
import { useApi } from '../hooks'
import { Card, ErrorNote, Skeleton } from '../components/ui'
import { Ticker } from '../landing/motion'

const weekday = (d: string) => new Date(d + 'T00:00:00').toLocaleDateString('en-IN', { weekday: 'short' })
const greeting = () => { const h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening' }

export default function Home() {
  const me = useApi<Worker>('/api/earnings/me')
  const today = useApi<Summary>('/api/earnings/summary?period=today', 10000)
  const week = useApi<Summary>('/api/earnings/summary?period=week', 30000)
  const daily = useApi<Day[]>('/api/earnings/daily', 30000)
  const wallet = useApi<Wallet>('/api/wallet', 10000)
  const incentives = useApi<Incentive[]>('/api/earnings/incentives', 10000)
  const t = today.data
  const w = wallet.data
  const chart = (daily.data ?? []).map(d => ({ ...d, label: weekday(d.day), rupees: d.net / 100 }))
  const weekTotal = chart.reduce((s, d) => s + d.net, 0)
  const workedDays = chart.filter(d => d.trips > 0).length

  return (
    <>
      <ErrorNote error={today.error ?? wallet.error} />
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-[26px] font-semibold tracking-[-0.03em] text-ink">{greeting()}, {me.data?.name.split(' ')[0] ?? '…'}</h1>
          <p className="mt-1 text-sm text-muted">{me.data ? `${me.data.city} · ${me.data.vehicle} · ${me.data.platforms.map(platformName).join(' and ')}` : ' '}</p>
        </div>
        <span className="inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-xs text-muted">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-pos" /> Updating live
        </span>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1.25fr_1fr]">
        <section className="relative overflow-hidden rounded-[var(--radius-card)] bg-forest p-6 text-white">
          <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full border border-white/10" />
          <div className="pointer-events-none absolute -right-4 -top-4 h-32 w-32 rounded-full border border-white/10" />
          <div className="text-sm text-white/65">Earned and waiting for you</div>
          <div className="mt-2 text-[44px] font-semibold leading-none tracking-[-0.035em]">
            {w ? <Ticker value={w.owed} format={v => rupees(v, true)} /> : <Skeleton className="h-11 w-48 opacity-20" />}
          </div>
          <div className="mt-6 grid grid-cols-2 gap-4 border-t border-white/10 pt-5 text-sm">
            <div><div className="text-white/55">Available now</div><div className="num mt-1 text-lg font-semibold text-lime">{w ? rupees(w.withdrawableNow) : '…'}</div></div>
            <div><div className="text-white/55">Instant payouts left today</div><div className="num mt-1 text-lg font-semibold">{w ? `${w.payoutsLeftToday} of 3` : '…'}</div></div>
          </div>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <Link to="/app/wallet" className="focus-ring inline-flex h-11 items-center gap-2 rounded-xl bg-lime px-5 text-sm font-semibold text-forest-2 transition hover:bg-lime-2">
              Get paid now <ArrowRight className="h-4 w-4" />
            </Link>
            <span className="text-xs text-white/55">₹5 flat fee · or wait for the free weekly payout</span>
          </div>
        </section>

        <div className="grid grid-cols-2 gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line">
          {[
            { l: 'Earned today', v: t && rupees(t.netPaise), h: t && `${t.trips} ${t.trips === 1 ? 'trip' : 'trips'}` },
            { l: 'Per hour on trips', v: t && rupees(t.netPerHourPaise), h: t && `${(t.minutesWorked / 60).toFixed(1)} h driving` },
            { l: 'This week', v: week.data && rupees(week.data.netPaise), h: week.data && `since Monday · ${week.data.trips} trips` },
            { l: 'Tips today', v: t && rupees(t.tipPaise), h: 'always 100% yours' },
          ].map(s => (
            <div key={s.l} className="bg-surface p-5">
              <div className="text-[13px] text-muted">{s.l}</div>
              <div className="num mt-2 text-2xl font-semibold tracking-tight text-ink">{s.v ?? <Skeleton className="h-7 w-24" />}</div>
              <div className="mt-1.5 text-xs text-muted">{s.h ?? ' '}</div>
            </div>
          ))}
        </div>
      </div>

      <Card title="Last 7 days" subtitle={daily.data ? `${rupees(weekTotal)} earned · ${workedDays} working ${workedDays === 1 ? 'day' : 'days'}` : ' '} padded={false}>
        <div className="h-60 px-2 pb-3">
          {daily.data ? (
            <ResponsiveContainer>
              <AreaChart data={chart} margin={{ top: 16, right: 16, left: 4, bottom: 0 }}>
                <defs>
                  <linearGradient id="earned" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#b3e352" stopOpacity={0.55} />
                    <stop offset="100%" stopColor="#b3e352" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} stroke="#ecebe5" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} fontSize={12} tick={{ fill: '#6b746e' }} dy={6} />
                <YAxis tickLine={false} axisLine={false} fontSize={12} tick={{ fill: '#6b746e' }} width={52}
                  tickFormatter={v => `₹${Number(v) >= 1000 ? `${(Number(v) / 1000).toFixed(1)}k` : v}`} />
                <Tooltip cursor={{ stroke: '#17402a', strokeDasharray: '3 3' }}
                  contentStyle={{ borderRadius: 10, border: '1px solid #e4e2da', boxShadow: '0 8px 24px -12px rgb(0 0 0 / .25)', fontSize: 13 }}
                  formatter={(v, _n, item) => [`${rupees(Number(v) * 100)} · ${(item.payload as Day).trips} trips`, 'Earned']} />
                <Area type="monotone" dataKey="rupees" stroke="#17402a" strokeWidth={2} fill="url(#earned)"
                  dot={{ r: 3, fill: '#17402a', strokeWidth: 0 }} activeDot={{ r: 5, fill: '#17402a', stroke: '#c6f36d', strokeWidth: 2 }} />
              </AreaChart>
            </ResponsiveContainer>
          ) : <Skeleton className="mx-3 h-full" />}
        </div>
      </Card>

      <div className="grid gap-5 md:grid-cols-2">
        <Card title="Today's streak bonuses" subtitle="Bonuses land on the trip that hits each target">
          <div className="space-y-5">
            {(incentives.data ?? []).map(i => {
              const target = i.nextTarget ?? i.tripsToday
              const pct = target ? Math.min(100, (i.tripsToday / target) * 100) : 100
              return (
                <div key={i.platform}>
                  <div className="flex items-baseline justify-between text-sm">
                    <span className="font-medium text-ink">{i.platform}</span>
                    <span className="num text-muted">
                      {i.nextTarget ? <>{i.tripsToday} / {i.nextTarget} trips · <span className="text-ink">{rupees(i.nextBonusPaise ?? 0)}</span></> : 'All bonuses unlocked'}
                    </span>
                  </div>
                  <div className="mt-2 h-2 overflow-hidden rounded-full bg-ink/[0.06]">
                    <motion.div className="h-full rounded-full bg-forest" initial={{ width: 0 }} animate={{ width: `${pct}%` }} transition={{ duration: 0.9, ease: 'easeOut' }} />
                  </div>
                  {i.earnedTodayPaise > 0 && <div className="mt-1.5 text-xs text-pos">{rupees(i.earnedTodayPaise)} in bonuses earned today</div>}
                </div>
              )
            })}
            {!incentives.data && <><Skeleton className="h-9" /><Skeleton className="h-9" /></>}
          </div>
        </Card>

        <Card title="Today by platform" subtitle="Net of commission">
          {t && t.byPlatform.length > 0 ? (
            <table className="w-full text-sm">
              <tbody className="divide-y divide-line">
                {t.byPlatform.map(p => (
                  <tr key={p.platform}>
                    <td className="py-2.5 font-medium text-ink">{p.platform}</td>
                    <td className="num py-2.5 text-muted">{p.trips} trips</td>
                    <td className="num py-2.5 text-right font-semibold text-ink">{rupees(p.net)}</td>
                  </tr>
                ))}
                {t.commissionPaise > 0 && (
                  <tr><td className="py-2.5 text-muted" colSpan={2}>Commission kept by platforms</td><td className="num py-2.5 text-right text-muted">−{rupees(t.commissionPaise)}</td></tr>
                )}
              </tbody>
            </table>
          ) : t ? <p className="py-6 text-center text-sm text-muted">No trips yet today.</p> : <Skeleton className="h-24" />}
        </Card>
      </div>

      <Link to="/app/coach" className="group flex items-center justify-between gap-4 rounded-[var(--radius-card)] border border-line bg-surface p-5 transition hover:border-line-2 hover:shadow-[var(--shadow-pop)]">
        <div>
          <div className="text-[15px] font-semibold text-ink">Not sure why today was slower?</div>
          <div className="mt-0.5 text-sm text-muted">The money coach reads your trips and explains it. It can also help with tax, insurance and e-Shram.</div>
        </div>
        <ArrowUpRight className="h-5 w-5 shrink-0 text-muted transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-ink" />
      </Link>
    </>
  )
}
