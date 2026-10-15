import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { ArrowLeft, ArrowRight } from 'lucide-react'
import { send, type Session } from '../api'
import { useAuth } from '../auth'
import { Button, ErrorNote, Logo } from '../components/ui'

const DEMO = [{ name: 'Ravi Kumar', phone: '9000000001', meta: 'Bengaluru · FoodRunner, RideNow' },
  { name: 'Pooja Devi', phone: '9000000002', meta: 'Mumbai · QuickDrop' }]

export default function Login() {
  const auth = useAuth()
  const [phone, setPhone] = useState('9000000001')
  const [pin, setPin] = useState('1234')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  if (auth.worker) return <Navigate to="/app" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true); setError(null)
    try { auth.signIn(await send<Session>('/api/auth/worker', 'POST', { phone, pin })) }
    catch (err) { setError((err as Error).message) }
    finally { setBusy(false) }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_1.05fr]">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-forest p-12 text-white lg:flex">
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,rgb(255_255_255/0.05)_1px,transparent_1px),linear-gradient(to_bottom,rgb(255_255_255/0.05)_1px,transparent_1px)] bg-[size:48px_48px]" />
        <Link to="/" className="relative w-fit"><Logo inverted /></Link>
        <div className="relative">
          <p className="max-w-md text-[32px] font-semibold leading-tight tracking-[-0.03em]">
            Finish a trip at 1 PM.<br />Have the money by 1 PM.
          </p>
          <p className="mt-4 text-white/60">That's the whole product. Everything else in this system exists to make it safe.</p>
        </div>
        <div className="relative grid grid-cols-3 gap-6 border-t border-white/10 pt-6 text-sm">
          {[['₹5', 'flat fee'], ['90%', 'of earnings available'], ['3', 'payouts a day']].map(([v, l]) => (
            <div key={l}><div className="num text-2xl font-semibold text-lime">{v}</div><div className="mt-1 text-white/60">{l}</div></div>
          ))}
        </div>
      </aside>

      <main className="flex flex-col px-6 py-8 sm:px-12">
        <div className="flex items-center justify-between">
          <Link to="/" className="inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink"><ArrowLeft className="h-4 w-4" /> Back to overview</Link>
          <Link to="/finance" className="text-sm text-muted hover:text-ink">Finance console →</Link>
        </div>
        <motion.form onSubmit={submit} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.45 }}
          className="m-auto w-full max-w-[380px] py-12">
          <div className="lg:hidden"><Logo /></div>
          <h1 className="mt-8 text-[28px] font-semibold tracking-[-0.03em] text-ink lg:mt-0">Sign in to GigPay</h1>
          <p className="mt-1.5 text-[15px] text-muted">For delivery and ride partners.</p>

          <div className="mt-8 space-y-4">
            <label className="label">Mobile number
              <div className="mt-1.5 flex rounded-lg border border-line-2 bg-surface focus-within:border-forest focus-within:ring-4 focus-within:ring-forest/10">
                <span className="flex items-center border-r border-line px-3 text-sm text-muted">+91</span>
                <input className="num w-full bg-transparent px-3 py-2.5 text-sm outline-none" inputMode="numeric" autoComplete="tel-national"
                  value={phone} onChange={e => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))} />
              </div>
            </label>
            <label className="label">4-digit PIN
              <input className="input num tracking-[0.6em]" type="password" inputMode="numeric" autoComplete="current-password"
                value={pin} onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 4))} />
            </label>
            <ErrorNote error={error} />
            <Button type="submit" size="lg" disabled={busy || phone.length !== 10 || pin.length !== 4} className="w-full">
              {busy ? 'Signing in…' : <>Sign in <ArrowRight className="h-4 w-4" /></>}
            </Button>
          </div>

          <div className="mt-10">
            <div className="eyebrow">Demo accounts · PIN 1234</div>
            <div className="mt-3 grid gap-2">
              {DEMO.map(d => (
                <button type="button" key={d.phone} onClick={() => { setPhone(d.phone); setPin('1234') }}
                  className={`focus-ring flex items-center justify-between rounded-lg border px-3.5 py-2.5 text-left transition ${phone === d.phone ? 'border-forest bg-forest/[0.04]' : 'border-line bg-surface hover:border-line-2'}`}>
                  <span><span className="block text-sm font-medium text-ink">{d.name}</span><span className="block text-xs text-muted">{d.meta}</span></span>
                  <span className="num font-mono text-xs text-muted">{d.phone}</span>
                </button>
              ))}
            </div>
          </div>
        </motion.form>
        <p className="text-center text-xs text-muted">Demo system. Platforms, trips and the bank are simulated.</p>
      </main>
    </div>
  )
}
