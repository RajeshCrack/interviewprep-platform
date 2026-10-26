import { ApiError, type Api, type CourseSummary, type Dashboard, type Goal, type McqAttempt, type McqResult, type Roadmap, type Session, type TopicDetail } from './types'

const BASE = import.meta.env.VITE_API_URL ?? '/api'
const ACCESS = 'gr.access'
const REFRESH = 'gr.refresh'

const read = (k: string) => { try { return localStorage.getItem(k) } catch { return null } }
const write = (k: string, v: string | null) => {
  try {
    if (v === null) localStorage.removeItem(k)
    else localStorage.setItem(k, v)
  } catch { /* storage unavailable */ }
}

async function raw(path: string, init: RequestInit = {}, token?: string | null): Promise<Response> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(`${BASE}${path}`, { ...init, headers })
}

async function body<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = 'Something went wrong. Try again.'
    try { message = (await res.json()).message ?? message } catch { /* no body */ }
    throw new ApiError(res.status, message)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

async function refresh(): Promise<boolean> {
  const token = read(REFRESH)
  if (!token) return false
  const res = await raw('/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: token }) })
  if (!res.ok) { write(ACCESS, null); write(REFRESH, null); return false }
  const t = await res.json()
  write(ACCESS, t.accessToken); write(REFRESH, t.refreshToken)
  return true
}

async function authed<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res = await raw(path, init, read(ACCESS))
  if (res.status === 401 && (await refresh())) res = await raw(path, init, read(ACCESS))
  return body<T>(res)
}

function store(t: { accessToken: string; refreshToken: string; user: Session['user'] }): Session {
  write(ACCESS, t.accessToken); write(REFRESH, t.refreshToken)
  return { user: t.user }
}

export const httpApi: Api = {
  async register(name, email, password) {
    return store(await body(await raw('/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) })))
  },
  async login(email, password) {
    return store(await body(await raw('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })))
  },
  async logout() {
    const token = read(REFRESH)
    if (token) await raw('/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: token }) }).catch(() => undefined)
    write(ACCESS, null); write(REFRESH, null)
  },
  async restore() {
    if (!read(ACCESS) && !read(REFRESH)) return null
    try { return { user: (await authed<{ user: Session['user'] }>('/me')).user } } catch { return null }
  },
  dashboard: () => authed<Dashboard>('/dashboard'),
  saveGoal: (goal: Goal) => authed<Goal>('/me/goal', { method: 'PUT', body: JSON.stringify(goal) }),
  courses: () => authed<CourseSummary[]>('/courses'),
  roadmap: (slug: string) => authed<Roadmap>(`/courses/${encodeURIComponent(slug)}/roadmap`),
  topic: (id: string) => authed<TopicDetail>(`/topics/${encodeURIComponent(id)}`),
  startMcq: (topicId: string) => authed<McqAttempt>(`/topics/${encodeURIComponent(topicId)}/mcq/attempts`, { method: 'POST' }),
  getMcq: (id: string) => authed<McqAttempt>(`/mcq/attempts/${encodeURIComponent(id)}`),
  saveMcqAnswer: (id: string, position: number, selected: number | null) =>
    authed<void>(`/mcq/attempts/${encodeURIComponent(id)}/answers/${position}`, { method: 'PUT', body: JSON.stringify({ selected }) }),
  submitMcq: (id: string, answers: (number | null)[]) =>
    authed<McqResult>(`/mcq/attempts/${encodeURIComponent(id)}/submit`, { method: 'POST', body: JSON.stringify({ answers }) }),
  mcqResult: (id: string) => authed<McqResult>(`/mcq/attempts/${encodeURIComponent(id)}/result`),
  logStudy: (minutes: number) => authed<Dashboard>('/me/study-log', { method: 'POST', body: JSON.stringify({ minutes }) }),
}
