import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../auth'
import type { CheckoutResponse, Order, OrderHistory, Page } from '../types'

export function useOrders(status?: string, page = 0) {
  const qs = new URLSearchParams()
  if (status) qs.set('status', status)
  qs.set('page', String(page))
  return useQuery({
    queryKey: ['orders', { status, page }],
    queryFn: () => api<Page<Order>>(`/api/v1/orders?${qs}`),
  })
}

export function useOrder(id?: string, poll = false) {
  return useQuery({
    queryKey: ['order', id],
    queryFn: () => api<Order>(`/api/v1/orders/${id}`),
    enabled: !!id,
    // VNPAY: poll 3s trong lúc PENDING, tự dừng khi rời PENDING
    refetchInterval: poll
      ? (query) => {
          const status = query.state.data?.status
          return status && status !== 'PENDING' ? false : 3000
        }
      : false,
  })
}

export function useOrderHistory(id?: string) {
  return useQuery({
    queryKey: ['orderHistory', id],
    queryFn: () => api<OrderHistory[]>(`/api/v1/orders/${id}/history`),
    enabled: !!id,
  })
}

export function useCreateOrder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      body: { shippingAddress: string; paymentMethod: string; email: string }
      idempotencyKey: string
    }) =>
      api<CheckoutResponse>('/api/v1/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': input.idempotencyKey },
        body: JSON.stringify(input.body),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cart'] }) // giỏ đã rỗng sau checkout
      qc.invalidateQueries({ queryKey: ['orders'] })
    },
  })
}

export function useCancelOrder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<Order>(`/api/v1/orders/${id}/cancel`, { method: 'POST' }),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: ['orders'] })
      qc.invalidateQueries({ queryKey: ['order', id] })
      qc.invalidateQueries({ queryKey: ['orderHistory', id] })
    },
  })
}
