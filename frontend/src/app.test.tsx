import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from './auth'
import { mockApi } from './api/mock'
import AuthPage from './pages/AuthPage'
import DashboardPage from './pages/DashboardPage'

function renderAuth(mode: 'login' | 'register') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/auth']}>
        <Routes>
          <Route path="/auth" element={<AuthPage mode={mode} />} />
          <Route path="/" element={<p>home page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('registration', () => {
  it('shows field errors and does not submit invalid input', async () => {
    renderAuth('register')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Enter your full name.')).toBeInTheDocument()
    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
  })

  it('registers a student and moves to the home page', async () => {
    renderAuth('register')
    await userEvent.type(screen.getByLabelText('Full name'), 'Aarav Sharma')
    await userEvent.type(screen.getByLabelText('Email'), 'aarav@college.edu')
    await userEvent.type(screen.getByLabelText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('home page')).toBeInTheDocument()
  })

  it('rejects a second registration with the same email', async () => {
    await mockApi.register('A One', 'dup@college.edu', 'password1')
    renderAuth('register')
    await userEvent.type(screen.getByLabelText('Full name'), 'A Two')
    await userEvent.type(screen.getByLabelText('Email'), 'dup@college.edu')
    await userEvent.type(screen.getByLabelText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('That email is already registered.')).toBeInTheDocument()
  })
})

describe('login', () => {
  it('shows an error for a wrong password', async () => {
    await mockApi.register('A One', 'one@college.edu', 'password1')
    await mockApi.logout()
    renderAuth('login')
    await userEvent.type(screen.getByLabelText('Email'), 'one@college.edu')
    await userEvent.type(screen.getByLabelText('Password'), 'wrongpass')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByText('Email or password is incorrect.')).toBeInTheDocument()
  })
})

describe('dashboard and goals', () => {
  it('logs study time and closes the ring, extending the streak', async () => {
    await mockApi.register('Aarav Sharma', 'aarav@college.edu', 'password1')
    await mockApi.saveGoal({ hoursPerDay: 0.5, reminderTime: '20:00', channel: 'PUSH' })
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)
    expect(await screen.findByText('0m of 30m')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Log 15 minutes/ }))
    expect(await screen.findByText('15m of 30m')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Log 15 minutes/ }))
    await waitFor(() => expect(screen.getByText('1 day')).toBeInTheDocument())
    expect(screen.getByRole('status')).toHaveTextContent('Goal met')
  })

  it('keeps one student\'s goal and progress away from another', async () => {
    await mockApi.register('First One', 'first@college.edu', 'password1')
    await mockApi.saveGoal({ hoursPerDay: 4, reminderTime: '07:00', channel: 'EMAIL' })
    await mockApi.logStudy(15)
    await mockApi.logout()
    await mockApi.register('Second One', 'second@college.edu', 'password1')
    const d = await mockApi.dashboard()
    expect(d.goal.hoursPerDay).toBe(2)
    expect(d.todayMinutes).toBe(0)
    expect(d.user.email).toBe('second@college.edu')
  })
})
