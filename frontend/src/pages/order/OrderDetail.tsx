import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useCancelOrder, useOrder, useOrderHistory } from '../../hooks/useOrders'
import { useToast } from '../../components/ui/Toast'
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge'
import { OrderTimeline } from '../../components/order/OrderTimeline'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { vnd, formatDateTime } from '../../lib/format'

export default function OrderDetail() {
  const { id } = useParams()
  const { data: order, isLoading } = useOrder(id)
  const { data: history } = useOrderHistory(id)
  const cancel = useCancelOrder()
  const { toast } = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)

  if (isLoading || !order) return <div className="py-20 text-center text-gray-500">Đang tải…</div>

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-xl font-bold">Đơn hàng #{order.orderNumber}</h1>
        <OrderStatusBadge status={order.status} />
      </div>
      <p className="mb-4 text-sm text-gray-500">
        {formatDateTime(order.createdAt)} · {order.paymentMethod}
      </p>

      <div className="mb-4 rounded-md border border-border bg-card p-4">
        <h2 className="mb-2 font-semibold">Sản phẩm</h2>
        {order.items.map((it) => (
          <div key={it.sku} className="flex justify-between gap-2 py-1 text-sm">
            <span className="line-clamp-1">
              {it.productName} ×{it.quantity}
            </span>
            <span className="shrink-0">{vnd(it.subtotal)}</span>
          </div>
        ))}
        <p className="mt-2 border-t pt-2 text-lg font-bold text-brand">{vnd(order.totalAmount)}</p>
      </div>

      <div className="mb-4 rounded-md border border-border bg-card p-4 text-sm">
        <h2 className="mb-2 font-semibold">Địa chỉ giao</h2>
        <p className="whitespace-pre-wrap text-gray-600">{order.shippingAddressSnapshot}</p>
      </div>

      <div className="mb-4 rounded-md border border-border bg-card p-4">
        <h2 className="mb-2 font-semibold">Lịch sử đơn hàng</h2>
        {history && history.length > 0 ? <OrderTimeline history={history} /> : <p className="text-sm text-gray-400">Chưa có</p>}
      </div>

      {order.status === 'PENDING' && (
        <button onClick={() => setConfirmCancel(true)} className="text-red-600">
          Hủy đơn hàng
        </button>
      )}
      <Link to="/orders" className="ml-4 text-brand">
        ← Đơn hàng của tôi
      </Link>

      <ConfirmDialog
        open={confirmCancel}
        title="Hủy đơn hàng"
        message="Bạn có chắc muốn hủy đơn hàng này?"
        confirmLabel="Hủy đơn"
        onClose={() => setConfirmCancel(false)}
        onConfirm={() => cancel.mutate(order.id, { onSuccess: () => toast('Đã hủy đơn hàng') })}
      />
    </div>
  )
}
