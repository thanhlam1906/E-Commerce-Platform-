import { Link, useSearchParams } from 'react-router-dom'
import { useCategories } from '../../hooks/useProducts'

export function CategoryNav() {
  const { data, isLoading } = useCategories()
  const [params] = useSearchParams()
  const active = params.get('categoryId') ?? ''

  if (isLoading) return null
  // Nav ngang chỉ hiện category gốc; category con nằm trong sidebar trái của trang /products.
  const cats = (data?.content ?? []).filter((c) => !c.parentId)

  // Tab đang chọn: chữ brand + gạch chân cam 2px chạm đáy band (đè lên hairline). Inactive: text secondary, hover sang brand.
  const cls = (selected: boolean) =>
    `relative inline-block whitespace-nowrap py-2 text-sm transition-colors ${
      selected
        ? 'font-medium text-brand after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-brand'
        : 'text-gray-700 hover:text-brand'
    }`

  return (
    <nav className="border-b border-border bg-white">
      <div className="mx-auto flex max-w-7xl gap-6 overflow-x-auto px-4">
        <Link to="/products" aria-current={active === '' ? 'page' : undefined} className={cls(active === '')}>
          Tất cả
        </Link>
        {cats.map((c) => (
          <Link
            key={c.id}
            to={`/products?categoryId=${c.id}`}
            aria-current={active === c.id ? 'page' : undefined}
            className={cls(active === c.id)}
          >
            {c.name}
          </Link>
        ))}
      </div>
    </nav>
  )
}
