import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <div className="py-20 text-center">
      <p className="mb-4 text-gray-500">Trang không tồn tại</p>
      <Link to="/" className="text-brand">
        Về trang chủ
      </Link>
    </div>
  )
}
