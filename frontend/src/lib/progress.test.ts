import { describe, expect, it } from 'vitest'
import { daysToFinish, levelFromXp, reminderMessage, ringFraction, xpToNextLevel } from './progress'

describe('ringFraction', () => {
  it.each([[0, 120, 0], [60, 120, 0.5], [120, 120, 1], [200, 120, 1], [10, 0, 0], [-5, 120, 0]])(
    'today %i of goal %i is %f', (today, goal, expected) => expect(ringFraction(today, goal)).toBe(expected))
})

describe('levels', () => {
  it('starts at level 1 and moves up every 200 XP', () => {
    expect(levelFromXp(0)).toBe(1)
    expect(levelFromXp(199)).toBe(1)
    expect(levelFromXp(200)).toBe(2)
    expect(levelFromXp(490)).toBe(3)
  })
  it('reports XP to the next level', () => {
    expect(xpToNextLevel(0)).toBe(200)
    expect(xpToNextLevel(180)).toBe(20)
    expect(xpToNextLevel(200)).toBe(200)
  })
})

describe('reminderMessage', () => {
  it('encourages a start, a finish and a final push', () => {
    expect(reminderMessage(0, 120)).toMatch(/short start/)
    expect(reminderMessage(60, 120)).toMatch(/60 more minutes/)
    expect(reminderMessage(120, 120)).toMatch(/Goal met/)
  })
})

describe('daysToFinish', () => {
  it('rounds up and handles a zero goal', () => {
    expect(daysToFinish(8.5, 2)).toBe(5)
    expect(daysToFinish(8.5, 4)).toBe(3)
    expect(daysToFinish(8.5, 0)).toBe(Infinity)
  })
})
