import { useState } from 'react'
import type { FormEvent } from 'react'
import type { AddressInput } from '../../hooks/useAddresses'
import type { Address } from '../../types'
import { Button } from '../ui/Button'
import { Input } from '../ui/Input'

export function AddressForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: Address
  onSubmit: (body: AddressInput) => void
  onCancel: () => void
}) {
  const [recipientName, setRecipientName] = useState(initial?.recipientName ?? '')
  const [phone, setPhone] = useState(initial?.phone ?? '')
  const [province, setProvince] = useState(initial?.province ?? '')
  const [district, setDistrict] = useState(initial?.district ?? '')
  const [ward, setWard] = useState(initial?.ward ?? '')
  const [street, setStreet] = useState(initial?.street ?? '')
  const [isDefault, setIsDefault] = useState(initial?.default ?? false)

  function submit(e: FormEvent) {
    e.preventDefault()
    onSubmit({
      recipientName,
      phone,
      province: province || undefined,
      district: district || undefined,
      ward: ward || undefined,
      street: street || undefined,
      isDefault,
    })
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <Input placeholder="Người nhận *" required value={recipientName} onChange={(e) => setRecipientName(e.target.value)} />
      <Input placeholder="Số điện thoại *" required value={phone} onChange={(e) => setPhone(e.target.value)} />
      <Input placeholder="Tỉnh/Thành phố" value={province} onChange={(e) => setProvince(e.target.value)} />
      <Input placeholder="Quận/Huyện" value={district} onChange={(e) => setDistrict(e.target.value)} />
      <Input placeholder="Phường/Xã" value={ward} onChange={(e) => setWard(e.target.value)} />
      <Input placeholder="Số nhà, tên đường" value={street} onChange={(e) => setStreet(e.target.value)} />
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={isDefault} onChange={(e) => setIsDefault(e.target.checked)} />
        Đặt làm địa chỉ mặc định
      </label>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit">{initial ? 'Cập nhật' : 'Thêm mới'}</Button>
      </div>
    </form>
  )
}
