import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { sessions, type Role, type Session } from './api'

interface AuthState {
  worker: Session | null; admin: Session | null
  signIn: (s: Session) => void; signOut: (role: Role) => void
}

const Ctx = createContext<AuthState>(null!)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [worker, setWorker] = useState(sessions.get('WORKER'))
  const [admin, setAdmin] = useState(sessions.get('ADMIN'))
  const value = useMemo<AuthState>(() => ({
    worker, admin,
    signIn: s => { sessions.set(s); if (s.role === 'ADMIN') setAdmin(s); else setWorker(s) },
    signOut: role => { sessions.clear(role); if (role === 'ADMIN') setAdmin(null); else setWorker(null) },
  }), [worker, admin])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
