import { Link, useSearchParams } from 'react-router-dom'
import { useCategories } from '../../hooks/useProducts'

export function CategoryNav() {
  const { data, isLoading } = useCategories()
  const [params] = useSearchParams()
  const active = params.get('categoryId') ?? ''

  if (isLoading) return null
  // Nav ngang chỉ hiện category gốc; category con nằm trong sidebar trái của trang /products.
  const cats = (data?.content ?? []).filter((c) => !c.parentId)

  return (
    <nav className="border-b bg-white">
      <div className="mx-auto flex max-w-7xl gap-6 overflow-x-auto px-4 py-2 text-sm">
        <Link
          to="/products"
          aria-current={active === '' ? 'page' : undefined}
          className={`whitespace-nowrap ${active === '' ? 'font-medium text-brand' : 'text-gray-700 hover:text-brand'}`}
        >
          Tất cả
        </Link>
        {cats.map((c) => (
          <Link
            key={c.id}
            to={`/products?categoryId=${c.id}`}
            aria-current={active === c.id ? 'page' : undefined}
            className={`whitespace-nowrap ${active === c.id ? 'font-medium text-brand' : 'text-gray-700 hover:text-brand'}`}
          >
            {c.name}
          </Link>
        ))}
      </div>
    </nav>
  )
}
