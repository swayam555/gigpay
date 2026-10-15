import { useReducedMotion } from 'motion/react'

interface Node { id: string; x: number; y: number; w: number; title: string; sub: string; tone?: 'store' | 'ext' | 'bus' }

const NODES: Node[] = [
  { id: 'platforms', x: 20, y: 40, w: 150, title: 'Partner platforms', sub: 'HMAC-signed webhooks', tone: 'ext' },
  { id: 'app', x: 20, y: 250, w: 150, title: 'Partner app', sub: 'React · finance console', tone: 'ext' },
  { id: 'gateway', x: 235, y: 250, w: 150, title: 'API gateway', sub: 'JWT · roles · rate limits' },
  { id: 'earnings', x: 235, y: 40, w: 150, title: 'earnings-service', sub: 'trips · pay rules' },
  { id: 'kafka', x: 450, y: 40, w: 150, title: 'Kafka', sub: 'earnings.accrued', tone: 'bus' },
  { id: 'ledger', x: 665, y: 40, w: 150, title: 'ledger-service', sub: 'double-entry · payouts' },
  { id: 'coach', x: 450, y: 250, w: 150, title: 'coach-service', sub: 'RAG · tool calling' },
  { id: 'bank', x: 665, y: 250, w: 150, title: 'Payout bank', sub: 'UPI · callbacks', tone: 'ext' },
]
const H = 64
const center = (id: string) => { const n = NODES.find(x => x.id === id)!; return { x: n.x + n.w / 2, y: n.y + H / 2, n } }

const EDGES: [string, string][] = [
  ['platforms', 'earnings'], ['earnings', 'kafka'], ['kafka', 'ledger'], ['ledger', 'bank'],
  ['app', 'gateway'], ['gateway', 'earnings'], ['gateway', 'coach'], ['coach', 'ledger'],
]

// Routed by hand where a straight elbow would cross another box.
const CUSTOM: Record<string, string> = { 'coach>ledger': 'M 600 266 C 645 266, 690 170, 700 104' }

function path(a: string, b: string) {
  if (CUSTOM[`${a}>${b}`]) return CUSTOM[`${a}>${b}`]
  const A = center(a), B = center(b)
  if (A.y === B.y) {
    const dir = B.x > A.x ? 1 : -1
    return `M ${A.x + (dir * A.n.w) / 2} ${A.y} L ${B.x - (dir * B.n.w) / 2} ${B.y}`
  }
  if (A.x === B.x) {
    const dir = B.y > A.y ? 1 : -1
    return `M ${A.x} ${A.y + (dir * H) / 2} L ${B.x} ${B.y - (dir * H) / 2}`
  }
  // Elbow: leave horizontally, arrive vertically.
  const sx = A.x + (B.x > A.x ? A.n.w / 2 : -A.n.w / 2)
  const ey = B.y + (A.y > B.y ? H / 2 : -H / 2)
  return `M ${sx} ${A.y} L ${B.x - 12 * Math.sign(B.x - sx)} ${A.y} Q ${B.x} ${A.y} ${B.x} ${A.y + 12 * Math.sign(ey - A.y)} L ${B.x} ${ey}`
}

/** Services and the money flowing between them; the dots travel the real event paths. */
export default function Architecture() {
  const reduce = useReducedMotion()
  return (
    <div className="overflow-x-auto">
      <svg viewBox="0 0 835 340" className="mx-auto min-w-[720px] max-w-full" role="img"
        aria-label="GigPay architecture: platforms send signed webhooks to earnings-service, which publishes to Kafka; ledger-service consumes, moves money through the payout bank; the gateway fronts the app; the coach reads earnings and ledger data.">
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#9aa39d" />
          </marker>
        </defs>
        {EDGES.map(([a, b], i) => {
          const d = path(a, b)
          return (
            <g key={a + b}>
              <path d={d} fill="none" stroke="#cfd3cc" strokeWidth="1.5" markerEnd="url(#arrow)" />
              {!reduce && (
                <circle r="3.5" fill="#17402a">
                  <animateMotion dur={`${2.6 + (i % 3) * 0.5}s`} repeatCount="indefinite" path={d} begin={`${i * 0.35}s`} />
                </circle>
              )}
            </g>
          )
        })}
        {NODES.map(n => (
          <g key={n.id} transform={`translate(${n.x} ${n.y})`}>
            <rect width={n.w} height={H} rx="12"
              fill={n.tone === 'bus' ? '#17402a' : n.tone === 'ext' ? '#f6f5f1' : '#ffffff'}
              stroke={n.tone === 'bus' ? '#17402a' : '#d6d3c8'} strokeDasharray={n.tone === 'ext' ? '4 4' : undefined} />
            <text x="16" y="27" className="font-sans" fontSize="14" fontWeight="600" fill={n.tone === 'bus' ? '#c6f36d' : '#0f1a14'}>{n.title}</text>
            <text x="16" y="46" className="font-mono" fontSize="11" fill={n.tone === 'bus' ? '#ffffffaa' : '#6b746e'}>{n.sub}</text>
          </g>
        ))}
      </svg>
    </div>
  )
}
