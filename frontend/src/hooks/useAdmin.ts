import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../auth'
import type { Category, InventoryTransaction, Order, OrderStatus, Page, Product, Role, StockResponse, User } from '../types'

// ---------- Categories ----------

export interface CategoryInput {
  name: string
  slug: string
  parentId?: string
}

export function useCategoriesAdmin() {
  return useQuery({
    queryKey: ['categoriesAdmin'],
    queryFn: () => api<Page<Category>>('/api/v1/categories?size=200'),
  })
}

export function useCreateCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CategoryInput) => api<Category>('/api/v1/categories', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categoriesAdmin'] })
      qc.invalidateQueries({ queryKey: ['categories'] })
    },
  })
}

export function useUpdateCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: CategoryInput }) =>
      api<Category>(`/api/v1/categories/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categoriesAdmin'] })
      qc.invalidateQueries({ queryKey: ['categories'] })
    },
  })
}

export function useDeleteCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/categories/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categoriesAdmin'] })
      qc.invalidateQueries({ queryKey: ['categories'] })
    },
  })
}

// ---------- Products (multipart) ----------

export interface VariantInput {
  sku: string
  name: string
  price: number
  salePrice?: number
  saleEndTime?: string
  attributes?: Record<string, string>
}

export interface ProductInput {
  name: string
  description?: string
  categoryId?: string
  brand?: string
  variants: VariantInput[]
}

/** Multipart: part "product" = JSON, parts "images_{variantIndex}" = files (nhiều file/cùng tên). Không set Content-Type (boundary). */
function buildProductForm(input: ProductInput, images: Record<number, File[]>) {
  const fd = new FormData()
  fd.append('product', JSON.stringify(input))
  for (const [i, files] of Object.entries(images)) {
    for (const f of files) fd.append(`images_${i}`, f)
  }
  return fd
}

export function useProductsAdmin(size = 100) {
  return useQuery({
    queryKey: ['productsAdmin', size],
    queryFn: () => api<Page<Product>>(`/api/v1/products?size=${size}`),
  })
}

export function useCreateProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ input, images }: { input: ProductInput; images: Record<number, File[]> }) =>
      api<Product>('/api/v1/products', { method: 'POST', body: buildProductForm(input, images) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['productsAdmin'] })
      qc.invalidateQueries({ queryKey: ['products'] })
    },
  })
}

export function useUpdateProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input, images }: { id: string; input: ProductInput; images: Record<number, File[]> }) =>
      api<Product>(`/api/v1/products/${id}`, { method: 'PUT', body: buildProductForm(input, images) }),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['productsAdmin'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      qc.invalidateQueries({ queryKey: ['product', vars.id] })
    },
  })
}

export function useDeleteProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/products/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['productsAdmin'] })
      qc.invalidateQueries({ queryKey: ['products'] })
    },
  })
}

// ---------- Orders admin ----------

export function useAdminOrders(status?: string, page = 0) {
  const qs = new URLSearchParams()
  if (status) qs.set('status', status)
  qs.set('page', String(page))
  return useQuery({
    queryKey: ['adminOrders', { status, page }],
    queryFn: () => api<Page<Order>>(`/api/v1/orders/admin?${qs}`),
  })
}

export function useUpdateOrderStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status, reason }: { id: string; status: OrderStatus; reason?: string }) =>
      api<Order>(`/api/v1/orders/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status, reason }) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['adminOrders'] })
      qc.invalidateQueries({ queryKey: ['orders'] })
    },
  })
}

// ---------- Inventory ----------

export function useStock(page = 0) {
  return useQuery({
    queryKey: ['stock', page],
    queryFn: () => api<Page<StockResponse>>(`/api/v1/inventory?page=${page}&size=50`),
  })
}

export function useImportStock() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { sku: string; quantity: number; reference?: string }) =>
      api<{ sku: string; quantity: number }>('/api/v1/inventory/import', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['stock'] }),
  })
}

export function useStockTransactions(sku?: string) {
  return useQuery({
    queryKey: ['stockTxn', sku],
    queryFn: () => api<InventoryTransaction[]>(`/api/v1/inventory/${sku}/transactions`),
    enabled: !!sku,
  })
}

// ---------- Users admin ----------

export function useAdminUsers(page = 0) {
  return useQuery({
    queryKey: ['adminUsers', page],
    queryFn: () => api<Page<User>>(`/api/v1/users?page=${page}&size=20`),
  })
}

export function useUpdateUserStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api<User>(`/api/v1/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ active }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['adminUsers'] }),
  })
}

export function useUpdateUserRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, role }: { id: string; role: Role }) =>
      api<User>(`/api/v1/users/${id}/roles`, { method: 'PATCH', body: JSON.stringify({ role }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['adminUsers'] }),
  })
}
