// Token storage + API helper.
// Google OAuth flow: state cookie được set ở GATEWAY origin (:8080) nên nút
// Google Login phải là URL tuyệt đối tới gateway — không được qua proxy :3000.

import { getSessionId } from './session'
import type { User } from './types'

export const API_URL = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
export const GOOGLE_AUTH_URL = `${API_URL}/api/v1/auth/google`

const ACCESS_KEY = 'access_token'
const REFRESH_KEY = 'refresh_token'
const USER_KEY = 'user'

export function getTokens(): { access: string; refresh: string } | null {
  const access = localStorage.getItem(ACCESS_KEY)
  return access ? { access, refresh: localStorage.getItem(REFRESH_KEY) ?? '' } : null
}

export function saveTokens(access: string, refresh: string) {
  localStorage.setItem(ACCESS_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

export function saveUser(user: User) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getStoredUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

let refreshing: Promise<boolean> | null = null

/** POST /auth/refresh một lần duy nhất (single-flight), xoay refresh token. */
async function refreshTokens(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const tokens = getTokens()
      if (!tokens?.refresh) return false
      try {
        const res = await fetch('/api/v1/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: tokens.refresh }),
        })
        const body = await res.json()
        if (!res.ok || !body?.data?.accessToken) return false
        saveTokens(body.data.accessToken, body.data.refreshToken)
        if (body.data.user) saveUser(body.data.user)
        return true
      } catch {
        return false
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
}

/** fetch wrapper: Authorization + X-Session-Id, unwrap ApiDataResponse.data, refresh-on-401. */
async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers)
  const tokens = getTokens()
  if (tokens) headers.set('Authorization', `Bearer ${tokens.access}`)
  headers.set('X-Session-Id', getSessionId())
  const isForm = init.body instanceof FormData
  if (init.body && !isForm) headers.set('Content-Type', 'application/json')

  const res = await fetch(path, { ...init, headers })

  if (res.status === 401 && retry && !path.includes('/auth/refresh')) {
    const ok = await refreshTokens()
    if (ok) return request<T>(path, init, false)
    clearTokens()
    throw new ApiError('Phiên đã hết hạn, vui lòng đăng nhập lại', 401)
  }

  if (!res.ok) {
    let msg = `API ${path} → ${res.status}`
    try {
      const body = await res.json()
      if (body?.message) msg = body.message
    } catch {
      // body không phải JSON — giữ message mặc định
    }
    throw new ApiError(msg, res.status)
  }

  const body = await res.json()
  return (body?.data ?? body) as T
}

export function api<T>(path: string, init?: RequestInit): Promise<T> {
  return request<T>(path, init)
}
