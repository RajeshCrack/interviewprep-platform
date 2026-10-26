import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { validateEmail } from '../lib/validation'
import Field from './Field'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const found = validateEmail(email)
    setError(found ?? ''); setFormError('')
    if (found) return
    setBusy(true)
    try {
      await api.forgotPassword(email.trim())
      setSent(true)
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Something went wrong.')
    } finally { setBusy(false) }
  }

  return (
    <div className="auth single">
      {sent ? (
        <section className="card" aria-label="Check your email">
          <h2>Check your email</h2>
          <p>If <b>{email.trim()}</b> is registered, we sent a link to reset the password. It works once and expires in 1 hour.</p>
          <p className="muted small">Nothing arrived? Check spam, or <button className="linklike" onClick={() => setSent(false)}>try another address</button>.</p>
          <Link to="/login">Back to log in</Link>
        </section>
      ) : (
        <form className="card" onSubmit={submit} noValidate aria-label="Forgot password">
          <h2>Forgot your password?</h2>
          <p className="muted">Enter your email and we will send you a link to choose a new one.</p>
          <Field id="email" label="Email" type="email" value={email} onChange={setEmail} error={error} autoComplete="email" />
          <p className="err" role="alert">{formError}</p>
          <button className="btn primary" disabled={busy}>{busy ? 'Please wait…' : 'Send reset link'}</button>
          <p className="small"><Link to="/login">Back to log in</Link></p>
        </form>
      )}
    </div>
  )
}
