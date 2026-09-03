import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Minus, Plus } from '@phosphor-icons/react'
import { useProduct } from '../../hooks/useProducts'
import { useAddCartItem } from '../../hooks/useCart'
import { useToast } from '../../components/ui/Toast'
import { VariantPicker } from '../../components/product/VariantPicker'
import { effectivePrice, isOnSale, vnd } from '../../lib/format'
import type { ProductVariant } from '../../types'

function remaining(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000))
  const h = String(Math.floor(s / 3600)).padStart(2, '0')
  const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const sec = String(s % 60).padStart(2, '0')
  return `${h}:${m}:${sec}`
}

export default function ProductDetail() {
  const { id } = useParams()
  const { data: product, isLoading } = useProduct(id)
  const addCart = useAddCartItem()
  const { toast } = useToast()
  const [variant, setVariant] = useState<ProductVariant | null>(null)
  const [qty, setQty] = useState(1)
  const [img, setImg] = useState('')
  const [now, setNow] = useState(() => Date.now())

  // khi navigate giữa 2 product cùng component → reset state
  useEffect(() => {
    setVariant(null)
    setQty(1)
    setImg('')
  }, [id])

  // tick 1s chỉ khi variant đang sale → khi hết hạn tự chuyển về giá gốc
  useEffect(() => {
    const active = variant ?? product?.variants?.[0]
    if (!active || !isOnSale(active)) return
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [variant, product])

  if (isLoading) return <div className="py-20 text-center text-gray-500">Đang tải…</div>
  if (!product) return <div className="py-20 text-center text-gray-500">Không tìm thấy sản phẩm</div>

  const current = variant ?? product.variants[0]
  const onSale = isOnSale(current)
  const images = current.images?.length ? current.images : product.variants[0].images ?? []

  async function addToCart() {
    if (!current) return
    await addCart.mutateAsync({ sku: current.sku, quantity: qty })
    toast('Đã thêm vào giỏ hàng')
  }

  return (
    <div>
      <nav className="mb-4 flex flex-wrap items-center gap-1 text-sm text-gray-500">
        <Link to="/" className="hover:text-brand">Trang chủ</Link>
        <span>/</span>
        <Link to="/products" className="hover:text-brand">Sản phẩm</Link>
        <span>/</span>
        <span className="line-clamp-1 text-gray-700">{product.name}</span>
      </nav>
      <div className="grid gap-6 md:grid-cols-2">
        <div>
          {images[0] ? (
            <img src={img || images[0]} alt={product.name} className="aspect-square w-full rounded-lg border bg-white object-cover" />
          ) : (
            <div className="flex aspect-square w-full items-center justify-center rounded-lg border bg-gray-50">
              <span className="max-w-[80%] truncate text-base font-medium text-gray-500">{product.name}</span>
            </div>
          )}
        {images.length > 1 && (
          <div className="mt-2 flex gap-2">
            {images.map((src, i) => (
              <button
                key={i}
                type="button"
                onClick={() => setImg(src)}
                className={`h-16 w-16 overflow-hidden rounded-sm border ${src === (img || images[0]) ? 'border-brand' : 'border-border'}`}
              >
                <img src={src} alt="" className="h-full w-full object-cover" />
              </button>
            ))}
          </div>
        )}
      </div>
      <div>
        <h1 className="text-xl font-bold">{product.name}</h1>
        {product.brand && <p className="text-sm text-gray-500">{product.brand}</p>}
        <div className="my-4 rounded-sm bg-brand-light p-3">
          {onSale ? (
            <div className="flex items-baseline gap-3">
              <p className="text-2xl font-bold text-brand-dark">{vnd(effectivePrice(current))}</p>
              <p className="text-base text-gray-400 line-through">{vnd(current.price)}</p>
              <p className="ml-auto text-sm font-semibold text-brand-dark">
                ⏱ {remaining(new Date(current.saleEndTime as string).getTime() - now)}
              </p>
            </div>
          ) : (
            <p className="text-2xl font-bold text-brand-dark">{vnd(current.price)}</p>
          )}
        </div>
        {product.variants.length > 1 && (
          <div className="mb-4">
            <p className="mb-2 text-sm text-gray-500">Phân loại</p>
            <VariantPicker variants={product.variants} value={current} onChange={setVariant} />
          </div>
        )}
        <div className="mb-4 flex items-center gap-2">
          <span className="text-sm text-gray-500">Số lượng</span>
          <button type="button" onClick={() => setQty(Math.max(1, qty - 1))} className="flex h-9 w-9 items-center justify-center rounded-sm border">
            <Minus size={14} weight="bold" />
          </button>
          <span className="w-10 text-center">{qty}</span>
          <button type="button" onClick={() => setQty(qty + 1)} className="flex h-9 w-9 items-center justify-center rounded-sm border">
            <Plus size={14} weight="bold" />
          </button>
        </div>
        <div className="flex gap-2">
          <button
            onClick={addToCart}
            disabled={addCart.isPending}
            className="rounded-sm border-2 border-brand bg-brand-light px-6 py-2 font-semibold text-brand-dark transition hover:bg-brand hover:text-white"
          >
            Thêm vào giỏ
          </button>
          <Link to="/cart" className="rounded-sm bg-brand px-6 py-2 font-semibold text-white transition hover:bg-brand-dark">
            Mua ngay
          </Link>
        </div>
        {product.description && (
          <div className="mt-6">
            <p className="mb-1 text-sm font-semibold">Mô tả</p>
            <p className="whitespace-pre-wrap text-sm text-gray-600">{product.description}</p>
          </div>
        )}
      </div>
      </div>
    </div>
  )
}
