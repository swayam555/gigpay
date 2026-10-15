import { useState } from 'react'
import clsx from 'clsx'
import { AnimatePresence, motion } from 'motion/react'
import { ChevronDown } from 'lucide-react'
import { dateTime, rupees, type Trip } from '../api'
import { useApi } from '../hooks'
import { Badge, Card, Empty, ErrorNote, Segmented, Skeleton } from '../components/ui'

export default function Trips() {
  const [order, setOrder] = useState<'recent' | 'lowest'>('recent')
  const { data, error } = useApi<Trip[]>(`/api/earnings/trips?limit=50&order=${order}`, 15000)
  const [open, setOpen] = useState<string | null>(null)
  const total = (data ?? []).reduce((s, t) => s + t.netPaise, 0)

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-[26px] font-semibold tracking-[-0.03em] text-ink">Trips</h1>
          <p className="mt-1 text-sm text-muted">The last 7 days. Open any trip to see exactly how it was paid.</p>
        </div>
        <Segmented value={order} onChange={setOrder} options={[{ value: 'recent', label: 'Most recent' }, { value: 'lowest', label: 'Lowest paid' }]} />
      </div>
      <ErrorNote error={error} />
      <Card padded={false}>
        <div className="hidden grid-cols-[1.2fr_1.6fr_0.8fr_0.9fr_28px] gap-4 border-b border-line px-5 py-3 text-xs font-medium text-muted md:grid">
          <span>Platform</span><span>Route</span><span>Distance</span><span className="text-right">You earned</span><span />
        </div>
        {!data && <div className="space-y-3 p-5">{[0, 1, 2, 3, 4].map(i => <Skeleton key={i} className="h-10" />)}</div>}
        {data && data.length === 0 && <Empty title="No trips yet">Trips appear here seconds after a platform reports them.</Empty>}
        <ul className="divide-y divide-line">
          {(data ?? []).map(t => {
            const isOpen = open === t.id
            return (
              <li key={t.id}>
                <button onClick={() => setOpen(isOpen ? null : t.id)} aria-expanded={isOpen}
                  className="focus-ring grid w-full grid-cols-[1fr_auto_28px] items-center gap-4 px-5 py-3.5 text-left transition hover:bg-paper/70 md:grid-cols-[1.2fr_1.6fr_0.8fr_0.9fr_28px]">
                  <span>
                    <span className="flex items-center gap-2 text-sm font-medium text-ink">{t.platform}
                      {t.surge > 1 && <Badge tone="warn">{t.surge}× peak</Badge>}{t.tipPaise > 0 && <Badge tone="pos">tip</Badge>}</span>
                    <span className="mt-0.5 block text-xs text-muted">{dateTime(t.completedAt)}</span>
                  </span>
                  <span className="hidden text-sm text-ink-2 md:block">{t.pickupArea} → {t.dropArea}</span>
                  <span className="num hidden text-sm text-ink-2 md:block">{t.distanceKm} km</span>
                  <span className="num text-right text-[15px] font-semibold text-ink">{rupees(t.netPaise, true)}</span>
                  <ChevronDown className={clsx('h-4 w-4 text-muted transition-transform', isOpen && 'rotate-180')} />
                </button>
                <AnimatePresence initial={false}>
                  {isOpen && (
                    <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.25, ease: 'easeOut' }} className="overflow-hidden">
                      <div className="mx-5 mb-4 rounded-xl border border-line bg-paper p-4">
                        <div className="eyebrow">How this trip was paid</div>
                        <dl className="mt-3 space-y-1.5 text-sm">
                          {t.breakdown.map((l, i) => (
                            <div key={i} className="flex justify-between gap-4">
                              <dt className="text-ink-2">{l.label}</dt>
                              <dd className={clsx('num', l.paise < 0 ? 'text-neg' : 'text-ink')}>{l.paise < 0 ? '−' : ''}{rupees(Math.abs(l.paise), true)}</dd>
                            </div>
                          ))}
                          <div className="flex justify-between border-t border-line-2 pt-2 font-semibold">
                            <dt className="text-ink">You earned</dt><dd className="num text-ink">{rupees(t.netPaise, true)}</dd>
                          </div>
                        </dl>
                        <div className="mt-3 font-mono text-[11px] text-muted">
                          {t.durationMin} min driving{t.waitMin > 0 && ` · ${t.waitMin} min waiting`} · {t.pickupArea} → {t.dropArea} · {t.id}
                        </div>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </li>
            )
          })}
        </ul>
        {data && data.length > 0 && (
          <div className="flex justify-between border-t border-line px-5 py-3 text-sm">
            <span className="text-muted">{data.length} trips shown</span>
            <span className="num font-medium text-ink">{rupees(total, true)}</span>
          </div>
        )}
      </Card>
    </>
  )
}
