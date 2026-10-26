export type Channel = 'PUSH' | 'EMAIL'

export interface User { id: string; name: string; email: string }
export interface Goal { hoursPerDay: number; reminderTime: string; channel: Channel }
export interface Dashboard {
  user: User
  goal: Goal
  todayMinutes: number
  streak: number
  xp: number
}
export interface Session { user: User }

export type TopicStatus = 'PASSED' | 'CURRENT' | 'LOCKED'
export interface CourseSummary { slug: string; title: string; description: string; icon: string; published: boolean; topicCount: number }
export interface RoadmapTopic {
  id: string; position: number; title: string; estMinutes: number; status: TopicStatus
  mcqPassed: boolean; codingPassed: boolean; hasContent: boolean
}
export interface Roadmap { course: CourseSummary; passedCount: number; topics: RoadmapTopic[] }
export interface TopicDetail {
  id: string; courseSlug: string; position: number; totalTopics: number; title: string; estMinutes: number
  hasContent: boolean; what: string | null; why: string | null; exampleCode: string | null; hints: string[]
  videoQuery: string | null; mcqPassed: boolean; codingPassed: boolean; nextTopicId: string | null
}

export interface Api {
  register(name: string, email: string, password: string): Promise<Session>
  login(email: string, password: string): Promise<Session>
  logout(): Promise<void>
  restore(): Promise<Session | null>
  dashboard(): Promise<Dashboard>
  saveGoal(goal: Goal): Promise<Goal>
  logStudy(minutes: number): Promise<Dashboard>
  courses(): Promise<CourseSummary[]>
  roadmap(slug: string): Promise<Roadmap>
  topic(id: string): Promise<TopicDetail>
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}
