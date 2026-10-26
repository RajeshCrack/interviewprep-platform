import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'
import { mockApi } from './api/mock'
import { AuthProvider } from './auth'
import VerifyBanner from './VerifyBanner'
import AuthPage from './pages/AuthPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import { validateNewPassword } from './lib/validation'

afterEach(() => vi.restoreAllMocks())

function at(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/" element={<p>home page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('new password rules', () => {
  it('needs 8 to 72 characters and a matching confirmation', () => {
    expect(validateNewPassword('longenough', 'longenough')).toEqual({})
    expect(validateNewPassword('short', 'short').password).toBe('Use at least 8 characters.')
    expect(validateNewPassword('x'.repeat(73), 'x'.repeat(73)).password).toBe('Use 72 characters or fewer.')
    expect(validateNewPassword('longenough', 'different1').confirm).toBe('The two passwords do not match.')
  })
})

describe('login page', () => {
  it('links to the forgot password page', async () => {
    at('/login')
    await userEvent.click(screen.getByRole('link', { name: 'Forgot your password?' }))
    expect(await screen.findByRole('heading', { name: 'Forgot your password?' })).toBeInTheDocument()
  })
})

describe('forgot password', () => {
  it('rejects an invalid email without calling the server', async () => {
    const forgot = vi.spyOn(api, 'forgotPassword').mockResolvedValue(undefined)
    at('/forgot-password')
    await userEvent.type(screen.getByLabelText('Email'), 'nope')
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))
    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument()
    expect(forgot).not.toHaveBeenCalled()
  })

  it('sends the trimmed address and shows the same confirmation for any address', async () => {
    const forgot = vi.spyOn(api, 'forgotPassword').mockResolvedValue(undefined)
    at('/forgot-password')
    await userEvent.type(screen.getByLabelText('Email'), '  a@college.edu ')
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))
    expect(forgot).toHaveBeenCalledWith('a@college.edu')
    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Check your email' })).toHaveTextContent('If a@college.edu is registered, we sent a link to reset the password.')
  })

  it('shows a server error such as a rate limit', async () => {
    vi.spyOn(api, 'forgotPassword').mockRejectedValue(new ApiError(429, 'Too many requests. Try again in a few minutes.'))
    at('/forgot-password')
    await userEvent.type(screen.getByLabelText('Email'), 'a@college.edu')
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many requests')
  })

  it('lets the student go back and try another address', async () => {
    vi.spyOn(api, 'forgotPassword').mockResolvedValue(undefined)
    at('/forgot-password')
    await userEvent.type(screen.getByLabelText('Email'), 'a@college.edu')
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))
    await userEvent.click(await screen.findByRole('button', { name: 'try another address' }))
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
  })
})

describe('reset password', () => {
  it('explains a link that has no token', () => {
    at('/reset-password')
    expect(screen.getByRole('heading', { name: 'This link is incomplete' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Request a new link' })).toHaveAttribute('href', '/forgot-password')
  })

  it('checks the passwords before calling the server', async () => {
    const reset = vi.spyOn(api, 'resetPassword').mockResolvedValue(undefined)
    at('/reset-password?token=abc')
    await userEvent.type(screen.getByLabelText('New password'), 'short')
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'other')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('Use at least 8 characters.')).toBeInTheDocument()
    expect(screen.getByText('The two passwords do not match.')).toBeInTheDocument()
    expect(reset).not.toHaveBeenCalled()
  })

  it('sets the new password with the token from the link', async () => {
    const reset = vi.spyOn(api, 'resetPassword').mockResolvedValue(undefined)
    at('/reset-password?token=abc_123-x')
    await userEvent.type(screen.getByLabelText('New password'), 'brand-new-pass')
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'brand-new-pass')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(reset).toHaveBeenCalledWith('abc_123-x', 'brand-new-pass')
    expect(await screen.findByRole('heading', { name: 'Password changed' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login')
  })

  it('shows an expired link message and offers a new link', async () => {
    vi.spyOn(api, 'resetPassword').mockRejectedValue(new ApiError(400, 'This link is invalid or has expired. Request a new one.'))
    at('/reset-password?token=old')
    await userEvent.type(screen.getByLabelText('New password'), 'brand-new-pass')
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'brand-new-pass')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText(/invalid or has expired/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Request a new link' })).toBeInTheDocument()
  })
})

describe('verify email', () => {
  it('does nothing until the button is pressed, so a scanner cannot use the link up', () => {
    const verify = vi.spyOn(api, 'verifyEmail').mockResolvedValue(undefined)
    at('/verify-email?token=abc')
    expect(screen.getByRole('button', { name: 'Confirm email' })).toBeInTheDocument()
    expect(verify).not.toHaveBeenCalled()
  })

  it('confirms the address and points a logged-out visitor to log in', async () => {
    const verify = vi.spyOn(api, 'verifyEmail').mockResolvedValue(undefined)
    at('/verify-email?token=abc')
    await userEvent.click(screen.getByRole('button', { name: 'Confirm email' }))
    expect(verify).toHaveBeenCalledWith('abc')
    expect(await screen.findByRole('heading', { name: 'Email confirmed' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Log in' })).toBeInTheDocument()
  })

  it('refreshes the account for a logged-in student and links to the dashboard', async () => {
    await mockApi.register('Aarav Sharma', 'a@college.edu', 'password1')
    vi.spyOn(api, 'verifyEmail').mockResolvedValue(undefined)
    const restore = vi.spyOn(api, 'restore')
    at('/verify-email?token=abc')
    await vi.waitFor(() => expect(restore).toHaveBeenCalledTimes(1))
    await userEvent.click(screen.getByRole('button', { name: 'Confirm email' }))
    expect(await screen.findByRole('link', { name: 'Go to your dashboard' })).toBeInTheDocument()
    expect(restore).toHaveBeenCalledTimes(2)
  })

  it('shows why a link failed', async () => {
    vi.spyOn(api, 'verifyEmail').mockRejectedValue(new ApiError(400, 'This link is invalid or has expired. Request a new one.'))
    at('/verify-email?token=used')
    await userEvent.click(screen.getByRole('button', { name: 'Confirm email' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('invalid or has expired')
  })

  it('explains a link with no token', () => {
    at('/verify-email')
    expect(screen.getByRole('heading', { name: 'This link is incomplete' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm email' })).not.toBeInTheDocument()
  })
})

describe('unverified banner', () => {
  async function renderBanner() {
    await mockApi.register('Aarav Sharma', 'a@college.edu', 'password1')
    render(<AuthProvider><VerifyBanner /></AuthProvider>)
    await screen.findByRole('status')
  }

  it('names the address and resends the link', async () => {
    const resend = vi.spyOn(api, 'resendVerification').mockResolvedValue(undefined)
    await renderBanner()
    expect(screen.getByRole('status')).toHaveTextContent('a@college.edu')
    await userEvent.click(screen.getByRole('button', { name: 'Resend link' }))
    expect(resend).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Link sent. Check your inbox.')).toBeInTheDocument()
  })

  it('shows the throttle message from the server', async () => {
    vi.spyOn(api, 'resendVerification').mockRejectedValue(new ApiError(429, 'We just sent a link. Wait a minute before asking again.'))
    await renderBanner()
    await userEvent.click(screen.getByRole('button', { name: 'Resend link' }))
    expect(await screen.findByText(/Wait a minute/)).toBeInTheDocument()
  })

  it('"I confirmed it" re-reads the account', async () => {
    await renderBanner()
    const restore = vi.spyOn(api, 'restore')
    await userEvent.click(screen.getByRole('button', { name: 'I confirmed it' }))
    expect(restore).toHaveBeenCalledTimes(1)
  })
})
