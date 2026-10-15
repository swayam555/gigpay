import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { ArrowRight, ArrowUpRight, BookOpen, Check, Copy, FileCheck2, Landmark, Scale, ShieldCheck, Timer, Webhook } from 'lucide-react'
import { Logo } from '../components/ui'
import PhoneDemo from './PhoneDemo'
import JournalDemo from './JournalDemo'
import Architecture from './Architecture'
import { CountUp, Reveal } from './motion'

// Where the code lives. Override at build time with VITE_REPO_URL.
const REPO: string = import.meta.env.VITE_REPO_URL ?? 'https://github.com/swayam555/gigpay'

export default function Landing() {
  return (
    <div className="overflow-x-clip">
      <Nav />
      <Hero />
      <Problem />
      <HowItWorks />
      <Ledger />
      <Resilience />
      <Coach />
      <ArchitectureSection />
      <RunIt />
      <Footer />
    </div>
  )
}

function Nav() {
  return (
    <header className="sticky top-0 z-30 border-b border-line/70 bg-paper/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <Link to="/" className="focus-ring rounded-md"><Logo /></Link>
        <nav className="hidden items-center gap-7 text-sm text-ink-2 md:flex">
          <a href="#how" className="hover:text-ink">How it works</a>
          <a href="#ledger" className="hover:text-ink">The ledger</a>
          <a href="#coach" className="hover:text-ink">Money coach</a>
          <a href="#architecture" className="hover:text-ink">Architecture</a>
        </nav>
        <div className="flex items-center gap-2">
          <Link to="/finance" className="hidden h-9 items-center rounded-lg px-3 text-sm text-ink-2 hover:bg-ink/5 sm:inline-flex">Finance console</Link>
          <Link to="/login" className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-forest px-3.5 text-sm font-medium text-white hover:bg-forest-2">
            Open the app <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </header>
  )
}

function Hero() {
  return (
    <section className="relative">
      <div className="pointer-events-none absolute inset-0 -z-10 bg-[linear-gradient(to_right,#e4e2da_1px,transparent_1px),linear-gradient(to_bottom,#e4e2da_1px,transparent_1px)] bg-[size:56px_56px] [mask-image:radial-gradient(ellipse_at_top,black_20%,transparent_70%)]" />
      <div className="mx-auto grid max-w-6xl items-center gap-14 px-5 pb-20 pt-16 lg:grid-cols-[1.1fr_0.9fr] lg:pt-24">
        <div>
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}
            className="inline-flex items-center gap-2 rounded-full border border-line-2 bg-surface px-3 py-1 text-xs text-ink-2">
            <span className="h-1.5 w-1.5 rounded-full bg-pos" /> Earned-wage access for India's gig workers
          </motion.div>
          <motion.h1 initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.08 }}
            className="mt-6 text-[44px] font-semibold leading-[1.02] tracking-[-0.035em] text-ink sm:text-6xl lg:text-[68px]">
            Get paid the day<br />you <span className="relative whitespace-nowrap">earn it.
              <motion.svg viewBox="0 0 220 14" className="absolute -bottom-2 left-0 w-full" initial={{ pathLength: 0 }} aria-hidden>
                <motion.path d="M2 9 C 60 2, 150 2, 218 7" fill="none" stroke="#b3e352" strokeWidth="6" strokeLinecap="round"
                  initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 0.9, delay: 0.6, ease: 'easeOut' }} />
              </motion.svg>
            </span>
          </motion.h1>
          <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.16 }}
            className="mt-7 max-w-xl text-lg leading-relaxed text-ink-2">
            Delivery and ride partners work every day but wait a week for their money. GigPay tracks every trip
            across platforms and lets them take out what they've already earned, to any UPI ID, in seconds.
          </motion.p>
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.24 }}
            className="mt-9 flex flex-wrap items-center gap-3">
            <Link to="/login" className="inline-flex h-12 items-center gap-2 rounded-xl bg-forest px-5 text-[15px] font-medium text-white shadow-[var(--shadow-pop)] hover:bg-forest-2">
              Try the live demo <ArrowRight className="h-4 w-4" />
            </Link>
            <a href={REPO} className="inline-flex h-12 items-center gap-2 rounded-xl border border-line-2 bg-surface px-5 text-[15px] font-medium text-ink hover:bg-paper">
              View the source <ArrowUpRight className="h-4 w-4" />
            </a>
          </motion.div>
          <motion.dl initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.8, delay: 0.4 }}
            className="mt-12 grid max-w-lg grid-cols-3 gap-6 border-t border-line pt-6">
            {[['~3.5 s', 'tap to money in account'], ['₹5', 'flat fee, no interest'], ['₹0.00', 'trial balance, always']].map(([v, l]) => (
              <div key={l}><dt className="num text-2xl font-semibold tracking-tight text-ink">{v}</dt><dd className="mt-1 text-[13px] leading-snug text-muted">{l}</dd></div>
            ))}
          </motion.dl>
        </div>
        <motion.div initial={{ opacity: 0, y: 30, rotate: 2 }} animate={{ opacity: 1, y: 0, rotate: 0 }}
          transition={{ duration: 0.9, delay: 0.2, ease: [0.21, 0.47, 0.32, 0.98] }}>
          <PhoneDemo />
        </motion.div>
      </div>
    </section>
  )
}

function SectionHead({ eyebrow, title, children, dark }: { eyebrow: string; title: string; children?: React.ReactNode; dark?: boolean }) {
  return (
    <Reveal className="max-w-2xl">
      <div className={dark ? 'font-mono text-[11px] font-medium uppercase tracking-[0.14em] text-lime' : 'eyebrow'}>{eyebrow}</div>
      <h2 className={`mt-3 text-3xl font-semibold tracking-[-0.03em] sm:text-[40px] sm:leading-[1.1] ${dark ? 'text-white' : 'text-ink'}`}>{title}</h2>
      {children && <p className={`mt-4 text-[17px] leading-relaxed ${dark ? 'text-white/70' : 'text-ink-2'}`}>{children}</p>}
    </Reveal>
  )
}

function Problem() {
  const items = [
    { v: <CountUp to={77} suffix=" lakh" />, l: 'gig workers in India in 2020-21, projected to reach 2.35 crore by 2029-30 (NITI Aayog)' },
    { v: <><CountUp to={7} /> days</>, l: 'a typical wait between finishing a trip and being paid for it' },
    { v: <><CountUp to={0} />%</>, l: 'interest on GigPay. It is the worker\'s own money, not a loan' },
  ]
  return (
    <section className="border-y border-line bg-surface">
      <div className="mx-auto grid max-w-6xl divide-y divide-line px-5 md:grid-cols-3 md:divide-x md:divide-y-0">
        {items.map((it, i) => (
          <Reveal key={i} delay={i * 0.08} className="py-10 md:px-8 md:first:pl-0 md:last:pr-0">
            <div className="text-4xl font-semibold tracking-tight text-ink">{it.v}</div>
            <p className="mt-2 text-[15px] leading-relaxed text-muted">{it.l}</p>
          </Reveal>
        ))}
      </div>
    </section>
  )
}

function HowItWorks() {
  const steps = [
    { icon: Webhook, title: 'A trip is completed', body: 'The platform sends a signed webhook. Tampered or replayed requests are rejected before they are read.' },
    { icon: FileCheck2, title: 'Pay is explained', body: 'Base, distance, waiting time, peak boost, commission, tip and bonus become lines the worker can read.' },
    { icon: Scale, title: 'The ledger records it', body: 'A balanced double-entry posting: the platform owes GigPay, GigPay owes the worker. Exactly once.' },
    { icon: Landmark, title: 'The worker takes it', body: 'Up to 90% of earned money to UPI for a flat ₹5. Retries, lost callbacks and reversals are handled.' },
  ]
  return (
    <section id="how" className="mx-auto max-w-6xl scroll-mt-20 px-5 py-24">
      <SectionHead eyebrow="How it works" title="From a finished trip to money in the bank.">
        Every step is a real service in this repository: signed webhooks, Kafka, a Postgres ledger and a payout state machine.
      </SectionHead>
      <ol className="relative mt-14 grid gap-5 md:grid-cols-4">
        <motion.div className="absolute left-0 right-0 top-[27px] hidden h-px origin-left bg-line-2 md:block"
          initial={{ scaleX: 0 }} whileInView={{ scaleX: 1 }} viewport={{ once: true }} transition={{ duration: 1.2, ease: 'easeOut' }} />
        {steps.map((s, i) => (
          <Reveal key={s.title} delay={0.15 + i * 0.12}>
            <li className="relative list-none">
              <div className="relative z-10 flex h-14 w-14 items-center justify-center rounded-2xl border border-line-2 bg-surface shadow-[var(--shadow-card)]">
                <s.icon className="h-6 w-6 text-forest" strokeWidth={1.75} />
              </div>
              <div className="mt-5 font-mono text-xs text-muted">0{i + 1}</div>
              <h3 className="mt-1 text-lg font-semibold tracking-tight text-ink">{s.title}</h3>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-2">{s.body}</p>
            </li>
          </Reveal>
        ))}
      </ol>
    </section>
  )
}

function Ledger() {
  return (
    <section id="ledger" className="scroll-mt-16 bg-forest">
      <div className="mx-auto grid max-w-6xl items-center gap-14 px-5 py-24 lg:grid-cols-2">
        <div>
          <SectionHead dark eyebrow="The ledger" title="Money can't appear or vanish.">
            Every movement is an entry whose debits equal its credits. Entries are append-only and idempotent, and the
            accounts they touch are locked in a fixed order, so two taps on "withdraw" can never spend the same rupee twice.
          </SectionHead>
          <Reveal delay={0.15}>
            <ul className="mt-8 space-y-3 text-[15px] text-white/80">
              {['Integer paise everywhere. Floats never touch money.', 'Mistakes are fixed with reversing entries, never edits.',
                'The finance console proves it live: the sum of all balances is exactly ₹0.00.'].map(t => (
                <li key={t} className="flex gap-3"><Check className="mt-0.5 h-5 w-5 shrink-0 text-lime" />{t}</li>
              ))}
            </ul>
          </Reveal>
        </div>
        <Reveal delay={0.1}><JournalDemo /></Reveal>
      </div>
    </section>
  )
}

function Resilience() {
  const stats = [
    { v: <CountUp to={40} />, l: 'payouts sent at once' },
    { v: <CountUp to={40} suffix="%" />, l: 'of bank calls failing, lost or duplicated' },
    { v: <CountUp to={36} />, l: 'paid, 14 of them on a retry' },
    { v: <CountUp to={0} />, l: 'reconciliation issues afterwards' },
  ]
  return (
    <section className="mx-auto max-w-6xl px-5 py-24">
      <div className="grid gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:items-end">
        <SectionHead eyebrow="Built for unreliable rails" title="We broke the bank on purpose.">
          The mock payout bank can reject transfers, lose or duplicate its callbacks and rate-limit us. The chaos test
          turns all of that up at once. Every payout still reaches a final state, and the books still balance.
        </SectionHead>
        <div className="grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-line bg-line">
          {stats.map((s, i) => (
            <Reveal key={i} delay={i * 0.07} className="bg-surface p-6">
              <div className="text-4xl font-semibold tracking-tight text-ink">{s.v}</div>
              <div className="mt-2 text-sm text-muted">{s.l}</div>
            </Reveal>
          ))}
        </div>
      </div>
      <div className="mt-12 grid gap-5 md:grid-cols-3">
        {[
          { icon: Timer, t: 'Retries with backoff', b: 'Timeouts and rate limits retry after 2, 4 and 8 seconds, then give up and reverse.' },
          { icon: ShieldCheck, t: 'One bank reference per attempt', b: 'A network retry reuses its reference so the bank can de-duplicate it. Never paid twice.' },
          { icon: Scale, t: 'Reconciled every minute', b: 'Bank records vs ledger, money in transit vs payouts in flight, and a zero trial balance.' },
        ].map((f, i) => (
          <Reveal key={f.t} delay={i * 0.08} className="rounded-2xl border border-line bg-surface p-6">
            <f.icon className="h-5 w-5 text-forest" strokeWidth={1.75} />
            <h3 className="mt-4 font-semibold tracking-tight text-ink">{f.t}</h3>
            <p className="mt-1.5 text-[15px] leading-relaxed text-ink-2">{f.b}</p>
          </Reveal>
        ))}
      </div>
    </section>
  )
}

function Coach() {
  return (
    <section id="coach" className="scroll-mt-16 border-y border-line bg-surface">
      <div className="mx-auto grid max-w-6xl items-center gap-14 px-5 py-24 lg:grid-cols-2">
        <Reveal className="order-2 lg:order-1">
          <div className="rounded-2xl border border-line bg-paper p-5">
            <div className="ml-auto w-fit max-w-[80%] rounded-2xl rounded-br-md bg-forest px-4 py-2.5 text-[15px] text-white">Why are my earnings less today?</div>
            <motion.div initial={{ opacity: 0, y: 10 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.4 }}
              className="mt-4 max-w-[92%] rounded-2xl rounded-bl-md border border-line bg-surface px-4 py-3.5 text-[15px] leading-relaxed text-ink-2">
              Today so far: <b className="text-ink">₹277</b> from 3 trips. Your recent working-day average is <b className="text-ink">₹1,060</b> from about 13 trips.
              <ul className="mt-2 list-disc space-y-1 pl-5">
                <li>The main reason is <b className="text-ink">fewer trips</b>. Lunch and dinner peaks pay a 1.2-1.5× boost.</li>
                <li>FoodRunner: 8 of 10 trips for the next streak bonus (₹100).</li>
              </ul>
              <div className="mt-3 flex flex-wrap gap-1.5 border-t border-line pt-3">
                {['dailyEarnings', 'incentiveProgress', 'earningsSummary'].map(t => <span key={t} className="rounded-md bg-ink/[0.05] px-2 py-0.5 font-mono text-[11px] text-ink-2">{t}</span>)}
              </div>
            </motion.div>
          </div>
        </Reveal>
        <div className="order-1 lg:order-2">
          <SectionHead eyebrow="Money coach" title="Answers from your own trips, and cited sources.">
            Questions about pay call tools that read the signed-in worker's trips, wallet and payouts. Questions about tax,
            e-Shram or insurance are answered from a curated knowledge base with hybrid search, and every answer shows its sources.
          </SectionHead>
          <Reveal delay={0.15}>
            <div className="mt-8 flex flex-wrap gap-2 text-sm text-ink-2">
              {['pgvector + full-text, fused with RRF', 'In-process embeddings', 'Works without an API key', 'Claude, OpenAI-compatible or Ollama'].map(t => (
                <span key={t} className="rounded-full border border-line-2 bg-paper px-3 py-1">{t}</span>
              ))}
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  )
}

function ArchitectureSection() {
  return (
    <section id="architecture" className="mx-auto max-w-6xl scroll-mt-16 px-5 py-24">
      <SectionHead eyebrow="Architecture" title="Four Spring Boot services, one event bus.">
        Each service owns its own database. Kafka carries earnings to the ledger through a transactional outbox, so an
        event is published exactly when its database change commits.
      </SectionHead>
      <Reveal delay={0.1} className="mt-12 rounded-2xl border border-line bg-surface p-6"><Architecture /></Reveal>
      <Reveal delay={0.15}>
        <div className="mt-8 flex flex-wrap gap-2">
          {['Java 21', 'Spring Boot 4', 'Spring Cloud Gateway', 'Kafka', 'PostgreSQL + pgvector', 'Redis', 'LangChain4j', 'React 19', 'Docker', 'Kubernetes'].map(t => (
            <span key={t} className="rounded-md border border-line bg-surface px-2.5 py-1 font-mono text-xs text-ink-2">{t}</span>
          ))}
        </div>
      </Reveal>
    </section>
  )
}

function RunIt() {
  const cmd = 'git clone ' + REPO + '.git\ncd gigpay\ndocker compose up --build\n# open http://localhost:3003'
  const [copied, setCopied] = useState(false)
  const copy = () => { navigator.clipboard?.writeText(cmd).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) }) }
  return (
    <section className="px-5 pb-24">
      <Reveal className="mx-auto grid max-w-6xl items-center gap-10 overflow-hidden rounded-3xl bg-ink p-8 sm:p-12 lg:grid-cols-2">
        <div>
          <h2 className="text-3xl font-semibold tracking-[-0.03em] text-white sm:text-4xl">Run the whole system on your laptop.</h2>
          <p className="mt-4 text-[17px] leading-relaxed text-white/65">One command starts every service, Kafka, Postgres and Redis. The platforms and the bank are simulated, so it works offline.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/login" className="inline-flex h-11 items-center gap-2 rounded-xl bg-lime px-5 text-sm font-semibold text-forest-2 hover:bg-lime-2">Open the partner app <ArrowRight className="h-4 w-4" /></Link>
            <Link to="/finance" className="inline-flex h-11 items-center gap-2 rounded-xl border border-white/15 px-5 text-sm font-medium text-white hover:bg-white/5">Finance console</Link>
          </div>
        </div>
        <div className="relative rounded-2xl border border-white/10 bg-white/[0.03] p-5 font-mono text-[13px] leading-7 text-white/80">
          <button onClick={copy} aria-label="Copy commands" className="absolute right-3 top-3 rounded-md p-2 text-white/50 hover:bg-white/10 hover:text-white">
            {copied ? <Check className="h-4 w-4 text-lime" /> : <Copy className="h-4 w-4" />}
          </button>
          {cmd.split('\n').map(l => <div key={l} className={l.startsWith('#') ? 'text-white/40' : ''}>{l.startsWith('#') ? l : <><span className="text-lime">$</span> {l}</>}</div>)}
        </div>
      </Reveal>
    </section>
  )
}

function Footer() {
  return (
    <footer className="border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-4 px-5 py-8 text-sm text-muted sm:flex-row sm:items-center">
        <div className="flex items-center gap-3"><Logo className="text-ink" /><span>A portfolio project. Platforms and the bank are simulated.</span></div>
        <div className="flex gap-5">
          <a href={REPO} className="inline-flex items-center gap-1.5 hover:text-ink"><BookOpen className="h-4 w-4" />Documentation</a>
          <Link to="/finance" className="hover:text-ink">Finance console</Link>
          <Link to="/login" className="hover:text-ink">Partner app</Link>
        </div>
      </div>
    </footer>
  )
}
