import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../auth'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { useAuth } from '../hooks/useAuth'

export default function Profile() {
  const { user, userLoading, logout } = useAuth()
  const qc = useQueryClient()
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [saved, setSaved] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const body: Record<string, string> = { fullName }
    if (phone) body.phone = phone
    await api('/api/v1/users/me', { method: 'PUT', body: JSON.stringify(body) })
    await qc.invalidateQueries({ queryKey: ['me'] })
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  if (userLoading) return <div className="py-20 text-center text-gray-500">Đang tải…</div>
  if (!user) return null

  return (
    <div className="mx-auto max-w-md rounded-md border border-border bg-card p-6">
      <h1 className="mb-4 text-xl font-bold">Hồ sơ</h1>
      {user.avatarUrl && (
        <img src={user.avatarUrl} alt="avatar" className="mb-3 h-16 w-16 rounded-full object-cover" />
      )}
      <p className="mb-4 text-sm text-gray-500">
        {user.email} · {user.role}
      </p>
      <form onSubmit={submit} className="flex flex-col gap-3">
        <label className="text-sm">
          Họ tên
          <Input value={fullName} onChange={(e) => setFullName(e.target.value)} />
        </label>
        <label className="text-sm">
          Điện thoại
          <Input value={phone} onChange={(e) => setPhone(e.target.value)} />
        </label>
        {saved && <p className="text-sm text-green-600">Đã lưu</p>}
        <Button type="submit">Lưu thay đổi</Button>
      </form>
      <div className="mt-4 flex gap-4 text-sm">
        <Link to="/profile/addresses" className="text-brand">
          Địa chỉ giao hàng
        </Link>
        <Link to="/orders" className="text-brand">
          Đơn hàng
        </Link>
        <button className="text-red-600" onClick={() => logout()}>
          Đăng xuất
        </button>
      </div>
    </div>
  )
}
