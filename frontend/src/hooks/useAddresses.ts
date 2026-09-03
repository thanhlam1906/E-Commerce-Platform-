import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../auth'
import type { Address } from '../types'

export interface AddressInput {
  recipientName: string
  phone: string
  province?: string
  district?: string
  ward?: string
  street?: string
  // request dùng isDefault (asymmetric với response.default)
  isDefault?: boolean
}

export function useAddresses() {
  return useQuery({
    queryKey: ['addresses'],
    queryFn: () => api<Address[]>('/api/v1/users/me/addresses'),
  })
}

export function useCreateAddress() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: AddressInput) =>
      api<Address>('/api/v1/users/me/addresses', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
  })
}

export function useUpdateAddress() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: AddressInput }) =>
      api<Address>(`/api/v1/users/me/addresses/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
  })
}

export function useDeleteAddress() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/users/me/addresses/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
  })
}
