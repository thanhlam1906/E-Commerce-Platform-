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

export const ORDER_STATUS_LABEL: Record<string, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
}
