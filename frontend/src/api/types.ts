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

export type QuestionKind = 'THEORY' | 'CODE'
export type QuestionDifficulty = 'EASY' | 'HARD'
export interface McqQuestion {
  position: number; kind: QuestionKind; prompt: string; codeSnippet: string | null; options: string[]; selected: number | null
}
export interface McqAttempt {
  id: string; topicId: string; startedAt: string; expiresAt: string; total: number; questions: McqQuestion[]
}
export interface McqGroup { kind: QuestionKind; difficulty: QuestionDifficulty; correct: number; total: number }
export interface McqReviewItem {
  position: number; kind: QuestionKind; difficulty: QuestionDifficulty; prompt: string; codeSnippet: string | null
  options: string[]; selected: number | null; correct: number; isCorrect: boolean; explanation: string
}
export interface McqResult {
  attemptId: string; topicId: string; score: number; total: number; passMark: number; passed: boolean
  submittedAt: string; xpAwarded: number; groups: McqGroup[]; review: McqReviewItem[]
}

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export interface CodingProblem {
  id: string; position: number; difficulty: Difficulty; title: string; statement: string; starterCode: string
  samples: { input: string; expected: string }[]; solved: boolean; lastCode: string | null
}
export interface CodingRound {
  topicId: string; passed: boolean; solvedCount: number; totalProblems: number; solvedEasy: number; totalEasy: number
  neededTotal: number; problems: CodingProblem[]
}
export type CodingTestStatus = 'PASSED' | 'WRONG_ANSWER' | 'RUNTIME_ERROR' | 'TIME_LIMIT' | 'NOT_RUN'
export interface CodingTestResult {
  position: number; visible: boolean; passed: boolean; status: CodingTestStatus
  input: string | null; expected: string | null; actual: string | null; error: string | null
}
export type SubmissionStatus = 'ACCEPTED' | 'WRONG_ANSWER' | 'COMPILE_ERROR' | 'RUNTIME_ERROR' | 'TIME_LIMIT'
export interface CodingSubmission {
  id: string; mode: 'RUN' | 'SUBMIT'; status: SubmissionStatus; passedTests: number; totalTests: number
  message: string | null; tests: CodingTestResult[]; solved: boolean; roundPassed: boolean; xpAwarded: number
}

export interface AppNotification { id: string; kind: 'REMINDER' | 'BADGE'; title: string; body: string; createdAt: string; read: boolean }
export interface NotificationList { unread: number; items: AppNotification[] }
export interface Badge { key: string; title: string; description: string; earned: boolean; earnedAt: string | null }

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
  /** Starts a test for the topic, or returns the one already running. */
  startMcq(topicId: string): Promise<McqAttempt>
  getMcq(attemptId: string): Promise<McqAttempt>
  saveMcqAnswer(attemptId: string, position: number, selected: number | null): Promise<void>
  /** answers has one entry per question (0 to 3, or null for blank). */
  submitMcq(attemptId: string, answers: (number | null)[]): Promise<McqResult>
  mcqResult(attemptId: string): Promise<McqResult>
  notifications(): Promise<NotificationList>
  markNotificationsRead(): Promise<void>
  badges(): Promise<Badge[]>
  codingRound(topicId: string): Promise<CodingRound>
  /** RUN checks the sample tests only. SUBMIT checks every test and can solve the problem. */
  submitCode(problemId: string, code: string, mode: 'RUN' | 'SUBMIT'): Promise<CodingSubmission>
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}
