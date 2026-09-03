import { Link, useSearchParams } from 'react-router-dom'
import { useOrders } from '../../hooks/useOrders'
import { Pagination } from '../../components/ui/Pagination'
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge'
import { EmptyState } from '../../components/ui/EmptyState'
import { vnd, formatDateTime, ORDER_STATUS_LABEL } from '../../lib/format'

export default function Orders() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? undefined
  const page = Number(params.get('page') ?? 0)
  const { data, isLoading } = useOrders(status, page)

  const tabs = [{ value: undefined, label: 'Tất cả' }, ...Object.entries(ORDER_STATUS_LABEL).map(([value, label]) => ({ value, label }))]

  function setStatus(value?: string) {
    const next = new URLSearchParams()
    if (value) next.set('status', value)
    setParams(next)
  }

  if (isLoading) return <div className="py-20 text-center text-gray-500">Đang tải…</div>
  const orders = data?.content ?? []

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="mb-4 text-xl font-bold">Đơn hàng của tôi</h1>
      <div className="mb-4 flex flex-wrap gap-2">
        {tabs.map((t) => (
          <button
            key={t.label}
            onClick={() => setStatus(t.value)}
            className={`rounded-full px-3 py-1 text-sm ${status === t.value ? 'bg-brand text-white' : 'border border-gray-300 bg-white hover:border-brand'}`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {orders.length === 0 ? (
        <EmptyState message="Không có đơn hàng nào" />
      ) : (
        <div className="space-y-3">
          {orders.map((o) => (
            <Link key={o.id} to={`/orders/${o.id}`} className="block rounded-md border border-border bg-card p-4 hover:border-brand hover:shadow-md">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm text-gray-500">
                  #{o.orderNumber} · {formatDateTime(o.createdAt)}
                </span>
                <OrderStatusBadge status={o.status} />
              </div>
              <div className="mt-2 flex items-center justify-between">
                <span className="text-sm text-gray-600">
                  {o.items.reduce((n, it) => n + it.quantity, 0)} sản phẩm · {o.paymentMethod}
                </span>
                <span className="font-bold text-brand">{vnd(o.totalAmount)}</span>
              </div>
            </Link>
          ))}
        </div>
      )}
      <div className="mt-4">
        <Pagination
          page={page}
          totalPages={data?.totalPages ?? 0}
          onPage={(p) => {
            const next = new URLSearchParams()
            if (status) next.set('status', status)
            next.set('page', String(p))
            setParams(next)
          }}
        />
      </div>
    </div>
  )
}
