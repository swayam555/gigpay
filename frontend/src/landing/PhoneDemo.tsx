import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { Check, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Ticker } from './motion'

const TRIPS = [
  { platform: 'FoodRunner', route: 'Koramangala → HSR Layout', paise: 7420 },
  { platform: 'RideNow', route: 'Indiranagar → Whitefield', paise: 18650 },
  { platform: 'FoodRunner', route: 'Jayanagar → BTM Layout', paise: 6280 },
  { platform: 'RideNow', route: 'HSR Layout → Marathahalli', paise: 15340 },
  { platform: 'FoodRunner', route: 'BTM Layout → Koramangala', paise: 5810 },
]
const inr = (p: number) => '₹' + (p / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

type Phase = 'earning' | 'sending' | 'paid'

/** The partner app, playing itself: trips arrive, the balance grows, the worker takes money out. */
export default function PhoneDemo() {
  const reduce = useReducedMotion()
  const [balance, setBalance] = useState(318640)
  const [feed, setFeed] = useState<{ id: number; platform: string; route: string; paise: number }[]>([])
  const [phase, setPhase] = useState<Phase>('earning')
  const [step, setStep] = useState(0)

  useEffect(() => {
    if (reduce) return
    let n = 0
    let cancelled = false
    const timers: ReturnType<typeof setTimeout>[] = []
    const at = (ms: number, fn: () => void) => timers.push(setTimeout(() => { if (!cancelled) fn() }, ms))
    const cycle = () => {
      setPhase('earning'); setStep(0)
      for (let i = 0; i < 4; i++) {
        at(900 + i * 1500, () => {
          const t = TRIPS[n++ % TRIPS.length]
          setFeed(f => [{ id: n, ...t }, ...f].slice(0, 3))
          setBalance(b => b + t.paise)
        })
      }
      at(7200, () => { setPhase('sending'); setStep(1) })
      at(8200, () => setStep(2))
      at(9400, () => { setStep(3); setPhase('paid'); setBalance(b => b - 240500) })
      at(12500, cycle)
    }
    cycle()
    return () => { cancelled = true; timers.forEach(clearTimeout) }
  }, [reduce])

  return (
    <div className="relative mx-auto w-[300px]">
      <div className="absolute -inset-10 -z-10 rounded-full bg-lime/30 blur-3xl" />
      <div className="rounded-[44px] border border-ink/10 bg-ink p-2.5 shadow-[0_40px_80px_-30px_rgb(15_26_20/0.55)]">
        <div className="relative overflow-hidden rounded-[36px] bg-paper">
          <div className="flex items-center justify-between px-6 pt-3 text-[11px] font-medium text-ink">
            <span className="num">9:41</span><span className="h-5 w-20 rounded-full bg-ink" /><span>5G</span>
          </div>
          <div className="px-4 pb-5 pt-4">
            <div className="text-xs text-muted">Earned and waiting</div>
            <div className="mt-1 text-[34px] font-semibold tracking-tight text-ink"><Ticker value={balance} format={inr} /></div>
            <div className="mt-1 text-[11px] text-muted">Across FoodRunner and RideNow</div>

            <div className="mt-4 rounded-2xl bg-forest p-3.5 text-white">
              <div className="flex items-center justify-between">
                <span className="text-xs text-white/70">Get paid now</span>
                <span className="rounded-full bg-lime px-2 py-0.5 text-[10px] font-semibold text-forest-2">₹5 flat fee</span>
              </div>
              <div className="num mt-1 text-xl font-semibold">₹2,400</div>
              <div className="font-mono text-[11px] text-white/60">to ravi.kumar@oksbi</div>
              <AnimatePresence mode="wait">
                {phase === 'earning' ? (
                  <motion.div key="btn" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                    className="mt-3 rounded-xl bg-lime py-2 text-center text-sm font-semibold text-forest-2">Send instantly</motion.div>
                ) : (
                  <motion.ol key="steps" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="mt-3 space-y-1.5 text-[13px]">
                    {['Requested', 'Sent to bank', 'In your account · UTR 6A2F81C0'].map((label, i) => (
                      <li key={label} className="flex items-center gap-2">
                        {step > i + 1 || (i === 2 && step === 3) ? <Check className="h-4 w-4 text-lime" />
                          : step === i + 1 ? <Loader2 className="h-4 w-4 animate-spin text-lime" />
                          : <span className="h-4 w-4 rounded-full border border-white/30" />}
                        <span className={step > i ? 'text-white' : 'text-white/50'}>{label}</span>
                      </li>
                    ))}
                  </motion.ol>
                )}
              </AnimatePresence>
            </div>

            <div className="mt-4 flex items-center justify-between">
              <span className="text-xs font-medium text-ink">Latest trips</span>
              <span className="flex items-center gap-1 text-[10px] text-pos"><span className="h-1.5 w-1.5 animate-pulse rounded-full bg-pos" />live</span>
            </div>
            <ul className="mt-2 h-[156px] space-y-2 overflow-hidden">
              <AnimatePresence initial={false}>
                {feed.map(t => (
                  <motion.li key={t.id} layout initial={{ opacity: 0, y: -14, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0 }} transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                    className="flex items-center justify-between rounded-xl border border-line bg-surface px-3 py-2.5">
                    <div>
                      <div className="text-[13px] font-medium text-ink">{t.platform}</div>
                      <div className="text-[11px] text-muted">{t.route}</div>
                    </div>
                    <div className="num text-[13px] font-semibold text-pos">+{inr(t.paise)}</div>
                  </motion.li>
                ))}
              </AnimatePresence>
            </ul>
          </div>
        </div>
      </div>
    </div>
  )
}
