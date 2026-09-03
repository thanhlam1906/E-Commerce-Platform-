import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../../auth'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password !== confirm) {
      setError('Mật khẩu không khớp')
      return
    }
    setError('')
    try {
      await api('/api/v1/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ token, newPassword: password }),
      })
      setDone(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Có lỗi xảy ra')
    }
  }

  if (done)
    return (
      <div className="mx-auto mt-10 w-full max-w-sm rounded-md border border-border bg-card p-6 text-center text-sm">
        <p>Đặt lại mật khẩu thành công.</p>
        <Link to="/auth/login" className="mt-3 inline-block text-brand">
          Về đăng nhập
        </Link>
      </div>
    )

  return (
    <div className="mx-auto mt-10 w-full max-w-sm rounded-md border border-border bg-card p-6">
      <h1 className="mb-4 text-xl font-bold">Đặt lại mật khẩu</h1>
      <form onSubmit={submit} className="flex flex-col gap-3">
        <Input
          type="password"
          placeholder="Mật khẩu mới"
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Input
          type="password"
          placeholder="Nhập lại mật khẩu"
          required
          minLength={8}
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
        />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <Button type="submit">Đặt lại mật khẩu</Button>
      </form>
    </div>
  )
}
