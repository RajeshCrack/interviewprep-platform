import { useCallback, useEffect, useState } from 'react'
import { api, type Dashboard } from '../api'
import { levelFromXp, reminderMessage, ringFraction, xpToNextLevel } from '../lib/progress'

export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api.dashboard().then(setData).catch((e: Error) => setError(e.message))
  }, [])
  useEffect(load, [load])

  async function log() {
    try { setData(await api.logStudy(15)) } catch (e) { setError((e as Error).message) }
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
            <p className="muted small">Reminder at {data.goal.reminderTime} by {data.goal.channel === 'PUSH' ? 'push notification' : 'email'}.</p>
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
    </div>
  )
}
