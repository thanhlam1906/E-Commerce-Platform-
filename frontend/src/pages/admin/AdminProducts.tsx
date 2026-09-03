import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useProductsAdmin, useDeleteProduct } from '../../hooks/useAdmin'
import { useToast } from '../../components/ui/Toast'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { vnd } from '../../lib/format'
import type { Product } from '../../types'

export default function AdminProducts() {
  const { data, isLoading } = useProductsAdmin()
  const del = useDeleteProduct()
  const { toast } = useToast()
  const [deleting, setDeleting] = useState<Product | null>(null)

  const products = data?.content ?? []

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold">Sản phẩm</h1>
        <Link to="/admin/products/new" className="rounded-sm bg-brand px-4 py-2 text-sm font-semibold text-white">
          + Sản phẩm mới
        </Link>
      </div>
      {isLoading ? (
        <div className="py-10 text-center text-gray-500">Đang tải…</div>
      ) : products.length === 0 ? (
        <EmptyState message="Chưa có sản phẩm" />
      ) : (
        <div className="overflow-x-auto rounded-md border border-border bg-card">
          <table className="w-full text-sm">
            <thead className="border-b text-left text-gray-500">
              <tr>
                <th className="px-4 py-2">Tên</th>
                <th className="px-4 py-2">Brand</th>
                <th className="px-4 py-2">Biến thể</th>
                <th className="px-4 py-2">Giá</th>
                <th className="px-4 py-2 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id} className="border-b last:border-0">
                  <td className="px-4 py-2 font-medium">{p.name}</td>
                  <td className="px-4 py-2 text-gray-500">{p.brand ?? '—'}</td>
                  <td className="px-4 py-2 text-gray-500">{p.variants.length}</td>
                  <td className="px-4 py-2">{vnd(Math.min(...p.variants.map((v) => v.price)))}</td>
                  <td className="px-4 py-2 text-right">
                    <Link to={`/admin/products/${p.id}/edit`} className="mr-3 text-brand">
                      Sửa
                    </Link>
                    <button className="text-red-500" onClick={() => setDeleting(p)}>
                      Xóa
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ConfirmDialog
        open={!!deleting}
        title="Xóa sản phẩm"
        message={`Xóa "${deleting?.name}"? (ẩn khỏi catalog, không xóa vĩnh viễn)`}
        confirmLabel="Xóa"
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && del.mutate(deleting.id, { onSuccess: () => toast('Đã xóa sản phẩm') })}
      />
    </div>
  )
}
