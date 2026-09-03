import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { GOOGLE_AUTH_URL } from '../../auth'
import { useAuth } from '../../hooks/useAuth'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

export function AuthForm({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      if (mode === 'login') await login(email, password)
      else await register(email, password, fullName || email.split('@')[0])
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Có lỗi xảy ra')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="mx-auto mt-10 w-full max-w-sm rounded-md border border-border bg-card p-6 shadow-sm">
      <h1 className="mb-4 text-xl font-bold">{mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}</h1>
      <a
        href={GOOGLE_AUTH_URL}
        className="flex h-10 items-center justify-center gap-2 rounded-sm border border-border text-sm font-medium text-gray-700 transition hover:bg-gray-50"
      >
        <svg viewBox="0 0 24 24" className="h-4 w-4">
          <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.5-.2-2.2H12v4.2h6.5c-.3 1.5-1.1 2.7-2.3 3.6v3h3.7c2.2-2 3.6-5 3.6-8.6Z" />
          <path fill="#34A853" d="M12 24c3.2 0 6-1.1 8-2.9l-3.7-3c-1 .7-2.4 1.1-4.3 1.1-3.3 0-6.1-2.2-7.1-5.3H1.1v3.1A12 12 0 0 0 12 24Z" />
          <path fill="#FBBC05" d="M4.9 13.9a7.2 7.2 0 0 1 0-4.6V6.2H1.1a12 12 0 0 0 0 10.8l3.8-3.1Z" />
          <path fill="#EA4335" d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.3-3.3A12 12 0 0 0 1.1 6.2l3.8 3.1c1-3.1 3.8-5.3 7.1-5.3Z" />
        </svg>
        Đăng nhập bằng Google
      </a>
      <div className="my-4 flex items-center gap-3 text-xs text-gray-500">
        <span className="h-px flex-1 bg-gray-200" />
        hoặc
        <span className="h-px flex-1 bg-gray-200" />
      </div>
      <form onSubmit={submit} className="flex flex-col gap-3">
        {mode === 'register' && (
          <Input placeholder="Họ tên" autoComplete="name" value={fullName} onChange={(e) => setFullName(e.target.value)} />
        )}
        <Input
          type="email"
          placeholder="Email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Input
          type="password"
          placeholder="Mật khẩu"
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <Button type="submit" disabled={loading}>
          {loading ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}
        </Button>
      </form>
      {mode === 'login' && (
        <div className="mt-3 text-sm">
          <Link to="/auth/forgot-password" className="text-brand">
            Quên mật khẩu?
          </Link>
        </div>
      )}
      <p className="mt-4 text-center text-sm text-gray-500">
        {mode === 'login' ? (
          <>
            Chưa có tài khoản?{' '}
            <Link to="/auth/register" className="text-brand">
              Đăng ký
            </Link>
          </>
        ) : (
          <>
            Đã có tài khoản?{' '}
            <Link to="/auth/login" className="text-brand">
              Đăng nhập
            </Link>
          </>
        )}
      </p>
    </div>
  )
}
