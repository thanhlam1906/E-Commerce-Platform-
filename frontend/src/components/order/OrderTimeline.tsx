import { formatDateTime } from '../../lib/format'
import type { OrderHistory } from '../../types'

export function OrderTimeline({ history }: { history: OrderHistory[] }) {
  return (
    <ol className="space-y-3">
      {history.map((h) => (
        <li key={h.id} className="flex gap-3">
          <div className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand" />
          <div>
            <p className="text-sm">
              {h.newStatus}
              {h.reason ? ` — ${h.reason}` : ''}
            </p>
            <p className="text-xs text-gray-400">{formatDateTime(h.createdAt)}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
