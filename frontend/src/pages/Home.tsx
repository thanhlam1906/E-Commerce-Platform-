import { useMemo, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  Truck,
  CreditCard,
  ShoppingBagOpen,
  Headphones,
  Lightning,
  Flame,
  Sparkle,
  MagnifyingGlass,
} from '@phosphor-icons/react'
import { ProductGrid } from '../components/product/ProductGrid'
import { useProducts, useSoldCounts } from '../hooks/useProducts'
import { isOnSale } from '../lib/format'
import type { Product } from '../types'

const DEALS = [
  { icon: Truck, title: 'Vận chuyển toàn quốc', sub: 'Giao nhanh mọi tỉnh thành' },
  { icon: CreditCard, title: 'Thanh toán linh hoạt', sub: 'COD · VNPay QR' },
  { icon: ShoppingBagOpen, title: 'Đa dạng sản phẩm', sub: 'Phân loại rõ ràng' },
  { icon: Headphones, title: 'Hỗ trợ tận tình', sub: 'Liên hệ qua email' },
]

// Từ khóa curated khớp data demo (ES search theo name/slug/description). # ponytail: static, chưa có
// search-analytics backend để tính top-search thật — bổ sung khi có dữ liệu log tìm kiếm.
const TOP_SEARCHES = ['áo khoác', 'quần jean', 'blazer', 'iphone', 'samsung', 'apple watch', 'nike', 'macbook']

const FLASH_MAX = 10
const BEST_TOP = 10
const NEW_COUNT = 12

function SectionHead({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <div className="mb-3 flex items-center gap-2">
      <span className="flex h-8 w-8 items-center justify-center rounded-md bg-brand-50 text-brand">{icon}</span>
      <h2 className="text-lg font-bold text-text-primary">{title}</h2>
    </div>
  )
}

export default function Home() {
  const { data, isLoading } = useProducts({ page: 0, size: 100 })
  const products = data?.content ?? []
  const allSkus = products.flatMap((p) => p.variants.map((v) => v.sku))
  const soldQ = useSoldCounts(allSkus)
  const sold = soldQ.data
  // Chờ sold fetch xong mới render Bán chạy nhất để tránh re-sort sau khi ảnh đã hiện.
  const soldReady = soldQ.isSuccess || soldQ.isError

  const flash = useMemo(() => products.filter((p) => p.variants.some(isOnSale)).slice(0, FLASH_MAX), [products])

  // ponytail: rank toàn bộ catalog phía client — đúng ở demo scale (36 products); khi catalog lớn
  // thì đưa sort theo tổng sold xuống backend (endpoint chuyên dụng /best-sellers).
  const best = useMemo(() => {
    const totalSold = (p: Product) => p.variants.reduce((n, v) => n + (sold?.[v.sku] ?? 0), 0)
    return [...products].sort((a, b) => totalSold(b) - totalSold(a)).slice(0, BEST_TOP)
  }, [products, sold])

  const fresh = products.slice(0, NEW_COUNT)

  return (
    <div>
      {/* Hero */}
      <section className="mb-6 overflow-hidden rounded-xl bg-brand text-white shadow-sm">
        <div className="px-6 py-10 sm:px-10 sm:py-14">
          <h1 className="max-w-xl text-2xl font-bold leading-tight sm:text-3xl">
            Mua sắm dễ dàng, giao nhanh toàn quốc
          </h1>
          <p className="mt-2 max-w-md text-sm text-orange-100">
            Sản phẩm chất lượng từ VoltStack Shop — thanh toán COD hoặc VNPay QR.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link
              to="/products"
              className="rounded-md bg-white px-5 py-2.5 text-sm font-bold text-brand shadow-sm transition hover:bg-orange-50"
            >
              Khám phá ngay
            </Link>
            <Link
              to="/cart"
              className="rounded-md border border-white/60 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-white/10"
            >
              Giỏ hàng
            </Link>
          </div>
        </div>
      </section>

      {/* Deal chips */}
      <section className="mb-6 rounded-md border border-border bg-card shadow-sm">
        <div className="grid grid-cols-2 lg:grid-cols-4 divide-y lg:divide-y-0 lg:divide-x divide-gray-100">
          {DEALS.map((d) => (
            <div key={d.title} className="flex items-center gap-3 p-4">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm bg-brand-50 text-brand">
                <d.icon size={24} weight="regular" />
              </span>
              <div>
                <p className="text-sm font-semibold text-text-primary">{d.title}</p>
                <p className="text-xs text-text-muted">{d.sub}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Tìm kiếm hàng đầu */}
      <section className="mb-8 rounded-md border border-border bg-card px-4 py-3 shadow-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className="mr-1 flex items-center gap-1.5 text-sm font-bold text-text-primary">
            <MagnifyingGlass size={16} weight="bold" className="text-brand" />
            Tìm kiếm hàng đầu
          </span>
          {TOP_SEARCHES.map((kw) => (
            <Link
              key={kw}
              to={`/products?keyword=${encodeURIComponent(kw)}`}
              className="rounded-full border border-border bg-white px-3 py-1 text-sm text-gray-600 transition hover:border-brand hover:text-brand"
            >
              {kw}
            </Link>
          ))}
        </div>
      </section>

      {isLoading ? (
        <ProductGrid products={[]} loading />
      ) : (
        <>
          {/* Flash Sale */}
          {flash.length > 0 && (
            <section className="mb-8 overflow-hidden rounded-md border border-border bg-card shadow-sm">
              <div className="flex items-center justify-between bg-brand px-4 py-2.5">
                <h2 className="flex items-center gap-1.5 text-base font-extrabold text-white">
                  <Lightning size={20} weight="fill" />
                  Flash Sale
                </h2>
                <span className="rounded-full bg-white/15 px-2.5 py-0.5 text-xs font-semibold text-white">
                  Giảm sốc mỗi ngày
                </span>
              </div>
              <div className="p-3">
                <ProductGrid products={flash} />
              </div>
            </section>
          )}

          {/* Bán chạy nhất */}
          {soldReady && best.length > 0 && (
            <section className="mb-8">
              <SectionHead icon={<Flame size={18} weight="fill" />} title="Bán chạy nhất" />
              <ProductGrid products={best} />
            </section>
          )}

          {/* Sản phẩm mới */}
          {fresh.length > 0 && (
            <section className="mb-8">
              <SectionHead icon={<Sparkle size={18} weight="fill" />} title="Sản phẩm mới" />
              <ProductGrid products={fresh} />
            </section>
          )}
        </>
      )}
    </div>
  )
}
