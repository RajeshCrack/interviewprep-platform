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

export interface Api {
  register(name: string, email: string, password: string): Promise<Session>
  login(email: string, password: string): Promise<Session>
  logout(): Promise<void>
  restore(): Promise<Session | null>
  dashboard(): Promise<Dashboard>
  saveGoal(goal: Goal): Promise<Goal>
  logStudy(minutes: number): Promise<Dashboard>
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}
