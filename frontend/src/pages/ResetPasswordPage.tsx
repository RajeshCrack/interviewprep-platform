import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { validateNewPassword, type NewPasswordErrors } from '../lib/validation'
import Field from './Field'

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<NewPasswordErrors>({})
  const [formError, setFormError] = useState('')
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const found = validateNewPassword(password, confirm)
    setErrors(found); setFormError('')
    if (Object.keys(found).length) return
    setBusy(true)
    try {
      await api.resetPassword(token, password)
      setDone(true)
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Something went wrong.')
    } finally { setBusy(false) }
  }

  if (!token) {
    return (
      <div className="auth single">
        <section className="card" aria-label="Invalid link">
          <h2>This link is incomplete</h2>
          <p className="muted">Open the link from your email again, or ask for a new one.</p>
          <Link className="btn primary" to="/forgot-password">Request a new link</Link>
        </section>
      </div>
    )
  }

  return (
    <div className="auth single">
      {done ? (
        <section className="card" aria-label="Password changed">
          <h2>Password changed</h2>
          <p>You were signed out everywhere. Log in with your new password.</p>
          <Link className="btn primary" to="/login">Log in</Link>
        </section>
      ) : (
        <form className="card" onSubmit={submit} noValidate aria-label="Reset password">
          <h2>Choose a new password</h2>
          <Field id="password" label="New password" type="password" value={password} onChange={setPassword} error={errors.password} autoComplete="new-password" />
          <Field id="confirm" label="Confirm new password" type="password" value={confirm} onChange={setConfirm} error={errors.confirm} autoComplete="new-password" />
          <p className="err" role="alert">{formError}</p>
          {formError && <p className="small"><Link to="/forgot-password">Request a new link</Link></p>}
          <button className="btn primary" disabled={busy}>{busy ? 'Please wait…' : 'Change password'}</button>
        </form>
      )}
    </div>
  )
}
