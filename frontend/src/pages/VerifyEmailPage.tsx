import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

/** The student presses a button instead of the page verifying on load, so a mail scanner or a double render cannot use up the one-time link. */
export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { user, refreshUser } = useAuth()
  const [state, setState] = useState<'idle' | 'busy' | 'done' | 'failed'>('idle')
  const [message, setMessage] = useState('')

  async function confirm() {
    setState('busy'); setMessage('')
    try {
      await api.verifyEmail(token)
      setState('done')
      if (user) await refreshUser().catch(() => undefined)
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Something went wrong.')
      setState('failed')
    }
  }

  return (
    <div className="auth single">
      <section className="card" aria-label="Verify email">
        {state === 'done' ? (
          <>
            <h2>Email confirmed</h2>
            <p>Thank you. Study reminders can now reach this address.</p>
            <Link className="btn primary" to={user ? '/' : '/login'}>{user ? 'Go to your dashboard' : 'Log in'}</Link>
          </>
        ) : !token ? (
          <>
            <h2>This link is incomplete</h2>
            <p className="muted">Open the link from your email again. You can ask for a new one from your dashboard after logging in.</p>
            <Link className="btn primary" to="/login">Log in</Link>
          </>
        ) : (
          <>
            <h2>Confirm your email</h2>
            <p className="muted">Press the button to confirm this address for GateReady.</p>
            <p className="err" role="alert">{message}</p>
            <button className="btn primary" onClick={confirm} disabled={state === 'busy'}>{state === 'busy' ? 'Please wait…' : 'Confirm email'}</button>
            {state === 'failed' && <p className="small"><Link to={user ? '/' : '/login'}>{user ? 'Back to dashboard to get a new link' : 'Log in to get a new link'}</Link></p>}
          </>
        )}
      </section>
    </div>
  )
}
