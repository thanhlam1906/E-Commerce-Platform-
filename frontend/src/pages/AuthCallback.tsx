import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

/** Google OAuth callback — nhận access_token/refresh_token từ URL, lưu rồi về trang chủ. */
export default function AuthCallback() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { applyAuth } = useAuth()

  useEffect(() => {
    const error = params.get('error')
    if (error) {
      alert(`Đăng nhập Google thất bại: ${error}`)
      navigate('/auth/login')
      return
    }
    const access = params.get('access_token')
    const refresh = params.get('refresh_token')
    if (!access) {
      navigate('/auth/login')
      return
    }
    applyAuth(access, refresh ?? '').catch(() => navigate('/auth/login'))
    navigate('/')
  }, [params, navigate, applyAuth])

  return <div className="py-20 text-center text-gray-500">Đang xử lý đăng nhập Google…</div>
}
