import { Link } from 'react-router-dom'
import { Package } from '@phosphor-icons/react'
import { effectivePrice, isOnSale, vnd } from '../../lib/format'
import type { Product } from '../../types'

export function ProductCard({ product, sold }: { product: Product; sold: number | null }) {
  const variant = product.variants[0]
  const image = variant?.images?.[0]
  const price = variant ? effectivePrice(variant) : 0
  const onSale = !!variant && isOnSale(variant)
  return (
    <Link
      to={`/products/${product.id}`}
      className="group block overflow-hidden rounded-md border border-border bg-card shadow-sm transition hover:-translate-y-0.5 hover:border-brand/40 hover:shadow-md"
    >
      <div className="relative aspect-square w-full overflow-hidden bg-gray-50">
        {onSale && (
          <span className="absolute left-0 top-0 rounded-br-md bg-brand px-1.5 py-0.5 text-[11px] font-bold text-white">SALE</span>
        )}
        {image ? (
          <img
            src={image}
            alt={product.name}
            loading="lazy"
            className="h-full w-full object-cover transition duration-200 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-gray-400">
            <Package size={40} weight="regular" />
          </div>
        )}
      </div>
      <div className="p-3">
        <p className="line-clamp-2 min-h-10 text-sm text-gray-800">{product.name}</p>
        <div className="mt-2 flex items-end justify-between gap-2">
          <div className="min-w-0">
            {onSale && (
              <p className="text-xs text-gray-400 line-through">{vnd(variant.price)}</p>
            )}
            <p className="text-sm font-bold text-brand-dark">{vnd(price)}</p>
          </div>
          {sold !== null && <span className="shrink-0 text-xs text-gray-400">Đã bán {sold}</span>}
        </div>
      </div>
    </Link>
  )
}
