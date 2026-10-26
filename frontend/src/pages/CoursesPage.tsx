import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type CourseSummary } from '../api'

export default function CoursesPage() {
  const [courses, setCourses] = useState<CourseSummary[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.courses().then(setCourses).catch((e: Error) => setError(e.message))
  }, [])

  if (error) return <p className="err" role="alert">{error}</p>
  if (!courses) return <p className="muted">Loading courses…</p>

  return (
    <div className="stack">
      <div>
        <p className="label">Course catalog</p>
        <h1>Pick a course</h1>
      </div>
      <div className="grid">
        {courses.map((c) => (
          <article key={c.slug} className={`card course${c.published ? '' : ' soon'}`} aria-label={c.title}>
            <div className="top">
              <span className="ico" aria-hidden="true">{c.icon}</span>
              <span className={`chip${c.published ? ' good' : ''}`}>{c.published ? 'Available' : 'Coming soon'}</span>
            </div>
            <h2>{c.title}</h2>
            <p className="muted small">{c.description}</p>
            {c.published
              ? <Link className="btn primary" to={`/courses/${c.slug}`}>Open roadmap</Link>
              : <span className="muted small">Not open yet</span>}
          </article>
        ))}
      </div>
    </div>
  )
}
