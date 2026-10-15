import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

interface Entry { id: number; kind: string; text: string; legs: [string, number][] }

const TEMPLATES: Omit<Entry, 'id'>[] = [
  { kind: 'EARNING_ACCRUED', text: 'FoodRunner trip · Koramangala → HSR', legs: [['platform:FOODRUNNER:receivable', 7420], ['worker:GW-0001:payable', -7420]] },
  { kind: 'PAYOUT_REQUESTED', text: 'Instant payout to ravi.kumar@oksbi', legs: [['worker:GW-0001:payable', 240500], ['clearing:payouts-in-transit', -240000], ['income:payout-fees', -500]] },
  { kind: 'PAYOUT_SETTLED', text: 'Bank confirmed · UTR 6A2F81C0', legs: [['clearing:payouts-in-transit', 240000], ['bank:float', -240000]] },
  { kind: 'EARNING_ACCRUED', text: 'RideNow trip · Indiranagar → Whitefield', legs: [['platform:RIDENOW:receivable', 18650], ['worker:GW-0007:payable', -18650]] },
  { kind: 'PLATFORM_SETTLEMENT', text: 'QuickDrop settled its weekly payable', legs: [['bank:float', 1248300], ['platform:QUICKDROP:receivable', -1248300]] },
  { kind: 'PAYOUT_REVERSED', text: 'Bank rejected payout · money returned', legs: [['clearing:payouts-in-transit', 120000], ['income:payout-fees', 500], ['worker:GW-0019:payable', -120500]] },
]
const amt = (p: number) => (Math.abs(p) / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** A journal that keeps posting entries; every one balances, so the book always sums to zero. */
export default function JournalDemo() {
  const reduce = useReducedMotion()
  const [entries, setEntries] = useState<Entry[]>(() => TEMPLATES.slice(0, 3).map((t, i) => ({ ...t, id: 8120 - i })))
  const [posted, setPosted] = useState(8120)
  const counter = useRef(8120)

  useEffect(() => {
    if (reduce) return
    let n = 3
    const id = setInterval(() => {
      const next = ++counter.current
      const template = TEMPLATES[n++ % TEMPLATES.length]
      setPosted(next)
      setEntries(e => [{ ...template, id: next }, ...e].slice(0, 3))
    }, 2600)
    return () => clearInterval(id)
  }, [reduce])

  return (
    <div className="rounded-2xl border border-white/10 bg-forest-2/60 p-1 font-mono text-[12px] shadow-2xl backdrop-blur">
      <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
        <span className="text-white/60">journal_entries</span>
        <span className="num text-white/40">{posted.toLocaleString('en-IN')} posted</span>
      </div>
      <div className="h-[318px] overflow-hidden">
        <AnimatePresence initial={false}>
          {entries.map(e => (
            <motion.div key={e.id} layout initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0 }}
              transition={{ duration: 0.45, ease: 'easeOut' }} className="border-b border-white/[0.07] px-4 py-3">
              <div className="flex items-baseline justify-between gap-3">
                <span className="truncate text-white/85"><span className="text-white/35">#{e.id}</span> {e.text}</span>
                <span className="shrink-0 rounded bg-white/[0.08] px-1.5 py-0.5 text-[10px] text-lime">{e.kind}</span>
              </div>
              <div className="mt-2 space-y-0.5">
                {e.legs.map(([acct, a]) => (
                  <div key={acct} className="flex justify-between">
                    <span className={a < 0 ? 'pl-5 text-white/45' : 'text-white/70'}>{acct}</span>
                    <span className="num text-white/70">{a > 0 ? 'Dr' : 'Cr'} {amt(a)}</span>
                  </div>
                ))}
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
      <div className="flex items-center justify-between rounded-b-xl bg-black/20 px-4 py-3">
        <span className="text-white/60">Σ all balances</span>
        <motion.span key={posted} initial={{ scale: 1.25, color: '#c6f36d' }} animate={{ scale: 1, color: '#c6f36d' }}
          className="num flex items-center gap-2 font-semibold">₹0.00 <span className="text-[10px] font-normal text-white/50">balanced</span></motion.span>
      </div>
    </div>
  )
}
