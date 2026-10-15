import clsx from 'clsx'
import type { ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'lime'

export function Button({ children, onClick, variant = 'primary', size = 'md', disabled, type = 'button', className, title }: {
  children: ReactNode; onClick?: () => void; variant?: Variant; size?: 'sm' | 'md' | 'lg'; disabled?: boolean
  type?: 'button' | 'submit'; className?: string; title?: string
}) {
  const styles: Record<Variant, string> = {
    primary: 'bg-forest text-white hover:bg-forest-2 shadow-[inset_0_1px_0_rgb(255_255_255/0.08)]',
    lime: 'bg-lime text-forest-2 hover:bg-lime-2',
    secondary: 'bg-surface text-ink ring-1 ring-inset ring-line-2 hover:bg-paper',
    ghost: 'text-ink-2 hover:bg-ink/5',
    danger: 'bg-neg text-white hover:bg-neg/90',
  }
  const sizes = { sm: 'h-8 px-3 text-[13px]', md: 'h-10 px-4 text-sm', lg: 'h-12 px-5 text-[15px]' }
  return (
    <button type={type} onClick={onClick} disabled={disabled} title={title}
      className={clsx('focus-ring inline-flex select-none items-center justify-center gap-2 rounded-lg font-medium transition active:translate-y-px disabled:pointer-events-none disabled:opacity-45',
        styles[variant], sizes[size], className)}>
      {children}
    </button>
  )
}

export function Card({ title, subtitle, action, children, className, padded = true }: {
  title?: ReactNode; subtitle?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; padded?: boolean
}) {
  return (
    <section className={clsx('rounded-[var(--radius-card)] border border-line bg-surface shadow-[var(--shadow-card)]', className)}>
      {(title || action) && (
        <header className="flex items-start justify-between gap-4 px-5 pt-4">
          <div>
            <h2 className="text-[15px] font-semibold tracking-tight text-ink">{title}</h2>
            {subtitle && <p className="mt-0.5 text-[13px] text-muted">{subtitle}</p>}
          </div>
          {action}
        </header>
      )}
      <div className={clsx(padded && 'p-5', !padded && (title || action) && 'pt-3')}>{children}</div>
    </section>
  )
}

export function Stat({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: ReactNode; tone?: 'pos' | 'neg' | 'warn' }) {
  return (
    <div className="rounded-[var(--radius-card)] border border-line bg-surface px-5 py-4 shadow-[var(--shadow-card)]">
      <div className="text-[13px] text-muted">{label}</div>
      <div className={clsx('num mt-1.5 text-[26px] font-semibold leading-none tracking-tight',
        tone === 'pos' ? 'text-pos' : tone === 'neg' ? 'text-neg' : tone === 'warn' ? 'text-warn' : 'text-ink')}>{value}</div>
      {hint && <div className="mt-2 text-xs text-muted">{hint}</div>}
    </div>
  )
}

const BADGE: Record<string, string> = {
  neutral: 'bg-ink/[0.06] text-ink-2', pos: 'bg-pos/10 text-pos', neg: 'bg-neg/10 text-neg',
  warn: 'bg-warn/12 text-warn', info: 'bg-info/10 text-info', lime: 'bg-lime text-forest-2',
}

export function Badge({ children, tone = 'neutral', dot }: { children: ReactNode; tone?: keyof typeof BADGE | string; dot?: boolean }) {
  return (
    <span className={clsx('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', BADGE[tone] ?? BADGE.neutral)}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}{children}
    </span>
  )
}

export function ErrorNote({ error }: { error: string | null }) {
  return error ? <div role="alert" className="rounded-lg border border-neg/20 bg-neg/[0.06] px-3.5 py-2.5 text-sm text-neg">{error}</div> : null
}

export function Empty({ title, children }: { title?: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-10 text-center">
      <div className="mb-3 h-9 w-9 rounded-full border border-dashed border-line-2" />
      {title && <div className="text-sm font-medium text-ink">{title}</div>}
      {children && <div className="mt-1 max-w-sm text-[13px] text-muted">{children}</div>}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('skeleton', className)} />
}

export function Segmented<T extends string>({ value, options, onChange }: { value: T; options: { value: T; label: string }[]; onChange: (v: T) => void }) {
  return (
    <div className="inline-flex rounded-lg bg-ink/[0.05] p-0.5">
      {options.map(o => (
        <button key={o.value} onClick={() => onChange(o.value)}
          className={clsx('focus-ring rounded-md px-3 py-1.5 text-[13px] font-medium transition',
            value === o.value ? 'bg-surface text-ink shadow-sm' : 'text-muted hover:text-ink')}>{o.label}</button>
      ))}
    </div>
  )
}

/** GigPay mark: three rising bars, the last one a rupee that has just been paid out. */
export function Logo({ className, inverted }: { className?: string; inverted?: boolean }) {
  return (
    <span className={clsx('inline-flex items-center gap-2 font-semibold tracking-tight', className)}>
      <svg viewBox="0 0 32 32" className="h-7 w-7" aria-hidden>
        <rect width="32" height="32" rx="8" fill={inverted ? '#c6f36d' : '#17402a'} />
        <rect x="7" y="17" width="4" height="8" rx="1.5" fill={inverted ? '#17402a' : '#c6f36d'} opacity=".55" />
        <rect x="14" y="12" width="4" height="13" rx="1.5" fill={inverted ? '#17402a' : '#c6f36d'} opacity=".8" />
        <rect x="21" y="7" width="4" height="18" rx="1.5" fill={inverted ? '#17402a' : '#c6f36d'} />
      </svg>
      <span className="text-[17px]">GigPay</span>
    </span>
  )
}

export const PAYOUT_TONE: Record<string, string> = { CREATED: 'info', SUBMITTED: 'info', RETRY: 'warn', SUCCEEDED: 'pos', FAILED: 'neg' }
