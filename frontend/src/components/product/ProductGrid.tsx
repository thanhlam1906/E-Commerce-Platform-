import type { Product } from '../../types'
import { useSoldCounts } from '../../hooks/useProducts'
import { EmptyState } from '../ui/EmptyState'
import { Skeleton } from '../ui/Skeleton'
import { ProductCard } from './ProductCard'

/** Tổng đã bán của product = Σ theo SKU các variant. sold null khi chưa load/API hỏng → ẩn dòng. */
function totalSold(p: Product, sold: Record<string, number> | undefined): number | null {
  if (!sold) return null
  return p.variants.reduce((n, v) => n + (sold[v.sku] ?? 0), 0)
}

export function ProductGrid({ products, loading }: { products: Product[]; loading?: boolean }) {
  const skus = products.flatMap((p) => p.variants.map((v) => v.sku))
  const { data: sold } = useSoldCounts(skus)
  if (loading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
        {Array.from({ length: 12 }).map((_, i) => (
          <Skeleton key={i} className="aspect-square" />
        ))}
      </div>
    )
  }
  if (products.length === 0) return <EmptyState message="Không có sản phẩm nào" />
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
      {products.map((p) => (
        <ProductCard key={p.id} product={p} sold={totalSold(p, sold)} />
      ))}
    </div>
  )
}
