import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api, type McqAttempt } from '../api'

export default function McqPage() {
  const { id = '' } = useParams()
  return <Loader key={id} topicId={id} />
}

function Loader({ topicId }: { topicId: string }) {
  const [attempt, setAttempt] = useState<McqAttempt | null>(null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let live = true
    // The server resumes a running test, so a refresh or a double call never costs an attempt.
    api.startMcq(topicId).then((a) => live && setAttempt(a)).catch((e: Error) => live && setError(e))
    return () => { live = false }
  }, [topicId])

  const back = <Link to={`/topics/${topicId}`}>← Back to the topic</Link>
  if (error) {
    const status = error instanceof ApiError ? error.status : 0
    const title = status === 403 ? 'This topic is locked' : status === 409 ? 'You have already passed this test' : 'The test could not start'
    return (
      <div className="stack">
        {back}
        <div className="card" role="alert"><h1>{title}</h1><p className="muted">{error.message}</p></div>
      </div>
    )
  }
  if (!attempt) return <p className="muted">Preparing your test…</p>
  return <Runner attempt={attempt} topicId={topicId} />
}

const fmt = (ms: number) => {
  const s = Math.ceil(ms / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

function Runner({ attempt, topicId }: { attempt: McqAttempt; topicId: string }) {
  const navigate = useNavigate()
  const questions = [...attempt.questions].sort((a, b) => a.position - b.position)
  const [answers, setAnswers] = useState<(number | null)[]>(() => questions.map((q) => q.selected))
  const [index, setIndex] = useState(0)
  const [now, setNow] = useState(() => Date.now())
  const [confirming, setConfirming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [problem, setProblem] = useState<{ message: string; gone: boolean } | null>(null)

  const expiry = Date.parse(attempt.expiresAt)
  const remaining = Math.max(0, expiry - now)
  const expired = remaining === 0
  const answered = answers.filter((a) => a !== null).length
  const q = questions[index]

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  const submit = async () => {
    if (submitting) return
    setSubmitting(true)
    setProblem(null)
    try {
      const r = await api.submitMcq(attempt.id, answers)
      navigate(`/tests/${r.attemptId}/result`, { state: { xpAwarded: r.xpAwarded } })
    } catch (e) {
      setSubmitting(false)
      setConfirming(false)
      setProblem({ message: (e as Error).message, gone: e instanceof ApiError && e.status === 410 })
    }
  }

  // When the clock runs out, send whatever has been answered.
  useEffect(() => {
    if (expired) void submit()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expired])

  const choose = (option: number) => {
    if (submitting || expired) return
    setAnswers((prev) => prev.map((a, i) => (i === index ? option : a)))
    api.saveMcqAnswer(attempt.id, q.position, option).catch(() => undefined) // submit sends everything again
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey || confirming || submitting) return
      if (['1', '2', '3', '4'].includes(e.key)) choose(Number(e.key) - 1)
      else if (e.key === 'ArrowRight') setIndex((i) => Math.min(i + 1, questions.length - 1))
      else if (e.key === 'ArrowLeft') setIndex((i) => Math.max(i - 1, 0))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  if (problem?.gone) {
    return (
      <div className="stack">
        <div className="card" role="alert">
          <h1>Time is up</h1>
          <p className="muted">{problem.message}</p>
          <Link className="btn primary" to={`/topics/${topicId}`}>Back to the topic</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="stack">
      <div className="top">
        <div>
          <p className="label">MCQ test · {questions.length} questions · pass mark 14</p>
          <h1>Question {index + 1} of {questions.length}</h1>
        </div>
        <p className={`timer${remaining < 5 * 60_000 ? ' low' : ''}`} role="timer" aria-label="Time left">{fmt(remaining)}</p>
      </div>

      <div className="test-layout">
        <section className="card" aria-label={`Question ${index + 1}`}>
          <p className="label">{q.kind === 'THEORY' ? 'Theory' : 'Code'}</p>
          <p className="prompt">{q.prompt}</p>
          {q.codeSnippet && <pre className="code"><code>{q.codeSnippet}</code></pre>}
          <div role="radiogroup" aria-label="Answer options" className="options">
            {q.options.map((text, i) => (
              <button key={i} type="button" role="radio" aria-checked={answers[index] === i}
                className={`option${answers[index] === i ? ' picked' : ''}`} onClick={() => choose(i)} disabled={submitting}>
                <span className="key" aria-hidden="true">{i + 1}</span><span>{text}</span>
              </button>
            ))}
          </div>
          <div className="top">
            <button className="btn" onClick={() => setIndex(index - 1)} disabled={index === 0}>Previous</button>
            {index < questions.length - 1
              ? <button className="btn primary" onClick={() => setIndex(index + 1)}>Next</button>
              : <button className="btn primary" onClick={() => setConfirming(true)} disabled={submitting}>Review and submit</button>}
          </div>
          <p className="muted small">Keys: 1 to 4 pick an answer, left and right arrows move between questions.</p>
        </section>

        <aside className="card" aria-label="Question palette">
          <p className="label">{answered} of {questions.length} answered</p>
          <nav className="palette" aria-label="Jump to question">
            {questions.map((item, i) => (
              <button key={item.position} type="button" onClick={() => setIndex(i)}
                className={`pal${answers[i] !== null ? ' done' : ''}${i === index ? ' here' : ''}`}
                aria-current={i === index ? 'step' : undefined}
                aria-label={`Question ${i + 1}, ${answers[i] !== null ? 'answered' : 'not answered'}`}>{i + 1}</button>
            ))}
          </nav>
          <button className="btn primary" onClick={() => setConfirming(true)} disabled={submitting}>Submit test</button>
        </aside>
      </div>

      {problem && <p className="err" role="alert">{problem.message}</p>}

      {confirming && (
        <div className="card confirm" role="alertdialog" aria-label="Submit the test?">
          <h2>Submit the test?</h2>
          <p>
            You answered {answered} of {questions.length}.
            {answered < questions.length && ` ${questions.length - answered} left blank will count as wrong.`} You cannot change answers after this.
          </p>
          <div className="chips">
            <button className="btn" onClick={() => setConfirming(false)} disabled={submitting}>Keep working</button>
            <button className="btn primary" onClick={() => void submit()} disabled={submitting}>{submitting ? 'Grading…' : 'Submit now'}</button>
          </div>
        </div>
      )}
    </div>
  )
}
