import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

/**
 * Fetches a path, optionally re-polling every `everyMs`. `reload` refetches now. When the path
 * changes, data for the old path is not returned, so the page shows its loading state instead
 * of stale rows that are about to be replaced.
 */
export function useApi<T>(path: string | null, everyMs?: number) {
  const [state, setState] = useState<{ path: string; data: T } | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)
  const reload = useCallback(() => setTick(t => t + 1), [])

  useEffect(() => {
    if (!path) return
    let live = true
    const load = () => api<T>(path).then(d => { if (live) { setState({ path, data: d }); setError(null) } })
      .catch(e => { if (live) setError((e as Error).message) })
    load()
    const id = everyMs ? setInterval(load, everyMs) : undefined
    return () => { live = false; if (id) clearInterval(id) }
  }, [path, everyMs, tick])

  return { data: state && state.path === path ? state.data : undefined, error, reload }
}
