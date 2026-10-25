import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, type User } from './api'

interface AuthState {
  user: User | null
  ready: boolean
  login(email: string, password: string): Promise<void>
  register(name: string, email: string, password: string): Promise<void>
  logout(): Promise<void>
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let alive = true
    api.restore().then((s) => { if (alive) { setUser(s?.user ?? null); setReady(true) } })
    return () => { alive = false }
  }, [])

  const value: AuthState = {
    user, ready,
    async login(email, password) { setUser((await api.login(email, password)).user) },
    async register(name, email, password) { setUser((await api.register(name, email, password)).user) },
    async logout() { await api.logout(); setUser(null) },
  }
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth(): AuthState {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth must be used inside AuthProvider')
  return v
}
