const vndFormatter = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

export function vnd(n: number): string {
  return vndFormatter.format(n)
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('vi-VN')
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN')
}

export interface Priced {
  price: number
  salePrice?: number | null
  saleEndTime?: string | null
}

/** Đang giảm giá: có salePrice thấp hơn giá gốc và chưa hết hạn. */
export function isOnSale(v: Priced): boolean {
  if (!v.salePrice || v.salePrice >= v.price) return false
  if (!v.saleEndTime) return false
  return new Date(v.saleEndTime).getTime() > Date.now()
}

/** Giá hiện hành: sale nếu đang giảm, ngược lại giá gốc. */
export function effectivePrice(v: Priced): number {
  return isOnSale(v) ? (v.salePrice as number) : v.price
}

export const ORDER_STATUS_LABEL: Record<string, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
}
