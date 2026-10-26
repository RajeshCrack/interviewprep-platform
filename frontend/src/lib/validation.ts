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

export function validateEmail(email: string): string | undefined {
  return EMAIL.test(email.trim()) ? undefined : 'Enter a valid email address.'
}

export type NewPasswordErrors = { password?: string; confirm?: string }

export function validateNewPassword(password: string, confirm: string): NewPasswordErrors {
  const errors: NewPasswordErrors = {}
  if (password.length < 8) errors.password = 'Use at least 8 characters.'
  else if (password.length > 72) errors.password = 'Use 72 characters or fewer.'
  if (confirm !== password) errors.confirm = 'The two passwords do not match.'
  return errors
}
