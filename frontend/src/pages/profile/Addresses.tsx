import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AddressForm } from '../../components/address/AddressForm'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { useAddresses, useCreateAddress, useDeleteAddress, useUpdateAddress } from '../../hooks/useAddresses'
import type { AddressInput } from '../../hooks/useAddresses'
import type { Address } from '../../types'

export default function Addresses() {
  const { data: addresses, isLoading } = useAddresses()
  const create = useCreateAddress()
  const update = useUpdateAddress()
  const remove = useDeleteAddress()
  const [formFor, setFormFor] = useState<Address | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Address | null>(null)

  function handleSubmit(body: AddressInput) {
    if (formFor && formFor !== 'new') update.mutate({ id: formFor.id, body })
    else create.mutate(body)
    setFormFor(null)
  }

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="mb-4 text-xl font-bold">Địa chỉ giao hàng</h1>
      {formFor ? (
        <div className="mb-4 rounded-md border border-border bg-card p-4">
          <AddressForm initial={formFor !== 'new' ? formFor : undefined} onSubmit={handleSubmit} onCancel={() => setFormFor(null)} />
        </div>
      ) : (
        <Button className="mb-4" onClick={() => setFormFor('new')}>
          + Thêm địa chỉ mới
        </Button>
      )}
      {isLoading ? (
        <div className="py-10 text-center text-gray-500">Đang tải…</div>
      ) : !addresses || addresses.length === 0 ? (
        <EmptyState message="Chưa có địa chỉ nào" />
      ) : (
        <div className="space-y-3">
          {addresses.map((a) => (
            <div key={a.id} className="flex items-center justify-between rounded-md border border-border bg-card p-4">
              <div>
                <p className="font-medium">
                  {a.recipientName}
                  {a.default && <span className="ml-2 rounded-full bg-brand-light px-1.5 py-0.5 text-xs text-brand">Mặc định</span>}
                </p>
                <p className="text-sm text-gray-600">
                  {[a.street, a.ward, a.district, a.province].filter(Boolean).join(', ')}
                </p>
                <p className="text-sm text-gray-500">{a.phone}</p>
              </div>
              <div className="flex shrink-0 gap-3 text-sm">
                <button className="text-brand" onClick={() => setFormFor(a)}>
                  Sửa
                </button>
                <button className="text-red-500" onClick={() => setDeleting(a)}>
                  Xóa
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
      <ConfirmDialog
        open={!!deleting}
        title="Xóa địa chỉ"
        message={`Xóa địa chỉ của ${deleting?.recipientName}?`}
        confirmLabel="Xóa"
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
      />
      <Link to="/profile" className="mt-4 inline-block text-brand">
        ← Về hồ sơ
      </Link>
    </div>
  )
}
