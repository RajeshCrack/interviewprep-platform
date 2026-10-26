import { httpApi } from './http'
import { mockApi } from './mock'
import type { Api } from './types'

export const api: Api = import.meta.env.VITE_USE_MOCK === 'true' ? mockApi : httpApi
export * from './types'
