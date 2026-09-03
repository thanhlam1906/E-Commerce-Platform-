import { Badge } from '../ui/Badge'
import type { OrderStatus } from '../../types'

const tone: Record<OrderStatus, 'amber' | 'blue' | 'teal' | 'green' | 'gray' | 'red'> = {
  PENDING: 'amber',
  CONFIRMED: 'blue',
  SHIPPING: 'teal',
  DELIVERED: 'green',
  CANCELLED: 'gray',
  EXPIRED: 'red',
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={tone[status]}>{status}</Badge>
}
