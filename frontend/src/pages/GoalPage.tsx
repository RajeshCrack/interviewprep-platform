import { useEffect, useState, type FormEvent } from 'react'
import { api, type Channel } from '../api'
import { daysToFinish } from '../lib/progress'

const JAVA_HOURS = 8.5

export default function GoalPage() {
  const [hours, setHours] = useState(2)
  const [time, setTime] = useState('20:00')
  const [channel, setChannel] = useState<Channel>('PUSH')
  const [status, setStatus] = useState('')
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    api.dashboard().then((d) => { setHours(d.goal.hoursPerDay); setTime(d.goal.reminderTime); setChannel(d.goal.channel) }).catch(() => undefined).finally(() => setLoaded(true))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    try { await api.saveGoal({ hoursPerDay: hours, reminderTime: time, channel }); setStatus('Goal saved.') }
    catch (err) { setStatus((err as Error).message) }
  }

  if (!loaded) return <p className="muted">Loading your goal…</p>

  return (
    <form className="card stack" onSubmit={save} aria-label="Study goal">
      <h1>How much will you study?</h1>
      <div className="field">
        <label htmlFor="hours">Hours per day: {hours}</label>
        <input id="hours" type="range" min={0.5} max={4} step={0.5} value={hours} onChange={(e) => setHours(Number(e.target.value))} />
        <span className="muted small">Java is about {JAVA_HOURS} hours of material. At this pace you finish in {daysToFinish(JAVA_HOURS, hours)} study days.</span>
      </div>
      <div className="field">
        <label htmlFor="time">Reminder time</label>
        <input id="time" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
      </div>
      <fieldset className="field">
        <legend>Reminder channel</legend>
        {(['PUSH', 'EMAIL'] as const).map((c) => (
          <label key={c} className="radio">
            <input type="radio" name="channel" checked={channel === c} onChange={() => setChannel(c)} />
            {c === 'PUSH' ? 'Push notification' : 'Email'}
          </label>
        ))}
      </fieldset>
      <button className="btn primary">Save goal</button>
      <p role="status" className="muted">{status}</p>
    </form>
  )
}
