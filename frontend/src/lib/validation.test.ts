import { describe, expect, it } from 'vitest'
import { validateLogin, validateRegister } from './validation'

describe('validateRegister', () => {
  it('accepts valid input', () => {
    expect(validateRegister({ name: 'Aarav Sharma', email: 'a@college.edu', password: 'password1' })).toEqual({})
  })
  it('flags every bad field', () => {
    const e = validateRegister({ name: 'A', email: 'nope', password: 'short' })
    expect(e.name).toBeDefined(); expect(e.email).toBeDefined(); expect(e.password).toMatch(/8 characters/)
  })
  it('allows exactly 8 characters', () => {
    expect(validateRegister({ name: 'Aa', email: 'a@b.co', password: '12345678' })).toEqual({})
  })
})

describe('validateLogin', () => {
  it('requires an email and a password', () => {
    expect(Object.keys(validateLogin('', ''))).toEqual(['email', 'password'])
    expect(validateLogin('a@b.co', 'x')).toEqual({})
  })
})
