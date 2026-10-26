import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Badge, type Dashboard, type NotificationList, type RoadmapTopic } from '../api'
import { levelFromXp, reminderMessage, ringFraction, xpToNextLevel } from '../lib/progress'

export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')
  const [current, setCurrent] = useState<RoadmapTopic | null>(null)
  const [inbox, setInbox] = useState<NotificationList | null>(null)
  const [badges, setBadges] = useState<Badge[]>([])

  const load = useCallback(() => {
    api.dashboard().then(setData).catch((e: Error) => setError(e.message))
  }, [])
  useEffect(load, [load])
  useEffect(() => {
    api.roadmap('java').then((r) => setCurrent(r.topics.find((t) => t.status === 'CURRENT') ?? null)).catch(() => undefined)
  }, [])

  useEffect(() => {
    api.notifications().then(setInbox).catch(() => undefined)
    api.badges().then(setBadges).catch(() => undefined)
  }, [])

  async function log() {
    try {
      setData(await api.logStudy(15))
      api.notifications().then(setInbox).catch(() => undefined) // a goal or badge may have just created one
      api.badges().then(setBadges).catch(() => undefined)
    } catch (e) { setError((e as Error).message) }
  }

  async function markRead() {
    try {
      await api.markNotificationsRead()
      setInbox((cur) => (cur ? { unread: 0, items: cur.items.map((n) => ({ ...n, read: true })) } : cur))
    } catch { /* the list stays as it was */ }
  }

  if (error) return <p className="err" role="alert">{error}</p>
  if (!data) return <p className="muted">Loading your dashboard…</p>

  const goalMin = Math.round(data.goal.hoursPerDay * 60)
  const fraction = ringFraction(data.todayMinutes, goalMin)
  const r = 56, c = 2 * Math.PI * r

  return (
    <div className="stack">
      <div className="top">
        <h1>Hi {data.user.name.split(' ')[0]}</h1>
        <button className="btn" onClick={log}>Log 15 minutes of study</button>
      </div>
      <p className="banner" role="status">{reminderMessage(data.todayMinutes, goalMin)}</p>
      <div className="grid">
        <section className="card ring-card" aria-label="Daily goal">
          <svg viewBox="0 0 132 132" width="132" height="132" role="img" aria-label={`${Math.round(fraction * 100)} percent of daily goal`}>
            <circle cx="66" cy="66" r={r} fill="none" stroke="var(--line)" strokeWidth="12" />
            <circle cx="66" cy="66" r={r} fill="none" stroke={fraction >= 1 ? 'var(--good)' : 'var(--primary)'} strokeWidth="12"
              strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c * (1 - fraction)} transform="rotate(-90 66 66)" />
          </svg>
          <div>
            <p className="label">Daily goal</p>
            <h2>{data.todayMinutes}m of {goalMin}m</h2>
            <p className="muted small">Reminder at {data.goal.reminderTime} {data.goal.channel === 'PUSH' ? 'in the app' : 'in the app and by email'}.</p>
          </div>
        </section>
        <section className="card">
          <p className="label">Streak</p>
          <h2>{data.streak} {data.streak === 1 ? 'day' : 'days'}</h2>
          <p className="muted small">Meet your daily goal to extend it.</p>
        </section>
        <section className="card">
          <p className="label">Level {levelFromXp(data.xp)}</p>
          <h2>{data.xp} XP</h2>
          <p className="muted small">{xpToNextLevel(data.xp)} XP to the next level.</p>
        </section>
      </div>
      {inbox && inbox.items.length > 0 && (
        <section className="card" aria-label="Notifications">
          <div className="top">
            <h2>Notifications {inbox.unread > 0 && <span className="chip spark">{inbox.unread} new</span>}</h2>
            {inbox.unread > 0 && <button className="btn ghost" onClick={() => void markRead()}>Mark all read</button>}
          </div>
          <ul className="notes">
            {inbox.items.slice(0, 5).map((n) => (
              <li key={n.id} className={n.read ? '' : 'unread'}>
                <strong>{n.title}</strong>
                <span className="muted small"> {n.body}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
      {badges.length > 0 && (
        <section className="card" aria-label="Badges">
          <h2>Badges · {badges.filter((b) => b.earned).length} of {badges.length}</h2>
          <ul className="badges">
            {badges.map((b) => (
              <li key={b.key} className={b.earned ? 'earned' : 'locked'} title={b.description}>
                <strong>{b.title}</strong>
                <span className="muted small">{b.earned ? 'Earned' : b.description}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
      {current && (
        <section className="card" aria-label="Continue learning">
          <p className="label">Continue learning · Java · topic {current.position}</p>
          <h2>{current.title}</h2>
          <p><Link className="btn primary" to={`/topics/${current.id}`}>Continue</Link></p>
        </section>
      )}
    </div>
  )
}
