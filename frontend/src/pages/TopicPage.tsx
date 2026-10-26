import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api, type TopicDetail } from '../api'

export default function TopicPage() {
  const { id = '' } = useParams()
  return <TopicView key={id} id={id} />
}

function TopicView({ id }: { id: string }) {
  const [topic, setTopic] = useState<TopicDetail | null>(null)
  const [error, setError] = useState<ApiError | Error | null>(null)

  useEffect(() => {
    api.topic(id).then(setTopic).catch(setError)
  }, [id])

  const back = <Link to="/courses/java">← Roadmap</Link>

  if (error) {
    const locked = error instanceof ApiError && error.status === 403
    return (
      <div className="stack">
        {back}
        <div className="card" role={locked ? 'status' : 'alert'}>
          <h1>{locked ? 'This topic is locked' : 'Could not open this topic'}</h1>
          <p className="muted">{error.message}</p>
        </div>
      </div>
    )
  }
  if (!topic) return <p className="muted">Loading topic…</p>

  const search = topic.videoQuery
    ? `https://www.youtube.com/results?search_query=${encodeURIComponent(topic.videoQuery)}&sp=CAM%253D`
    : null

  return (
    <div className="stack">
      {back}
      <div className="top">
        <div>
          <p className="label">Topic {topic.position} of {topic.totalTopics} · {topic.estMinutes} min</p>
          <h1>{topic.title}</h1>
        </div>
        {search && <a className="btn" href={search} target="_blank" rel="noopener noreferrer">Watch top-viewed videos</a>}
      </div>

      {topic.hasContent ? (
        <>
          <section className="card prose"><h2>What is it?</h2>{paragraphs(topic.what)}</section>
          <section className="card prose"><h2>Why do we need it?</h2>{paragraphs(topic.why)}</section>
          {topic.exampleCode && (
            <section className="card"><h2>Real-world example</h2><pre className="code"><code>{topic.exampleCode}</code></pre></section>
          )}
          {topic.hints.length > 0 && (
            <section className="card prose"><h2>Stuck? Hints</h2><ul>{topic.hints.map((h) => <li key={h}>{h}</li>)}</ul></section>
          )}
        </>
      ) : (
        <section className="card" role="status">
          <h2>Lesson coming soon</h2>
          <p className="muted">The lesson for this topic is still being written. The roadmap and unlock rules already work.</p>
        </section>
      )}

      <section aria-label="Topic gate">
        <p className="label">Pass both rounds to unlock the next topic</p>
        <div className="grid">
          <div className="card">
            <div className="top"><h2>Round 1 · MCQ test</h2>{topic.mcqPassed && <span className="chip good">Passed</span>}</div>
            <p className="muted small">20 questions, half theory and half code. Score 14 or more to pass.</p>
            <button className="btn primary" disabled>Opens in the next release</button>
          </div>
          <div className="card">
            <div className="top"><h2>Round 2 · Coding</h2>{topic.codingPassed ? <span className="chip good">Passed</span> : !topic.mcqPassed && <span className="chip">Locked</span>}</div>
            <p className="muted small">5 problems: 2 easy, 1 medium and 2 hard.</p>
            <button className="btn primary" disabled>{topic.mcqPassed ? 'Opens in a later release' : 'Pass the MCQ test first'}</button>
          </div>
        </div>
      </section>
    </div>
  )
}

function paragraphs(text: string | null) {
  return (text ?? '').split('\n\n').filter(Boolean).map((p) => <p key={p}>{p}</p>)
}
