import { animate, motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState, type ReactNode } from 'react'

/** Fades and lifts its children into place the first time they scroll into view. */
export function Reveal({ children, delay = 0, className, y = 18 }: { children: ReactNode; delay?: number; className?: string; y?: number }) {
  return (
    <motion.div className={className} initial={{ opacity: 0, y }} whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-60px' }} transition={{ duration: 0.6, delay, ease: [0.21, 0.47, 0.32, 0.98] }}>
      {children}
    </motion.div>
  )
}

/** Counts from 0 to `to` when it scrolls into view. */
export function CountUp({ to, decimals = 0, prefix = '', suffix = '', duration = 1.4 }: {
  to: number; decimals?: number; prefix?: string; suffix?: string; duration?: number
}) {
  const ref = useRef<HTMLSpanElement>(null)
  const inView = useInView(ref, { once: true })
  const reduce = useReducedMotion()
  const [v, setV] = useState(reduce ? to : 0)
  useEffect(() => {
    if (!inView || reduce) return
    const c = animate(0, to, { duration, ease: 'easeOut', onUpdate: setV })
    return () => c.stop()
  }, [inView, to, duration, reduce])
  return <span ref={ref} className="num">{prefix}{v.toLocaleString('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}{suffix}</span>
}

/** A number that glides to each new value. */
export function Ticker({ value, format }: { value: number; format: (n: number) => string }) {
  const [v, setV] = useState(value)
  const prev = useRef(value)
  useEffect(() => {
    const c = animate(prev.current, value, { duration: 0.7, ease: 'easeOut', onUpdate: setV })
    prev.current = value
    return () => c.stop()
  }, [value])
  return <span className="num">{format(v)}</span>
}
