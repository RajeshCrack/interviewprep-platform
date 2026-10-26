import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api, type CodingRound, type CodingSubmission } from '../api'

export default function CodingPage() {
  const { id = '' } = useParams()
  return <Loader key={id} topicId={id} />
}

function Loader({ topicId }: { topicId: string }) {
  const [round, setRound] = useState<CodingRound | null>(null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let live = true
    api.codingRound(topicId).then((r) => live && setRound(r)).catch((e: Error) => live && setError(e))
    return () => { live = false }
  }, [topicId])

  if (error) {
    const closed = error instanceof ApiError && error.status === 403
    return (
      <div className="stack">
        <Link to={`/topics/${topicId}`}>← Back to the topic</Link>
        <div className="card" role={closed ? 'status' : 'alert'}>
          <h1>{closed ? 'The coding round is closed' : 'The coding round could not open'}</h1>
          <p className="muted">{error.message}</p>
          {closed && <Link className="btn primary" to={`/topics/${topicId}/test`}>Go to the MCQ test</Link>}
        </div>
      </div>
    )
  }
  if (!round) return <p className="muted">Loading the coding round…</p>
  return <Workspace initial={round} topicId={topicId} />
}

const LEVEL: Record<string, string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' }

function Workspace({ initial, topicId }: { initial: CodingRound; topicId: string }) {
  const [round, setRound] = useState(initial)
  const [index, setIndex] = useState(0)
  const [codes, setCodes] = useState<Record<string, string>>(() =>
    Object.fromEntries(initial.problems.map((p) => [p.id, p.lastCode ?? p.starterCode])))
  const [results, setResults] = useState<Record<string, CodingSubmission>>({})
  const [busy, setBusy] = useState<'RUN' | 'SUBMIT' | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  const p = round.problems[index]
  const result = results[p.id]

  const send = async (mode: 'RUN' | 'SUBMIT') => {
    setBusy(mode)
    setProblem(null)
    try {
      const r = await api.submitCode(p.id, codes[p.id], mode)
      setResults((prev) => ({ ...prev, [p.id]: r }))
      if (r.solved || r.roundPassed) {
        setRound((cur) => {
          const problems = cur.problems.map((x) => (x.id === p.id ? { ...x, solved: x.solved || r.solved } : x))
          return {
            ...cur, problems, passed: cur.passed || r.roundPassed,
            solvedCount: problems.filter((x) => x.solved).length,
            solvedEasy: problems.filter((x) => x.solved && x.difficulty === 'EASY').length,
          }
        })
      }
      if (r.roundPassed && r.xpAwarded > 0) setBanner(`Round passed: +${r.xpAwarded} XP. The next topic is unlocked.`)
    } catch (e) {
      setProblem((e as Error).message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="stack">
      <Link to={`/topics/${topicId}`}>← Back to the topic</Link>
      <div className="top">
        <div>
          <p className="label">Round 2 · Coding</p>
          <h1>Coding round</h1>
        </div>
        <div className="chips">
          <span className={`chip${round.passed ? ' good' : ''}`}>{round.passed ? 'Passed' : `${round.solvedCount} of ${round.totalProblems} solved`}</span>
          {!round.passed && <span className="muted small">Pass rule: {round.neededTotal} solved, including both easy problems ({round.solvedEasy} of {round.totalEasy} easy so far).</span>}
        </div>
      </div>
      {banner && <p className="banner" role="status">{banner} <Link to="/courses/java">See the roadmap</Link></p>}

      <div role="tablist" aria-label="Problems" className="tabs">
        {round.problems.map((x, i) => (
          <button key={x.id} role="tab" aria-selected={i === index} className={`tab${i === index ? ' on' : ''}`} onClick={() => setIndex(i)}>
            {i + 1}. {x.title}<span className="muted small"> · {LEVEL[x.difficulty]}</span>{x.solved && <span className="tick" aria-label="solved"> ✓</span>}
          </button>
        ))}
      </div>

      <div className="code-layout">
        <section className="card" aria-label="Problem">
          <div className="top"><h2>{p.title}</h2><span className="chip">{LEVEL[p.difficulty]}</span></div>
          <div className="prose statement">{p.statement.split('\n\n').map((para) => <p key={para}>{para}</p>)}</div>
          {p.samples.map((s, i) => (
            <div key={i} className="sample">
              <p className="label">Sample {i + 1}</p>
              <div className="grid">
                <div><p className="small muted">Input</p><pre className="code"><code>{s.input}</code></pre></div>
                <div><p className="small muted">Expected output</p><pre className="code"><code>{s.expected}</code></pre></div>
              </div>
            </div>
          ))}
        </section>

        <section className="card" aria-label="Your solution">
          <Editor key={p.id} value={codes[p.id]} onChange={(v) => setCodes((c) => ({ ...c, [p.id]: v }))} />
          <div className="chips">
            <button className="btn" onClick={() => void send('RUN')} disabled={busy !== null}>{busy === 'RUN' ? 'Running…' : 'Run samples'}</button>
            <button className="btn primary" onClick={() => void send('SUBMIT')} disabled={busy !== null}>{busy === 'SUBMIT' ? 'Judging…' : 'Submit'}</button>
            <button className="btn ghost" onClick={() => setCodes((c) => ({ ...c, [p.id]: p.starterCode }))} disabled={busy !== null}>Reset code</button>
          </div>
          <p className="muted small">The class must be named Main. Read from standard input and print to standard output.</p>
          {problem && <p className="err" role="alert">{problem}</p>}
          {result && <Result result={result} />}
        </section>
      </div>
    </div>
  )
}

function Editor({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const escaped = useRef(false)
  const lines = value.split('\n').length
  return (
    <div className="editor">
      <pre className="gutter" aria-hidden="true">{Array.from({ length: lines }, (_, i) => i + 1).join('\n')}</pre>
      <textarea aria-label="Java code" spellCheck={false} value={value} rows={Math.max(14, lines + 1)}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') { escaped.current = true; return }
          if (e.key === 'Tab' && !e.shiftKey && !escaped.current) {
            // Tab indents. Press Escape first to let Tab leave the editor (keyboard users).
            e.preventDefault()
            const el = e.currentTarget
            const { selectionStart: a, selectionEnd: b } = el
            onChange(value.slice(0, a) + '    ' + value.slice(b))
            requestAnimationFrame(() => { el.selectionStart = el.selectionEnd = a + 4 })
          }
          if (e.key !== 'Escape' && e.key !== 'Tab') escaped.current = false
        }} />
    </div>
  )
}

const STATUS_TEXT: Record<string, string> = {
  ACCEPTED: 'Accepted', WRONG_ANSWER: 'Wrong answer', COMPILE_ERROR: 'Compile error', RUNTIME_ERROR: 'Runtime error', TIME_LIMIT: 'Time limit exceeded',
}

function Result({ result }: { result: CodingSubmission }) {
  const good = result.status === 'ACCEPTED'
  return (
    <div className="result-box" role="region" aria-label="Result">
      <div className="chips">
        <span className={`chip ${good ? 'good' : 'spark'}`}>{STATUS_TEXT[result.status]}</span>
        <span>{result.passedTests} of {result.totalTests} tests passed{result.mode === 'RUN' ? ' (samples only)' : ''}</span>
        {result.mode === 'SUBMIT' && good && <span className="chip good">Problem solved</span>}
      </div>
      {result.message && <pre className="code err-out"><code>{result.message}</code></pre>}
      <ul className="tests">
        {result.tests.map((t) => (
          <li key={t.position} className={t.passed ? 'ok' : 'bad'}>
            <p><strong>{t.visible ? `Sample test ${t.position}` : `Hidden test ${t.position}`}</strong> · {t.passed ? 'passed' : t.status === 'NOT_RUN' ? 'not run' : t.status.replace('_', ' ').toLowerCase()}</p>
            {!t.passed && t.visible && t.input !== null && (
              <div className="grid">
                <div><p className="small muted">Input</p><pre className="code"><code>{t.input}</code></pre></div>
                <div><p className="small muted">Expected</p><pre className="code"><code>{t.expected}</code></pre></div>
                <div><p className="small muted">Your output</p><pre className="code"><code>{t.actual || '(nothing)'}</code></pre></div>
              </div>
            )}
            {!t.passed && t.error && <pre className="code err-out"><code>{t.error}</code></pre>}
          </li>
        ))}
      </ul>
    </div>
  )
}
