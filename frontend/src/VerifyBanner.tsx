import { useState } from 'react'
import { api, ApiError } from './api'
import { useAuth } from './auth'

/** Shown to students whose email is not confirmed yet. Reminder emails wait until it is. */
export default function VerifyBanner() {
  const { user, refreshUser } = useAuth()
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  async function resend() {
    setBusy(true); setNote('')
    try {
      await api.resendVerification()
      setNote('Link sent. Check your inbox.')
    } catch (err) {
      setNote(err instanceof ApiError || err instanceof Error ? err.message : 'Could not send the link.')
    } finally { setBusy(false) }
  }

  return (
    <div className="banner verify" role="status">
      <span>Confirm your email <b>{user?.email}</b> so reminders can reach you.</span>
      <button className="btn" onClick={resend} disabled={busy}>Resend link</button>
      <button className="btn ghost" onClick={() => void refreshUser()}>I confirmed it</button>
      {note && <span className="small" aria-live="polite">{note}</span>}
    </div>
  )
}
