import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Funnel } from '@phosphor-icons/react'
import { useBrands, useCategories, useProducts } from '../../hooks/useProducts'
import { ProductGrid } from '../../components/product/ProductGrid'
import { FilterSidebar } from '../../components/product/FilterSidebar'
import { Pagination } from '../../components/ui/Pagination'
import { Button } from '../../components/ui/Button'
import type { Category } from '../../types'

export default function ProductList() {
  const [params, setParams] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  const categoryId = params.get('categoryId') ?? undefined
  const brand = params.get('brand') ?? undefined
  const page = Number(params.get('page') ?? 0)
  const [showFilter, setShowFilter] = useState(false)

  const { data, isLoading } = useProducts({ keyword, categoryId, brand, page, size: 12 })
  const { data: catData } = useCategories(50)
  const cats = useMemo(() => catData?.content ?? [], [catData])

  const byId = useMemo(() => new Map(cats.map((c) => [c.id, c])), [cats])
  const activeCat: Category | undefined = categoryId ? byId.get(categoryId) : undefined

  // Gốc cây của danh mục đang xem (đi lên tới top) — dùng để scope sidebar + danh sách brand.
  const rootId = useMemo(() => {
    let c = activeCat
    while (c?.parentId) c = byId.get(c.parentId)
    return c?.id
  }, [activeCat, byId])

  const { data: brands } = useBrands(rootId)
  // Sidebar chỉ hiện khi đang xem một danh mục cụ thể (không phải "Tất cả sản phẩm" / tìm kiếm).
  const scoped = !!activeCat && !keyword
  const childCats = activeCat ? cats.filter((c) => c.parentId === activeCat.id) : []

  /** Gộp filter vào URL (bỏ page → về trang đầu khi đổi điều kiện). */
  function update(over: { keyword?: string | null; categoryId?: string | null; brand?: string | null }) {
    const next = new URLSearchParams()
    const merged = { keyword, categoryId, brand, ...over }
    if (merged.keyword) next.set('keyword', merged.keyword)
    if (merged.categoryId) next.set('categoryId', merged.categoryId)
    if (merged.brand) next.set('brand', merged.brand)
    setParams(next)
  }

  function goPage(p: number) {
    const next = new URLSearchParams()
    if (keyword) next.set('keyword', keyword)
    if (categoryId) next.set('categoryId', categoryId)
    if (brand) next.set('brand', brand)
    next.set('page', String(p))
    setParams(next)
  }

  const heading = keyword ? `Kết quả cho “${keyword}”` : activeCat ? activeCat.name : 'Tất cả sản phẩm'
  const breadcrumb = activeCat?.parentId ? byId.get(activeCat.parentId)?.name : null

  return (
    <div className={scoped ? 'flex gap-6' : ''}>
      {scoped && rootId && (
        <aside className={showFilter ? 'w-56 shrink-0' : 'hidden w-56 shrink-0 lg:block'}>
          <div className="rounded-md border border-border bg-card p-3 shadow-sm lg:sticky lg:top-16">
            <FilterSidebar
              cats={cats}
              rootId={rootId}
              activeId={categoryId}
              activeBrand={brand}
              brands={brands ?? []}
              onCategory={(id) => update({ categoryId: id, brand: null })}
              onBrand={(b) => update({ brand: b })}
            />
          </div>
        </aside>
      )}

      <div className="min-w-0 flex-1">
        {/* Mobile: nút bật sidebar lọc (tìm kiếm đã nằm trên header; desktop hiện sidebar sẵn) */}
        {scoped && (
          <div className="mb-3 flex justify-end">
            <Button variant="secondary" className="lg:hidden" onClick={() => setShowFilter((v) => !v)}>
              <Funnel size={16} weight="bold" /> Lọc
            </Button>
          </div>
        )}

        {/* Breadcrumb + tiêu đề */}
        <p className="mb-1 text-sm text-gray-500">
          {breadcrumb ? `${breadcrumb} › ` : ''}
          <span className="font-medium text-text-primary">{heading}</span>
          {brand ? <span> · Thương hiệu {brand}</span> : null} — {data?.totalElements ?? 0} sản phẩm
        </p>

        {/* Danh mục con (khi đang ở category cha) */}
        {childCats.length > 0 && (
          <div className="mb-4 flex flex-wrap gap-1.5">
            {childCats.map((c) => (
              <button
                key={c.id}
                onClick={() => update({ categoryId: c.id, brand: null })}
                className={`rounded-full border px-3 py-1 text-sm transition ${
                  categoryId === c.id ? 'border-brand bg-brand text-white' : 'border-border bg-white text-gray-700 hover:border-brand/50 hover:text-brand'
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
        )}

        <ProductGrid products={data?.content ?? []} loading={isLoading} />
        <Pagination page={page} totalPages={data?.totalPages ?? 0} onPage={goPage} />
      </div>
    </div>
  )
}
