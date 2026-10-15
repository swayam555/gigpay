import { useEffect, useRef, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowUp, BookOpen, Database } from 'lucide-react'
import { send, type Answer } from '../api'
import { useApi } from '../hooks'
import { Badge, ErrorNote } from '../components/ui'

interface Message { role: 'user' | 'coach'; text: string; answer?: Answer }

/** Tiny renderer for the coach's replies: **bold**, bullet lines and paragraphs. No HTML is injected. */
function Rich({ text }: { text: string }) {
  return (
    <div className="prose-coach space-y-2.5">
      {text.split(/\n{2,}/).map((block, i) => {
        const lines = block.split('\n')
        const bold = (s: string) => s.split(/(\*\*[^*]+\*\*)/g).map((part, j) =>
          part.startsWith('**') && part.endsWith('**') ? <strong key={j}>{part.slice(2, -2)}</strong> : <span key={j}>{part}</span>)
        return lines.every(l => /^\s*[-*] /.test(l))
          ? <ul key={i} className="list-disc space-y-1 pl-5 marker:text-muted">{lines.map((l, j) => <li key={j}>{bold(l.replace(/^\s*[-*] /, ''))}</li>)}</ul>
          : <p key={i}>{lines.map((l, j) => <span key={j}>{j > 0 && <br />}{bold(l)}</span>)}</p>
      })}
    </div>
  )
}

export default function Coach() {
  const suggestions = useApi<string[]>('/api/coach/suggestions')
  const status = useApi<{ llmEnabled: boolean; chatModel: string; knowledgeChunks: number }>('/api/coach/status')
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const bottom = useRef<HTMLDivElement>(null)

  useEffect(() => { if (messages.length) bottom.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [messages, busy])

  const ask = async (q: string) => {
    if (!q.trim() || busy) return
    setError(null); setInput('')
    setMessages(m => [...m, { role: 'user', text: q }])
    setBusy(true)
    try {
      const a = await send<Answer>('/api/coach/chat', 'POST', { message: q })
      setMessages(m => [...m, { role: 'coach', text: a.answer, answer: a }])
    } catch (err) { setError((err as Error).message) }
    finally { setBusy(false) }
  }
  const submit = (e: FormEvent) => { e.preventDefault(); ask(input) }

  return (
    <div className="flex min-h-[calc(100vh-12rem)] flex-col">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-[26px] font-semibold tracking-[-0.03em] text-ink">Money coach</h1>
          <p className="mt-1 text-sm text-muted">Reads your own trips and wallet. Answers about tax, insurance and schemes come with sources.</p>
        </div>
        {status.data && (
          <Badge tone={status.data.llmEnabled ? 'pos' : 'neutral'} dot>
            {status.data.llmEnabled ? status.data.chatModel : `Built-in engine · ${status.data.knowledgeChunks} knowledge passages`}
          </Badge>
        )}
      </div>

      <div className="mt-6 flex-1 space-y-5">
        {messages.length === 0 && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="rounded-[var(--radius-card)] border border-line bg-surface p-6">
            <div className="text-[15px] font-semibold text-ink">Try asking</div>
            <div className="mt-4 grid gap-2 sm:grid-cols-2">
              {(suggestions.data ?? []).map((s, i) => (
                <motion.button key={s} onClick={() => ask(s)} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                  className="focus-ring rounded-lg border border-line px-3.5 py-2.5 text-left text-sm text-ink-2 transition hover:border-forest hover:bg-paper hover:text-ink">{s}</motion.button>
              ))}
            </div>
          </motion.div>
        )}
        <AnimatePresence initial={false}>
          {messages.map((m, i) => m.role === 'user' ? (
            <motion.div key={i} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
              className="ml-auto w-fit max-w-[80%] rounded-2xl rounded-br-md bg-forest px-4 py-2.5 text-[15px] text-white">{m.text}</motion.div>
          ) : (
            <motion.div key={i} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
              className="max-w-[92%] rounded-2xl rounded-bl-md border border-line bg-surface px-5 py-4 text-[15px] leading-relaxed text-ink-2 shadow-[var(--shadow-card)]">
              <Rich text={m.text} />
              {m.answer && (m.answer.toolsUsed.length > 0 || m.answer.citations.length > 0) && (
                <div className="mt-4 space-y-3 border-t border-line pt-3">
                  {m.answer.toolsUsed.length > 0 && (
                    <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted">
                      <Database className="h-3.5 w-3.5" /> Read your data:
                      {m.answer.toolsUsed.map(t => <span key={t} className="rounded-md bg-ink/[0.05] px-1.5 py-0.5 font-mono text-[11px] text-ink-2">{t}</span>)}
                    </div>
                  )}
                  {m.answer.citations.length > 0 && (
                    <div className="space-y-1.5">
                      {m.answer.citations.map(c => (
                        <details key={c.n} className="group rounded-lg border border-line bg-paper/60 px-3 py-2 text-xs">
                          <summary className="flex cursor-pointer list-none items-center gap-2 text-ink-2">
                            <BookOpen className="h-3.5 w-3.5 text-muted" /><span className="font-mono text-muted">[{c.n}]</span>{c.docTitle} · {c.section}
                          </summary>
                          <p className="mt-2 leading-relaxed text-ink-2">{c.snippet}</p>
                        </details>
                      ))}
                    </div>
                  )}
                  <div className="font-mono text-[11px] text-muted">{m.answer.generatedBy} · {m.answer.latencyMs} ms</div>
                </div>
              )}
            </motion.div>
          ))}
        </AnimatePresence>
        {busy && (
          <div className="flex w-fit items-center gap-1.5 rounded-2xl border border-line bg-surface px-4 py-3">
            {[0, 1, 2].map(i => <motion.span key={i} className="h-1.5 w-1.5 rounded-full bg-muted" animate={{ opacity: [0.3, 1, 0.3] }} transition={{ duration: 1, repeat: Infinity, delay: i * 0.15 }} />)}
          </div>
        )}
        <ErrorNote error={error} />
        <div ref={bottom} />
      </div>

      <form onSubmit={submit} className="sticky bottom-20 mt-6 flex items-center gap-2 rounded-2xl border border-line-2 bg-surface p-2 pl-4 shadow-[var(--shadow-pop)] sm:bottom-6">
        <input className="flex-1 bg-transparent text-[15px] text-ink outline-none placeholder:text-muted" placeholder="Ask about your pay, payouts, tax or insurance…"
          value={input} onChange={e => setInput(e.target.value)} maxLength={500} aria-label="Your question" />
        <button disabled={busy || !input.trim()} aria-label="Send"
          className="focus-ring flex h-9 w-9 items-center justify-center rounded-xl bg-forest text-white transition hover:bg-forest-2 disabled:opacity-30">
          <ArrowUp className="h-4 w-4" />
        </button>
      </form>
    </div>
  )
}
