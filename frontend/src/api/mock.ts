import { ApiError, type Api, type Dashboard, type Goal } from './types'

/** In-browser stand-in for the Spring Boot API, so the UI runs before the backend exists. */
type Rec = { id: string; name: string; email: string; password: string; goal: Goal; today: number; streak: number; xp: number }
const USERS = 'gr.mock.users'
const CURRENT = 'gr.mock.current'

const load = (): Rec[] => { try { return JSON.parse(localStorage.getItem(USERS) ?? '[]') } catch { return [] } }
const save = (u: Rec[]) => { try { localStorage.setItem(USERS, JSON.stringify(u)) } catch { /* ignore */ } }
const cur = (): Rec | undefined => {
  let id: string | null = null
  try { id = localStorage.getItem(CURRENT) } catch { /* ignore */ }
  return load().find((u) => u.id === id)
}
const view = (u: Rec): Dashboard => ({
  user: { id: u.id, name: u.name, email: u.email }, goal: u.goal, todayMinutes: u.today, streak: u.streak, xp: u.xp,
})
const need = (): Rec => { const u = cur(); if (!u) throw new ApiError(401, 'Please log in.'); return u }
const update = (u: Rec) => save(load().map((x) => (x.id === u.id ? u : x)))

export const mockApi: Api = {
  async register(name, email, password) {
    const users = load()
    if (users.some((u) => u.email.toLowerCase() === email.toLowerCase())) throw new ApiError(409, 'That email is already registered.')
    const u: Rec = {
      id: crypto.randomUUID(), name, email, password,
      goal: { hoursPerDay: 2, reminderTime: '20:00', channel: 'PUSH' }, today: 0, streak: 0, xp: 0,
    }
    save([...users, u]); localStorage.setItem(CURRENT, u.id)
    return { user: { id: u.id, name, email } }
  },
  async login(email, password) {
    const u = load().find((x) => x.email.toLowerCase() === email.toLowerCase() && x.password === password)
    if (!u) throw new ApiError(401, 'Email or password is incorrect.')
    localStorage.setItem(CURRENT, u.id)
    return { user: { id: u.id, name: u.name, email: u.email } }
  },
  async logout() { localStorage.removeItem(CURRENT) },
  async restore() { const u = cur(); return u ? { user: { id: u.id, name: u.name, email: u.email } } : null },
  async dashboard() { return view(need()) },
  async saveGoal(goal) { const u = need(); u.goal = goal; update(u); return goal },
  async logStudy(minutes) {
    const u = need(); const goalMin = u.goal.hoursPerDay * 60; const was = u.today >= goalMin
    u.today += minutes
    if (!was && u.today >= goalMin) { u.streak += 1; u.xp += 50 }
    u.xp += Math.floor(minutes / 5); update(u); return view(u)
  },
}
