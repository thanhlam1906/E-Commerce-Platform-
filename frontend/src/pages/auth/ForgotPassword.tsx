import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../auth'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    try {
      await api('/api/v1/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) })
      setDone(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Có lỗi xảy ra')
    }
  }

  if (done)
    return (
      <div className="mx-auto mt-10 w-full max-w-sm rounded-md border border-border bg-card p-6 text-center text-sm">
        <p>Nếu email tồn tại, chúng tôi đã gửi link đặt lại mật khẩu.</p>
        <Link to="/auth/login" className="mt-3 inline-block text-brand">
          Về đăng nhập
        </Link>
      </div>
    )

  return (
    <div className="mx-auto mt-10 w-full max-w-sm rounded-md border border-border bg-card p-6">
      <h1 className="mb-4 text-xl font-bold">Quên mật khẩu</h1>
      <form onSubmit={submit} className="flex flex-col gap-3">
        <Input
          type="email"
          placeholder="Email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <Button type="submit">Gửi link đặt lại</Button>
      </form>
    </div>
  )
}
