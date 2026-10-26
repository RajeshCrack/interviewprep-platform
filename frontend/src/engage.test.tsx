import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, type Badge, type NotificationList } from './api'
import { mockApi } from './api/mock'
import DashboardPage from './pages/DashboardPage'

const inbox = (over: Partial<NotificationList> = {}): NotificationList => ({
  unread: 2,
  items: [
    { id: 'n1', kind: 'REMINDER', title: 'Time to study, Aarav', body: 'You have not studied yet today. 120 minutes meets your goal.', createdAt: '2026-10-26T14:30:00Z', read: false },
    { id: 'n2', kind: 'BADGE', title: 'Badge earned: First goal met', body: 'Meet your daily goal once.', createdAt: '2026-10-25T14:30:00Z', read: false },
  ],
  ...over,
})
const badgeList = (): Badge[] => [
  { key: 'FIRST_GOAL', title: 'First goal met', description: 'Meet your daily goal once.', earned: true, earnedAt: '2026-10-25T14:30:00Z' },
  { key: 'STREAK_3', title: '3-day streak', description: 'Meet your goal 3 days in a row.', earned: false, earnedAt: null },
]

async function renderDashboard() {
  await mockApi.register('Aarav Sharma', 'a@college.edu', 'password1')
  render(<MemoryRouter><DashboardPage /></MemoryRouter>)
  await screen.findByText(/Hi Aarav/)
}

afterEach(() => vi.restoreAllMocks())

describe('notifications on the dashboard', () => {
  it('lists the latest notifications with an unread count', async () => {
    vi.spyOn(api, 'notifications').mockResolvedValue(inbox())
    await renderDashboard()
    const card = await screen.findByRole('region', { name: 'Notifications' })
    expect(within(card).getByText('2 new')).toBeInTheDocument()
    expect(within(card).getByText('Time to study, Aarav')).toBeInTheDocument()
    expect(within(card).getByText('Badge earned: First goal met')).toBeInTheDocument()
  })

  it('Mark all read clears the count and tells the server', async () => {
    vi.spyOn(api, 'notifications').mockResolvedValue(inbox())
    const mark = vi.spyOn(api, 'markNotificationsRead').mockResolvedValue(undefined)
    await renderDashboard()
    await userEvent.click(await screen.findByRole('button', { name: 'Mark all read' }))
    expect(mark).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('2 new')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark all read' })).not.toBeInTheDocument()
  })

  it('shows no notification card when there are none', async () => {
    vi.spyOn(api, 'notifications').mockResolvedValue({ unread: 0, items: [] })
    await renderDashboard()
    expect(screen.queryByRole('region', { name: 'Notifications' })).not.toBeInTheDocument()
  })

  it('a notification failure does not break the dashboard', async () => {
    vi.spyOn(api, 'notifications').mockRejectedValue(new Error('down'))
    vi.spyOn(api, 'badges').mockRejectedValue(new Error('down'))
    await renderDashboard()
    expect(screen.getByText('Daily goal')).toBeInTheDocument()
  })

  it('refreshes after logging study, because a goal or badge may have created a notification', async () => {
    const list = vi.spyOn(api, 'notifications').mockResolvedValue({ unread: 0, items: [] })
    await renderDashboard()
    expect(list).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Log 15 minutes of study' }))
    await screen.findByText('15m of 120m')
    expect(list).toHaveBeenCalledTimes(2)
  })
})

describe('badges on the dashboard', () => {
  it('shows earned badges and what the locked ones need', async () => {
    vi.spyOn(api, 'badges').mockResolvedValue(badgeList())
    await renderDashboard()
    const shelf = await screen.findByRole('region', { name: 'Badges' })
    expect(within(shelf).getByText('Badges · 1 of 2')).toBeInTheDocument()
    expect(within(shelf).getByText('Earned')).toBeInTheDocument()
    expect(within(shelf).getByText('Meet your goal 3 days in a row.')).toBeInTheDocument()
  })

  it('the in-browser stand-in shows no badges and no notifications, and the page still works', async () => {
    await renderDashboard()
    expect(screen.queryByRole('region', { name: 'Badges' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Notifications' })).not.toBeInTheDocument()
  })
})

describe('reminder wording', () => {
  it('describes each channel honestly', async () => {
    await renderDashboard()
    expect(screen.getByText(/Reminder at 20:00 in the app\./)).toBeInTheDocument()
  })
})
