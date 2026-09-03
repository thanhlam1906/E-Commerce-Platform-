import { Link } from 'react-router-dom'
import { Truck, CreditCard, ShoppingBagOpen, Headphones } from '@phosphor-icons/react'
import { ProductGrid } from '../components/product/ProductGrid'
import { useProducts } from '../hooks/useProducts'

const DEALS = [
  { icon: Truck, title: 'Vận chuyển toàn quốc', sub: 'Giao nhanh mọi tỉnh thành' },
  { icon: CreditCard, title: 'Thanh toán linh hoạt', sub: 'COD · VNPay QR' },
  { icon: ShoppingBagOpen, title: 'Đa dạng sản phẩm', sub: 'Phân loại rõ ràng' },
  { icon: Headphones, title: 'Hỗ trợ tận tình', sub: 'Liên hệ qua email' },
]

export default function Home() {
  const { data, isLoading } = useProducts({ page: 0, size: 12 })

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

      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-bold text-text-primary">Sản phẩm mới</h2>
      </div>
      <ProductGrid products={data?.content ?? []} loading={isLoading} />
    </div>
  )
}
