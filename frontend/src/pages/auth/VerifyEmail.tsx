import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../../auth'

export default function VerifyEmail() {
  const [params] = useSearchParams()
  const [message, setMessage] = useState('Đang xác thực email…')
  const [error, setError] = useState(false)

  useEffect(() => {
    const token = params.get('token')
    if (!token) {
      setError(true)
      setMessage('Thiếu token xác thực')
      return
    }
    api(`/api/v1/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(() => setMessage('Xác thực email thành công!'))
      .catch((e) => {
        setError(true)
        setMessage(e instanceof Error ? e.message : 'Xác thực thất bại')
      })
  }, [params])

  return (
    <div className="mx-auto mt-10 w-full max-w-sm rounded-md border border-border bg-card p-6 text-center text-sm">
      <p className={error ? 'text-red-600' : ''}>{message}</p>
      {!error && (
        <Link to="/auth/login" className="mt-3 inline-block text-brand">
          Về đăng nhập
        </Link>
      )}
    </div>
  )
}
