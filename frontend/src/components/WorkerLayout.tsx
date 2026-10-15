import clsx from 'clsx'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { motion } from 'motion/react'
import { Bike, LayoutGrid, LogOut, MessageSquareText, Wallet } from 'lucide-react'
import { useAuth } from '../auth'
import { Logo } from './ui'

const TABS = [
  { to: '/app', label: 'Overview', icon: LayoutGrid, end: true },
  { to: '/app/trips', label: 'Trips', icon: Bike },
  { to: '/app/wallet', label: 'Wallet', icon: Wallet },
  { to: '/app/coach', label: 'Coach', icon: MessageSquareText },
]

export default function WorkerLayout() {
  const auth = useAuth()
  const location = useLocation()
  const initials = auth.worker?.name.split(' ').map(p => p[0]).join('').slice(0, 2)
  return (
    <div className="min-h-screen pb-24 sm:pb-12">
      <header className="sticky top-0 z-20 border-b border-line bg-paper/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between gap-4 px-5">
          <NavLink to="/" className="focus-ring rounded-md"><Logo /></NavLink>
          <nav className="hidden items-center gap-1 sm:flex">
            {TABS.map(t => (
              <NavLink key={t.to} to={t.to} end={t.end}
                className={({ isActive }) => clsx('focus-ring relative rounded-lg px-3.5 py-2 text-sm font-medium transition', isActive ? 'text-ink' : 'text-muted hover:text-ink')}>
                {({ isActive }) => (<>
                  {t.label}
                  {isActive && <motion.span layoutId="tab-underline" className="absolute inset-x-3 -bottom-[13px] h-0.5 rounded-full bg-forest" />}
                </>)}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <div className="hidden text-right md:block">
              <div className="text-sm font-medium text-ink">{auth.worker?.name}</div>
              <div className="text-xs text-muted">{auth.worker?.userId}</div>
            </div>
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-forest text-xs font-semibold text-lime">{initials}</span>
            <button title="Sign out" onClick={() => auth.signOut('WORKER')} className="focus-ring rounded-lg p-2 text-muted hover:bg-ink/5 hover:text-ink"><LogOut className="h-4 w-4" /></button>
          </div>
        </div>
      </header>
      <motion.main key={location.pathname} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25 }} className="mx-auto max-w-5xl space-y-5 px-5 py-8">
          <Outlet />
        </motion.main>
      <nav className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-4 border-t border-line bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur sm:hidden">
        {TABS.map(t => (
          <NavLink key={t.to} to={t.to} end={t.end}
            className={({ isActive }) => clsx('flex flex-col items-center gap-1 py-2.5 text-[11px] font-medium', isActive ? 'text-forest' : 'text-muted')}>
            <t.icon className="h-5 w-5" strokeWidth={1.75} />{t.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
