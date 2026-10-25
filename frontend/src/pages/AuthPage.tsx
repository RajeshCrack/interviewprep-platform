import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { validateLogin, validateRegister, type FieldErrors } from '../lib/validation'

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const isRegister = mode === 'register'
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const found = isRegister ? validateRegister({ name, email, password }) : validateLogin(email, password)
    setErrors(found); setFormError('')
    if (Object.keys(found).length) return
    setBusy(true)
    try {
      if (isRegister) await register(name.trim(), email.trim(), password)
      else await login(email.trim(), password)
      navigate('/')
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Something went wrong.')
    } finally { setBusy(false) }
  }

  return (
    <div className="auth">
      <section className="hero">
        <p className="brand"><span className="logo" aria-hidden="true" />GateReady</p>
        <h1>Pass the gate. Unlock the next topic.</h1>
        <p className="muted">Interview prep that moves one topic at a time. Clear a 20-question test and a coding round to move on, and a daily goal keeps you going.</p>
        <ol className="steps">
          <li><b>Learn</b> the topic with a real example.</li>
          <li><b>Clear</b> the MCQ test with 14 of 20.</li>
          <li><b>Solve</b> the coding round, then the next topic opens.</li>
        </ol>
      </section>
      <form className="card" onSubmit={submit} noValidate aria-label={isRegister ? 'Register' : 'Log in'}>
        <h2>{isRegister ? 'Create your account' : 'Welcome back'}</h2>
        {isRegister && (
          <Field id="name" label="Full name" value={name} onChange={setName} error={errors.name} autoComplete="name" />
        )}
        <Field id="email" label="Email" type="email" value={email} onChange={setEmail} error={errors.email} autoComplete="email" />
        <Field id="password" label="Password" type="password" value={password} onChange={setPassword} error={errors.password}
          autoComplete={isRegister ? 'new-password' : 'current-password'} />
        <p className="err" role="alert">{formError}</p>
        <button className="btn primary" disabled={busy}>{busy ? 'Please wait…' : isRegister ? 'Create account' : 'Log in'}</button>
        <p className="muted small">
          {isRegister ? 'Already registered? ' : 'New here? '}
          <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Log in' : 'Create an account'}</Link>
        </p>
      </form>
    </div>
  )
}

function Field(p: { id: string; label: string; value: string; onChange(v: string): void; error?: string; type?: string; autoComplete?: string }) {
  return (
    <div className="field">
      <label htmlFor={p.id}>{p.label}</label>
      <input id={p.id} type={p.type ?? 'text'} value={p.value} autoComplete={p.autoComplete}
        aria-invalid={!!p.error} aria-describedby={p.error ? `${p.id}-err` : undefined}
        onChange={(e) => p.onChange(e.target.value)} />
      {p.error && <span className="err" id={`${p.id}-err`}>{p.error}</span>}
    </div>
  )
}
