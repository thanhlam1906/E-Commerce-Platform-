// API types — khớp response thật của backend (Spring Page shape, field asymmetry active/default).

export interface ApiDataResponse<T> {
  code: number
  message: string
  data: T
  errors?: { field: string; message: string }[]
  timestamp: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export type Role = 'CUSTOMER' | 'PRODUCT_ADMIN' | 'ORDER_ADMIN' | 'SUPER_ADMIN'

export interface User {
  id: string
  email: string
  fullName: string
  phone?: string | null
  avatarUrl?: string | null
  role: Role
  active: boolean
  createdAt: string
}

export interface Address {
  id: string
  recipientName: string
  phone: string
  province?: string | null
  district?: string | null
  ward?: string | null
  street?: string | null
  default: boolean
}

export interface ProductVariant {
  sku: string
  name: string
  price: number
  salePrice?: number | null
  saleEndTime?: string | null
  attributes: Record<string, string>
  images: string[]
}

export interface Product {
  id: string
  name: string
  slug: string
  description?: string
  categoryId?: string | null
  brand?: string
  active: boolean
  variants: ProductVariant[]
  createdAt: string
  updatedAt: string
}

export interface Category {
  id: string
  name: string
  slug: string
  parentId?: string | null
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
  updatedAt: string
}

export interface CartItem {
  sku: string
  productName: string
  variantName: string
  unitPrice: number
  quantity: number
  subtotal: number
  stockWarning: boolean
}

export interface Cart {
  items: CartItem[]
  totalAmount: number
  itemCount: number
}

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED' | 'EXPIRED'

export interface OrderItem {
  sku: string
  productName: string
  variantName: string
  unitPrice: number
  quantity: number
  subtotal: number
}

export interface Order {
  id: string
  userId: string
  orderNumber: string
  status: OrderStatus
  totalAmount: number
  currency: string
  shippingAddressSnapshot: string
  paymentMethod: string
  paymentUrl?: string | null
  createdAt: string
  updatedAt: string
  items: OrderItem[]
}

export interface CheckoutResponse {
  orderId: string
  orderNumber: string
  status: string
  paymentUrl?: string | null
  totalAmount: number
  qrImage?: string | null
}

export interface OrderHistory {
  id: string
  orderId: string
  oldStatus?: string
  newStatus: string
  changedBy?: string
  reason?: string
  createdAt: string
}

export interface StockResponse {
  sku: string
  quantity: number
  reserved: number
  available: number
  updatedAt: string
}

export interface InventoryTransaction {
  id: string
  sku: string
  type: string
  quantity: number
  reference?: string | null
  createdAt: string
}

export interface Transaction {
  id: string
  orderId: string
  userId: string
  amount: number
  currency: string
  paymentMethod: string
  gateway: string
  status: string
  paymentUrl?: string | null
  refundAmount?: number
  createdAt: string
  updatedAt: string
}
