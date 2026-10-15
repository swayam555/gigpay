export type Role = 'WORKER' | 'ADMIN'

export interface Session { accessToken: string; userId: string; name: string; role: Role }

export interface Worker {
  id: string; name: string; phone: string; city: string; vehicle: string; upiId: string; platforms: string[]
  language: string; joinedOn: string
}
export interface Summary {
  period: string; from: string; to: string; trips: number; netPaise: number; farePaise: number; incentivePaise: number
  tipPaise: number; commissionPaise: number; minutesWorked: number; km: number; netPerHourPaise: number
  byPlatform: { platform: string; trips: number; net: number; incentives: number }[]
}
export interface Day { day: string; net: number; trips: number }
export interface Trip {
  id: string; platform: string; kind: string; distanceKm: number; durationMin: number; waitMin: number; surge: number
  farePaise: number; incentivePaise: number; commissionPaise: number; tipPaise: number; netPaise: number
  breakdown: { label: string; paise: number }[]; pickupArea: string; dropArea: string; completedAt: string
}
export interface Incentive { platform: string; tripsToday: number; nextTarget: number | null; nextBonusPaise: number | null; earnedTodayPaise: number }
export interface Wallet {
  workerId: string; owed: number; withdrawableNow: number; instantFee: number; minPayout: number; dailyLimitLeft: number
  payoutsLeftToday: number; withdrawablePercent: number
}
export interface Payout {
  id: string; workerId: string; kind: 'INSTANT' | 'WEEKLY'; amount: number; fee: number; upiId: string; status: string
  providerRef: string | null; attempts: number; failureReason: string | null; createdAt: string; submittedAt: string | null
  completedAt: string | null
}
export interface StatementLine { entryId: number; kind: string; description: string; reference: string; amount: number; createdAt: string }
export interface Citation { n: number; docTitle: string; section: string; snippet: string; source: string }
export interface Answer { answer: string; toolsUsed: string[]; citations: Citation[]; generatedBy: string; llm: boolean; latencyMs: number }

const KEY = 'gigpay.session.'

export const sessions = {
  get(role: Role): Session | null {
    const raw = localStorage.getItem(KEY + role)
    return raw ? JSON.parse(raw) : null
  },
  set(s: Session) { localStorage.setItem(KEY + s.role, JSON.stringify(s)) },
  clear(role: Role) { localStorage.removeItem(KEY + role) },
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) { super(message); this.status = status }
}

const isAdminPath = (path: string) => path.startsWith('/api/ledger/admin') || path.startsWith('/api/earnings/admin')

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const role: Role = isAdminPath(path) ? 'ADMIN' : 'WORKER'
  const session = sessions.get(role)
  if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)
  const res = await fetch(path, { ...init, headers })
  if (!res.ok) {
    let body: Record<string, unknown> = {}
    try { body = await res.json() } catch { /* not json */ }
    if (res.status === 401 && !path.startsWith('/api/auth')) { sessions.clear(role); window.location.reload() }
    const message = res.status === 429 && !body.error
      ? 'Too many requests. Please wait a few seconds and try again.'
      : (body.detail ?? body.error ?? res.statusText) as string
    throw new ApiError(res.status, message)
  }
  return res.json()
}

export const send = <T,>(path: string, method: 'POST' | 'PUT', body?: unknown, headers?: HeadersInit) =>
  api<T>(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })

/** All money in the APIs is in paise. */
export const rupees = (paise: number, decimals = false) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', minimumFractionDigits: decimals ? 2 : 0, maximumFractionDigits: decimals ? 2 : 0,
}).format(paise / 100)

export const time = (iso: string) => new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
export const dateTime = (iso: string) => new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export const PAYOUT_LABEL: Record<string, string> = {
  CREATED: 'Requested', SUBMITTED: 'Sent to bank', RETRY: 'Retrying', SUCCEEDED: 'Paid', FAILED: 'Failed, money returned',
}

/** Turns provider error codes into words a worker understands. */
export function friendlyReason(reason: string) {
  const r = reason.replace(/\{"error":"([A-Z_]+)"\}/, '$1')
  if (r.startsWith('NPCI_TIMEOUT')) return 'the bank network timed out'
  if (r.startsWith('RATE_LIMITED')) return 'the bank was busy'
  if (r.startsWith('INVALID_VPA')) return 'that UPI ID does not exist'
  return r.toLowerCase().replaceAll('_', ' ')
}

const PLATFORM_NAMES: Record<string, string> = { FOODRUNNER: 'FoodRunner', RIDENOW: 'RideNow', QUICKDROP: 'QuickDrop' }
export const platformName = (id: string) => PLATFORM_NAMES[id] ?? id
