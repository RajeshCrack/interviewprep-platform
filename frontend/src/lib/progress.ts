/** Pure display helpers. The backend stays the source of truth for goals, streaks and XP. */
export const XP_PER_LEVEL = 200

export function ringFraction(todayMinutes: number, goalMinutes: number): number {
  if (goalMinutes <= 0) return 0
  return Math.min(1, Math.max(0, todayMinutes / goalMinutes))
}

export function levelFromXp(xp: number): number {
  return Math.floor(Math.max(0, xp) / XP_PER_LEVEL) + 1
}

export function xpToNextLevel(xp: number): number {
  return XP_PER_LEVEL - (Math.max(0, xp) % XP_PER_LEVEL)
}

export function reminderMessage(todayMinutes: number, goalMinutes: number): string {
  const f = ringFraction(todayMinutes, goalMinutes)
  if (f >= 1) return "Goal met for today. Tomorrow's topic is waiting."
  const remaining = Math.max(0, goalMinutes - todayMinutes)
  if (f >= 0.5) return `You are past halfway. ${remaining} more minutes closes today's ring.`
  return 'A short start counts. Fifteen minutes now keeps your streak alive.'
}

export function daysToFinish(courseHours: number, hoursPerDay: number): number {
  if (hoursPerDay <= 0) return Infinity
  return Math.ceil(courseHours / hoursPerDay)
}
