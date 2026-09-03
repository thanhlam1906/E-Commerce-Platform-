import { Link, useLocation, useParams } from 'react-router-dom'
import { useOrder } from '../../hooks/useOrders'
import { Button } from '../../components/ui/Button'

export default function PaymentPending() {
  const { orderId } = useParams()
  const location = useLocation()
  const state = (location.state ?? {}) as { qrImage?: string; paymentUrl?: string }
  const { data: order, isLoading } = useOrder(orderId, true)

  if (isLoading || !order) return <div className="py-20 text-center text-gray-500">Đang kiểm tra thanh toán…</div>

  const pending = order.status === 'PENDING'

  return (
    <div className="mx-auto max-w-md rounded-md border border-border bg-card p-6 text-center">
      <h1 className="mb-3 text-xl font-bold">{pending ? 'Đang chờ thanh toán' : 'Đã nhận thanh toán'}</h1>

      {state.qrImage && (
        <div className="mb-4 flex justify-center rounded-sm border border-border bg-card p-3">
          <img src={state.qrImage} alt="VNPay QR" className="h-52 w-52" />
        </div>
      )}
      {state.paymentUrl && (
        <a href={state.paymentUrl} target="_blank" rel="noreferrer">
          <Button className="mb-3 w-full">Mở trang thanh toán VNPay</Button>
        </a>
      )}

      {pending ? (
        <p className="text-sm text-gray-500">Trang tự động cập nhật khi VNPay xác nhận. Nếu vừa thanh toán xong, chờ vài giây…</p>
      ) : (
        <Link to={`/orders/${order.id}`}>
          <Button className="w-full">Xem chi tiết đơn hàng</Button>
        </Link>
      )}
    </div>
  )
}
