import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Roadmap, type RoadmapTopic } from '../api'

function Node({ t }: { t: RoadmapTopic }) {
  const locked = t.status === 'LOCKED'
  const body = (
    <>
      <div className="top">
        <h2>{t.title}</h2>
        <span className="muted small">{t.estMinutes} min</span>
      </div>
      <div className="chips">
        <span className={`chip${t.mcqPassed ? ' good' : ''}`}>MCQ {t.mcqPassed ? 'passed' : '20 questions'}</span>
        <span className={`chip${t.codingPassed ? ' good' : ''}`}>Code {t.codingPassed ? 'passed' : '5 problems'}</span>
        {t.status === 'CURRENT' && <span className="chip spark">Up next</span>}
        {locked && <span className="muted small">Pass topic {t.position - 1} to unlock</span>}
      </div>
    </>
  )
  return (
    <li className={`node ${t.status.toLowerCase()}`} data-status={t.status}>
      <span className="dot" aria-hidden="true">{t.status === 'PASSED' ? '✓' : locked ? '🔒' : t.position}</span>
      {locked
        ? <div className="body" role="group" aria-label={`${t.title}, locked`}>{body}</div>
        : <Link className="body" to={`/topics/${t.id}`}>{body}</Link>}
    </li>
  )
}

export default function RoadmapPage() {
  const { slug = '' } = useParams()
  const [roadmap, setRoadmap] = useState<Roadmap | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.roadmap(slug).then(setRoadmap).catch((e: Error) => setError(e.message))
  }, [slug])

  if (error) return <div className="stack"><p className="err" role="alert">{error}</p><Link to="/courses">Back to courses</Link></div>
  if (!roadmap) return <p className="muted">Loading your roadmap…</p>

  const total = roadmap.topics.length
  return (
    <div className="stack">
      <div className="top">
        <div>
          <p className="label">{roadmap.course.title} · roadmap</p>
          <h1>Your path</h1>
        </div>
        <div className="progress">
          <p className="small muted">{roadmap.passedCount} of {total} topics passed</p>
          <progress max={total} value={roadmap.passedCount} aria-label="Topics passed" />
        </div>
      </div>
      <ol className="path">
        {roadmap.topics.map((t) => <Node key={t.id} t={t} />)}
      </ol>
    </div>
  )
}
