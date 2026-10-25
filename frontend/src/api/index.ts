import { httpApi } from './http'
import { mockApi } from './mock'
import type { Api } from './types'

export const api: Api = import.meta.env.VITE_USE_MOCK === 'false' ? httpApi : mockApi
export * from './types'
