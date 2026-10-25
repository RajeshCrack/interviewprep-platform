export type RegisterInput = { name: string; email: string; password: string }
export type FieldErrors = Partial<Record<keyof RegisterInput, string>>

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

export function validateLogin(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {}
  if (!EMAIL.test(email.trim())) errors.email = 'Enter a valid email address.'
  if (!password) errors.password = 'Enter your password.'
  return errors
}

export function validateRegister(input: RegisterInput): FieldErrors {
  const errors = validateLogin(input.email, input.password)
  if (input.name.trim().length < 2) errors.name = 'Enter your full name.'
  if (input.password && input.password.length < 8) errors.password = 'Use at least 8 characters.'
  return errors
}
