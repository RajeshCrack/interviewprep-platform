import { COURSES, JAVA_TOPICS, TOPIC1 } from './catalog'
import { ApiError, type Api, type Dashboard, type Goal, type RoadmapTopic, type TopicStatus } from './types'

/** In-browser stand-in for the Spring Boot API, so the UI runs before the backend exists. */
type Rec = { id: string; name: string; email: string; password: string; goal: Goal; today: number; streak: number; xp: number; passed?: number[] }
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

const topicId = (position: number) => `java-${position}`
const positionOf = (id: string) => (id.startsWith('java-') ? Number(id.slice(5)) : NaN)

function roadmapFor(u: Rec): RoadmapTopic[] {
  const passed = new Set(u.passed ?? [])
  return JAVA_TOPICS.map((t, i) => {
    const position = i + 1
    const unlocked = position === 1 || passed.has(position - 1)
    const status: TopicStatus = !unlocked ? 'LOCKED' : passed.has(position) ? 'PASSED' : 'CURRENT'
    return {
      id: topicId(position), position, title: t.title, estMinutes: t.est, status,
      mcqPassed: status === 'PASSED', codingPassed: status === 'PASSED', hasContent: position === 1,
    }
  })
}

/** Test and demo helper: stands in for passing both rounds, which phases 3 and 4 will do for real. */
export function mockPassTopic(position: number): void {
  const u = cur()
  if (!u) return
  u.passed = Array.from(new Set([...(u.passed ?? []), position]))
  update(u)
}

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
  async courses() { need(); return COURSES },
  async roadmap(slug) {
    const u = need()
    const course = COURSES.find((c) => c.slug === slug)
    if (!course || !course.published) throw new ApiError(404, 'This course is not available yet.')
    const topics = roadmapFor(u)
    return { course, passedCount: topics.filter((t) => t.status === 'PASSED').length, topics }
  },
  async topic(id) {
    const u = need()
    const position = positionOf(id)
    const t = roadmapFor(u).find((x) => x.position === position)
    if (!t) throw new ApiError(404, 'Topic not found.')
    if (t.status === 'LOCKED') throw new ApiError(403, 'Pass the previous topic to unlock this one.')
    const first = position === 1
    return {
      id: t.id, courseSlug: 'java', position, totalTopics: JAVA_TOPICS.length, title: t.title, estMinutes: t.estMinutes,
      hasContent: first, what: first ? TOPIC1.what : null, why: first ? TOPIC1.why : null,
      exampleCode: first ? TOPIC1.exampleCode : null, hints: first ? TOPIC1.hints : [],
      videoQuery: first ? TOPIC1.videoQuery : `Java ${t.title.toLowerCase()} explained`,
      mcqPassed: t.mcqPassed, codingPassed: t.codingPassed,
      nextTopicId: position < JAVA_TOPICS.length ? topicId(position + 1) : null,
    }
  },
  async logStudy(minutes) {
    const u = need(); const goalMin = u.goal.hoursPerDay * 60; const was = u.today >= goalMin
    u.today += minutes
    if (!was && u.today >= goalMin) { u.streak += 1; u.xp += 50 }
    u.xp += Math.floor(minutes / 5); update(u); return view(u)
  },
  // The question bank and grading live on the server only, so the in-browser stand-in cannot run a test.
  async startMcq() { throw noServer() },
  async getMcq() { throw noServer() },
  async saveMcqAnswer() { throw noServer() },
  async submitMcq() { throw noServer() },
  async mcqResult() { throw noServer() },
  async codingRound() { throw noServer() },
  async submitCode() { throw noServer() },
}

function noServer() {
  return new ApiError(501, 'Tests and coding problems need the real server. Start the backend and run without VITE_USE_MOCK.')
}
