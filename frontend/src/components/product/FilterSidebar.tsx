import { Check } from '@phosphor-icons/react'
import type { ReactNode } from 'react'
import type { Category } from '../../types'

interface Props {
  cats: Category[]
  /** Danh mục gốc đang xem — chỉ render cây con bắt đầu từ đây (không render toàn bộ store). */
  rootId: string
  activeId?: string
  activeBrand?: string
  brands: string[]
  onCategory: (id: string | null) => void
  onBrand: (brand: string | null) => void
}

const rowCls = (on: boolean) =>
  `flex w-full items-center gap-1.5 rounded-sm px-2 py-1.5 text-left text-sm transition ${
    on ? 'bg-brand-50 font-medium text-brand' : 'text-gray-700 hover:bg-gray-50 hover:text-brand'
  }`

/** Thanh lọc trái, chỉ hiện khi đang xem 1 danh mục: cây của danh mục đó + brand thuộc cụm (gom con). */
export function FilterSidebar({ cats, rootId, activeId, activeBrand, brands, onCategory, onBrand }: Props) {
  const byId = new Map(cats.map((c) => [c.id, c]))
  const childrenOf = (pid: string) => cats.filter((c) => c.parentId === pid)
  const root = byId.get(rootId)

  const node = (id: string, depth: number): ReactNode => {
    const c = byId.get(id)
    if (!c) return null
    return (
      <div key={c.id}>
        <button
          onClick={() => onCategory(c.id)}
          className={rowCls(activeId === c.id)}
          style={{ paddingLeft: 10 + depth * 16 }}
        >
          {c.name}
        </button>
        {childrenOf(c.id).map((k) => node(k.id, depth + 1))}
      </div>
    )
  }

  if (!root) return null
  return (
    <div className="space-y-5">
      <section>
        <h3 className="mb-2 px-2 text-xs font-bold uppercase tracking-wide text-gray-400">Danh mục</h3>
        <nav className="space-y-0.5">{node(root.id, 0)}</nav>
      </section>

      {brands.length > 0 && (
        <section>
          <h3 className="mb-2 px-2 text-xs font-bold uppercase tracking-wide text-gray-400">Thương hiệu</h3>
          <div className="space-y-0.5">
            {brands.map((b) => {
              const on = activeBrand === b
              return (
                <button
                  key={b}
                  onClick={() => onBrand(on ? null : b)}
                  className={`${rowCls(on)} justify-between`}
                >
                  <span className="truncate">{b}</span>
                  {on && <Check size={14} weight="bold" className="shrink-0" />}
                </button>
              )
            })}
          </div>
        </section>
      )}
    </div>
  )
}
