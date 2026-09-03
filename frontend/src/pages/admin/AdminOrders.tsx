import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAdminOrders, useUpdateOrderStatus } from '../../hooks/useAdmin'
import { OrderStatusBadge } from '../../components/order/OrderStatusBadge'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { Button } from '../../components/ui/Button'
import { useToast } from '../../components/ui/Toast'
import { vnd, formatDateTime, ORDER_STATUS_LABEL } from '../../lib/format'
import type { Order, OrderStatus } from '../../types'

const inputCls = 'w-full rounded-sm border border-border px-3 py-2 text-sm outline-none placeholder:text-placeholder focus:border-brand focus:ring-2 focus:ring-brand/30'
const STATUSES = Object.keys(ORDER_STATUS_LABEL) as OrderStatus[]

// Mirror backend OrderService.adminUpdateStatus: chỉ offer transition hợp lệ theo trạng thái hiện tại.
function legalStatuses(o: Order): OrderStatus[] {
  const cod = o.paymentMethod === 'COD'
  switch (o.status) {
    case 'PENDING': return cod ? ['CONFIRMED', 'CANCELLED'] : ['CANCELLED']
    case 'CONFIRMED': return ['SHIPPING', 'CANCELLED']
    case 'SHIPPING': return ['DELIVERED']
    default: return [] // DELIVERED / CANCELLED / EXPIRED — terminal
  }
}

export default function AdminOrders() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? undefined
  const page = Number(params.get('page') ?? 0)
  const { data, isLoading } = useAdminOrders(status, page)
  const update = useUpdateOrderStatus()
  const { toast } = useToast()
  // per-row: { orderId, newStatus, reason }
  const [drafts, setDrafts] = useState<Record<string, { status: OrderStatus; reason: string }>>({})

  const orders = data?.content ?? []

  function setStatusFilter(value?: string) {
    const next = new URLSearchParams()
    if (value) next.set('status', value)
    setParams(next)
  }

  function save(o: Order) {
    const d = drafts[o.id]
    if (!d || d.status === o.status) return
    update.mutate(
      { id: o.id, status: d.status, reason: d.reason.trim() || undefined },
      { onSuccess: () => toast(`Đã cập nhật trạng thái → ${d.status}`) }
    )
  }

  const tabs = [{ value: undefined, label: 'Tất cả' }, ...STATUSES.map((s) => ({ value: s, label: s }))]

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold">Quản lý đơn hàng</h1>
      <div className="mb-4 flex flex-wrap gap-2">
        {tabs.map((t) => (
          <button
            key={t.label}
            onClick={() => setStatusFilter(t.value)}
            className={`rounded-full px-3 py-1 text-sm ${status === t.value ? 'bg-brand text-white' : 'border border-gray-300 bg-white'}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="py-10 text-center text-gray-500">Đang tải…</div>
      ) : orders.length === 0 ? (
        <EmptyState message="Không có đơn hàng" />
      ) : (
        <div className="space-y-3">
          {orders.map((o) => {
            const draft = drafts[o.id]
            const targets = legalStatuses(o)
            return (
              <div key={o.id} className="rounded-md border border-border bg-card p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <Link to={`/orders/${o.id}`} className="font-medium hover:text-brand">
                      #{o.orderNumber}
                    </Link>
                    <p className="text-sm text-gray-500">
                      {formatDateTime(o.createdAt)} · {o.paymentMethod} · {o.items.reduce((n, it) => n + it.quantity, 0)} sp
                    </p>
                  </div>
                  <div className="text-right">
                    <OrderStatusBadge status={o.status} />
                    <p className="mt-1 font-bold text-brand">{vnd(o.totalAmount)}</p>
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap items-end gap-2">
                  <label className="text-xs text-gray-500">
                    Trạng thái mới
                    <select
                      className={`${inputCls} mt-1`}
                      value={draft?.status ?? o.status}
                      disabled={targets.length === 0}
                      onChange={(e) => setDrafts((m) => ({ ...m, [o.id]: { status: e.target.value as OrderStatus, reason: draft?.reason ?? '' } }))}
                    >
                      <option value={o.status} disabled>
                        {o.status}
                      </option>
                      {targets.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="min-w-40 flex-1 text-xs text-gray-500">
                    Lý do (nếu hủy)
                    <input
                      className={`${inputCls} mt-1`}
                      placeholder="Ghi chú cho lịch sử"
                      value={draft?.reason ?? ''}
                      onChange={(e) => setDrafts((m) => ({ ...m, [o.id]: { status: draft?.status ?? o.status, reason: e.target.value } }))}
                    />
                  </label>
                  <Button
                    onClick={() => save(o)}
                    disabled={!draft || draft.status === o.status}
                    className="text-sm"
                  >
                    Lưu
                  </Button>
                </div>
              </div>
            )
          })}
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
