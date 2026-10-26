import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api, type McqReviewItem, type McqResult } from '../api'

export default function McqResultPage() {
  const { attemptId = '' } = useParams()
  return <ResultView key={attemptId} attemptId={attemptId} />
}

const groupLabel = (kind: string, difficulty: string) =>
  `${kind === 'THEORY' ? 'Theory' : 'Code'} · ${difficulty === 'EASY' ? 'easy' : 'hard'}`

function ResultView({ attemptId }: { attemptId: string }) {
  const location = useLocation()
  const navigate = useNavigate()
  // The award is only known right after submitting. Read it once, then clear it so a reload does not show it again.
  const [xp] = useState(() => (location.state as { xpAwarded?: number } | null)?.xpAwarded ?? 0)
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, navigate])
  const [result, setResult] = useState<McqResult | null>(null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let live = true
    api.mcqResult(attemptId).then((r) => live && setResult(r)).catch((e: Error) => live && setError(e))
    return () => { live = false }
  }, [attemptId])

  if (error) {
    return (
      <div className="stack">
        <Link to="/courses/java">← Roadmap</Link>
        <div className="card" role="alert">
          <h1>{error instanceof ApiError && error.status === 409 ? 'This test is still running' : 'Result not available'}</h1>
          <p className="muted">{error.message}</p>
        </div>
      </div>
    )
  }
  if (!result) return <p className="muted">Loading your result…</p>

  const wrong = result.review.filter((r) => !r.isCorrect).length
  return (
    <div className="stack">
      <Link to={`/topics/${result.topicId}`}>← Back to the topic</Link>
      <section className={`card result ${result.passed ? 'pass' : 'fail'}`} aria-label="Result">
        <p className="label">MCQ test result</p>
        <p className="score"><span>{result.score}</span> / {result.total}</p>
        <h1>{result.passed ? 'Passed' : 'Not yet'}</h1>
        <p className="muted">
          {result.passed
            ? 'You cleared the pass mark of ' + result.passMark + '. The coding round is next, and the next topic unlocks once you pass it too.'
            : `You need ${result.passMark} to pass and scored ${result.score}. A new test draws fresh questions, so read the explanations below first.`}
        </p>
        {xp > 0 && <p className="banner">+{xp} XP for passing</p>}
        <div className="chips">
          {result.passed
            ? <Link className="btn primary" to={`/topics/${result.topicId}`}>Continue</Link>
            : <Link className="btn primary" to={`/topics/${result.topicId}/test`}>Try again with new questions</Link>}
          {!result.passed && <Link className="btn" to={`/topics/${result.topicId}`}>Revise the lesson</Link>}
        </div>
      </section>

      <section className="card" aria-label="Score by group">
        <h2>Where you scored</h2>
        <div className="grid">
          {result.groups.map((g) => (
            <div key={`${g.kind}-${g.difficulty}`}>
              <p className="label">{groupLabel(g.kind, g.difficulty)}</p>
              <p className="group-score">{g.correct} / {g.total}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="stack" aria-label="Review">
        <h2>Review · {wrong} to learn from</h2>
        {result.review.map((item) => <Review key={item.position} item={item} />)}
      </section>
    </div>
  )
}

function Review({ item }: { item: McqReviewItem }) {
  return (
    <details className={`card review ${item.isCorrect ? 'right' : 'wrong'}`} open={!item.isCorrect}>
      <summary>
        <span className={`chip ${item.isCorrect ? 'good' : 'spark'}`}>{item.isCorrect ? 'Correct' : item.selected === null ? 'Blank' : 'Wrong'}</span>
        <span> Q{item.position} · {item.prompt}</span>
      </summary>
      {item.codeSnippet && <pre className="code"><code>{item.codeSnippet}</code></pre>}
      <ul className="review-options">
        {item.options.map((text, i) => (
          <li key={i} className={i === item.correct ? 'is-correct' : i === item.selected ? 'is-picked' : ''}>
            <span className="key" aria-hidden="true">{i + 1}</span>
            <span>{text}</span>
            {i === item.correct && <span className="chip good">Right answer</span>}
            {i === item.selected && i !== item.correct && <span className="chip spark">Your answer</span>}
            {i === item.selected && i === item.correct && <span className="chip good">Your answer</span>}
          </li>
        ))}
      </ul>
      <p><strong>Why: </strong>{item.explanation}</p>
    </details>
  )
}
