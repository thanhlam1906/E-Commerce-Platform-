import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../auth'
import type { Cart } from '../types'

export function useCart() {
  return useQuery({
    queryKey: ['cart'],
    queryFn: () => api<Cart>('/api/v1/cart'),
    staleTime: 15_000,
  })
}

export function useAddCartItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sku, quantity }: { sku: string; quantity: number }) =>
      api<Cart>('/api/v1/cart/items', { method: 'POST', body: JSON.stringify({ sku, quantity }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cart'] }),
  })
}

export function useUpdateCartItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sku, quantity }: { sku: string; quantity: number }) =>
      api<Cart>(`/api/v1/cart/items/${sku}`, { method: 'PUT', body: JSON.stringify({ quantity }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cart'] }),
  })
}

export function useRemoveCartItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sku: string) => api<Cart>(`/api/v1/cart/items/${sku}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cart'] }),
  })
}

/** Gọi sau khi đăng nhập: gộp giỏ guest vào tài khoản. */
export async function mergeGuestCart() {
  await api<Cart>('/api/v1/cart/merge', { method: 'POST' })
}
