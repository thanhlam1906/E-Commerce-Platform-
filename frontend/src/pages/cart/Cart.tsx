import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Minus, Plus } from '@phosphor-icons/react'
import { useCart, useRemoveCartItem, useUpdateCartItem } from '../../hooks/useCart'
import { useAuth } from '../../hooks/useAuth'
import { vnd } from '../../lib/format'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'

export default function Cart() {
  const { data: cart, isLoading } = useCart()
  const update = useUpdateCartItem()
  const remove = useRemoveCartItem()
  const { isAuthenticated } = useAuth()
  const [confirmSku, setConfirmSku] = useState<string | null>(null)

  if (isLoading) return <div className="py-20 text-center text-gray-500">Đang tải…</div>
  if (!cart || cart.items.length === 0)
    return (
      <EmptyState message="Giỏ hàng trống">
        <Link to="/products">
          <Button className="mt-3">Xem sản phẩm</Button>
        </Link>
      </EmptyState>
    )

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
      <div className="space-y-3">
        {cart.items.map((it) => (
          <div key={it.sku} className="flex flex-wrap items-center gap-4 rounded-md border border-border bg-card p-3">
            <div className="min-w-0 flex-1">
              <p className="line-clamp-1 font-medium">{it.productName}</p>
              <p className="text-xs text-gray-500">{it.variantName} · {vnd(it.unitPrice)}</p>
              {it.stockWarning && <p className="text-xs text-red-600">Vượt tồn kho — số lượng sẽ được điều chỉnh</p>}
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => update.mutate({ sku: it.sku, quantity: it.quantity - 1 })}
                disabled={it.quantity <= 1}
                className="flex h-8 w-8 items-center justify-center rounded-sm border disabled:opacity-40"
              >
                <Minus size={14} weight="bold" />
              </button>
              <span className="w-8 text-center">{it.quantity}</span>
              <button
                onClick={() => update.mutate({ sku: it.sku, quantity: it.quantity + 1 })}
                className="flex h-8 w-8 items-center justify-center rounded-sm border"
              >
                <Plus size={14} weight="bold" />
              </button>
            </div>
            <p className="w-24 text-right font-semibold">{vnd(it.subtotal)}</p>
            <button onClick={() => setConfirmSku(it.sku)} className="text-sm text-red-500">
              Xóa
            </button>
          </div>
        ))}
        <ConfirmDialog
          open={!!confirmSku}
          title="Xóa sản phẩm"
          message="Bạn có chắc muốn xóa sản phẩm này khỏi giỏ?"
          confirmLabel="Xóa"
          onClose={() => setConfirmSku(null)}
          onConfirm={() => confirmSku && remove.mutate(confirmSku)}
        />
      </div>
      <div className="h-fit rounded-md border border-border bg-card p-4">
        <p className="mb-2 text-sm text-gray-500">Tổng cộng ({cart.itemCount} sản phẩm)</p>
        <p className="mb-4 text-2xl font-bold text-brand">{vnd(cart.totalAmount)}</p>
        <Link to={isAuthenticated ? '/checkout' : '/auth/login'}>
          <Button className="w-full">Thanh toán</Button>
        </Link>
        {!isAuthenticated && <p className="mt-2 text-xs text-gray-400">Đăng nhập để thanh toán — giỏ khách sẽ được gộp vào tài khoản.</p>}
      </div>
    </div>
  )
}
