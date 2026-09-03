import { CaretLeft, CaretRight } from '@phosphor-icons/react'

export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number
  totalPages: number
  onPage: (p: number) => void
}) {
  if (totalPages <= 1) return null

  // Trang đầu, cuối, và quanh trang hiện tại (Spring page 0-based)
  const pages = Array.from({ length: totalPages }, (_, i) => i).filter(
    (p) => p === 0 || p === totalPages - 1 || Math.abs(p - page) <= 1,
  )
  const items: (number | '...')[] = []
  let prev = -2
  for (const p of pages) {
    if (p - prev > 1) items.push('...')
    items.push(p)
    prev = p
  }

  return (
    <div className="flex items-center justify-center gap-1 py-4">
      <button
        className="flex items-center justify-center rounded-sm border px-2 py-1 text-sm disabled:opacity-40"
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
        aria-label="Trang trước"
      >
        <CaretLeft size={16} weight="bold" />
      </button>
      {items.map((it, i) =>
        it === '...' ? (
          <span key={`e${i}`} className="px-1 text-gray-400">
            …
          </span>
        ) : (
          <button
            key={it}
            onClick={() => onPage(it)}
            className={`rounded-sm px-3 py-1 text-sm ${it === page ? 'bg-brand text-white' : 'border bg-white'}`}
          >
            {it + 1}
          </button>
        ),
      )}
      <button
        className="flex items-center justify-center rounded-sm border px-2 py-1 text-sm disabled:opacity-40"
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
        aria-label="Trang sau"
      >
        <CaretRight size={16} weight="bold" />
      </button>
    </div>
  )
}
