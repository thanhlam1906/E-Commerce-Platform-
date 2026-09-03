import { useQuery } from '@tanstack/react-query'
import { api } from '../auth'
import type { Category, Page, Product } from '../types'

export interface ProductListParams {
  categoryId?: string
  brand?: string
  keyword?: string
  page?: number
  size?: number
  sort?: string
}

export function useProducts(params: ProductListParams = {}) {
  const qs = new URLSearchParams()
  if (params.categoryId) qs.set('categoryId', params.categoryId)
  if (params.brand) qs.set('brand', params.brand)
  if (params.keyword) qs.set('keyword', params.keyword)
  qs.set('page', String(params.page ?? 0))
  qs.set('size', String(params.size ?? 12))
  if (params.sort) qs.set('sort', params.sort)
  return useQuery({
    queryKey: ['products', params],
    queryFn: () => api<Page<Product>>(`/api/v1/products?${qs}`),
  })
}

export function useBrands(categoryId?: string) {
  const qs = categoryId ? `?categoryId=${encodeURIComponent(categoryId)}` : ''
  return useQuery({
    queryKey: ['brands', categoryId ?? 'all'],
    queryFn: () => api<string[]>(`/api/v1/products/brands${qs}`),
  })
}

export function useProduct(id?: string) {
  return useQuery({
    queryKey: ['product', id],
    queryFn: () => api<Product>(`/api/v1/products/${id}`),
    enabled: !!id,
  })
}

/** Số đã bán thật per SKU (public, Order-Service). Record rỗng → chưa có đơn DELIVERED nào. */
export function useSoldCounts(skus: string[]) {
  const uniq = [...new Set(skus.filter((s) => s && s.trim()))]
  const qs = uniq.map((s) => `skus=${encodeURIComponent(s)}`).join('&')
  return useQuery({
    queryKey: ['sold', uniq],
    queryFn: () => api<Record<string, number>>(`/api/v1/orders/sold?${qs}`),
    enabled: uniq.length > 0,
    staleTime: 60_000,
  })
}

export function useCategories(size = 30) {
  return useQuery({
    queryKey: ['categories', size],
    queryFn: () => api<Page<Category>>(`/api/v1/categories?size=${size}`),
  })
}
