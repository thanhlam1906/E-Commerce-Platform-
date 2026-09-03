import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import type { Role } from './types'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, userLoading } = useAuth()
  if (!isAuthenticated) return <Navigate to="/auth/login" replace />
  if (userLoading) return <div className="py-20 text-center text-gray-500">Đang tải…</div>
  return <>{children}</>
}

export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user, isAuthenticated, userLoading } = useAuth()
  if (!isAuthenticated) return <Navigate to="/auth/login" replace />
  if (userLoading) return <div className="py-20 text-center text-gray-500">Đang tải…</div>
  if (!user || !roles.includes(user.role)) return <Navigate to="/" replace />
  return <>{children}</>
}
