import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateOrder } from '../../hooks/useOrders'
import { useCart } from '../../hooks/useCart'
import { useAuth } from '../../hooks/useAuth'
import { useAddresses } from '../../hooks/useAddresses'
import { vnd } from '../../lib/format'
import { Button } from '../../components/ui/Button'

export default function Checkout() {
  const { user } = useAuth()
  const { data: cart } = useCart()
  const { data: addresses } = useAddresses()
  const create = useCreateOrder()
  const navigate = useNavigate()
  const [address, setAddress] = useState('')
  const [paymentMethod, setPaymentMethod] = useState<'COD' | 'VNPAY_QR'>('COD')
  const [error, setError] = useState('')
  // idempotency key: sinh 1 lần/mount → double-click submit trả về cùng đơn
  const [idem] = useState(() => crypto.randomUUID())

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!address.trim()) {
      setError('Vui lòng nhập địa chỉ giao hàng')
      return
    }
    setError('')
    const res = await create.mutateAsync({
      body: { shippingAddress: address.trim(), paymentMethod, email: user?.email ?? '' },
      idempotencyKey: idem,
    })
    if (paymentMethod === 'VNPAY_QR') {
      navigate(`/checkout/${res.orderId}/pay`, { state: { qrImage: res.qrImage, paymentUrl: res.paymentUrl } })
    } else {
      navigate(`/orders/${res.orderId}`)
    }
  }

  if (!cart || cart.items.length === 0) return <div className="py-20 text-center text-gray-500">Giỏ hàng trống</div>

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-4 text-xl font-bold">Thanh toán</h1>
      <form onSubmit={submit} className="grid gap-4 lg:grid-cols-[1fr_280px]">
        <div className="space-y-4">
          <div className="rounded-md border border-border bg-card p-4">
            <h2 className="mb-2 font-semibold">Địa chỉ giao hàng</h2>
            {addresses && addresses.length > 0 && (
              <div className="mb-3 flex flex-wrap gap-2">
                {addresses.map((a) => (
                  <button
                    key={a.id}
                    type="button"
                    onClick={() => setAddress(`${a.recipientName} — ${[a.street, a.ward, a.district, a.province].filter(Boolean).join(', ')} (${a.phone})`)}
                    className="rounded-sm border border-border px-2 py-1 text-xs hover:border-brand"
                  >
                    {a.recipientName} · {a.province}
                  </button>
                ))}
              </div>
            )}
            <textarea
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              placeholder="Nhập địa chỉ giao hàng…"
              rows={2}
              className="w-full rounded-sm border border-border px-3 py-2 text-sm outline-none placeholder:text-placeholder focus:border-brand focus:ring-2 focus:ring-brand/30"
            />
          </div>
          <div className="rounded-md border border-border bg-card p-4">
            <h2 className="mb-2 font-semibold">Phương thức thanh toán</h2>
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" checked={paymentMethod === 'COD'} onChange={() => setPaymentMethod('COD')} />
                Thanh toán khi nhận hàng (COD)
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" checked={paymentMethod === 'VNPAY_QR'} onChange={() => setPaymentMethod('VNPAY_QR')} />
                VNPay QR
              </label>
            </div>
          </div>
        </div>
        <div className="h-fit rounded-md border border-border bg-card p-4">
          <h2 className="mb-2 font-semibold">Đơn hàng</h2>
          <ul className="mb-3 max-h-40 space-y-1 overflow-auto text-sm">
            {cart.items.map((it) => (
              <li key={it.sku} className="flex justify-between gap-2 text-gray-600">
                <span className="line-clamp-1">
                  {it.productName} ×{it.quantity}
                </span>
                <span className="shrink-0">{vnd(it.subtotal)}</span>
              </li>
            ))}
          </ul>
          <p className="mb-3 border-t pt-2 text-lg font-bold text-brand">{vnd(cart.totalAmount)}</p>
          {error && <p className="mb-2 text-sm text-red-600">{error}</p>}
          <Button type="submit" className="w-full" disabled={create.isPending}>
            {create.isPending ? 'Đang xử lý…' : 'Đặt hàng'}
          </Button>
        </div>
      </form>
    </div>
  )
}
