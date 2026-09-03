import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, clearTokens, getTokens, saveTokens, saveUser } from '../auth'
import type { User } from '../types'
import { mergeGuestCart } from './useCart'

interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  userLoading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, fullName: string) => Promise<void>
  logout: () => Promise<void>
  /** Lưu tokens + user, gộp giỏ guest. Nếu không truyền user thì /users/me tự load. */
  applyAuth: (access: string, refresh: string, user?: User) => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!getTokens())

  const { data: user, isError, isLoading } = useQuery<User>({
    queryKey: ['me'],
    queryFn: () => api<User>('/api/v1/users/me'),
    enabled: isAuthenticated,
    staleTime: 60_000,
  })

  // Refresh fail → api() đã clearTokens → coi như chưa đăng nhập
  useEffect(() => {
    if (isError && !getTokens()) setIsAuthenticated(false)
  }, [isError])

  async function applyAuth(access: string, refresh: string, u?: User) {
    saveTokens(access, refresh)
    if (u) {
      saveUser(u)
      qc.setQueryData(['me'], u)
    }
    setIsAuthenticated(true)
    try {
      await mergeGuestCart()
    } catch {
      // giỏ guest có thể rỗng — không fail flow
    }
    qc.invalidateQueries({ queryKey: ['cart'] })
  }

  async function login(email: string, password: string) {
    const res = await api<{ accessToken: string; refreshToken: string; user: User }>(
      '/api/v1/auth/login',
      { method: 'POST', body: JSON.stringify({ email, password }) },
    )
    await applyAuth(res.accessToken, res.refreshToken, res.user)
  }

  async function register(email: string, password: string, fullName: string) {
    const res = await api<{ accessToken: string; refreshToken: string; user: User }>(
      '/api/v1/auth/register',
      { method: 'POST', body: JSON.stringify({ email, password, fullName }) },
    )
    await applyAuth(res.accessToken, res.refreshToken, res.user)
  }

  async function logout() {
    const t = getTokens()
    if (t?.refresh) {
      try {
        await api('/api/v1/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken: t.refresh }),
        })
      } catch {
        // best effort — logout local vẫn chạy
      }
    }
    clearTokens()
    setIsAuthenticated(false)
    qc.removeQueries({ queryKey: ['me'] })
    qc.invalidateQueries({ queryKey: ['cart'] })
  }

  return (
    <AuthContext.Provider
      value={{
        user: user ?? null,
        isAuthenticated,
        userLoading: isLoading,
        login,
        register,
        logout,
        applyAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth phải dùng trong <AuthProvider>')
  return ctx
}
