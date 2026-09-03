import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useCreateProduct, useUpdateProduct } from '../../hooks/useAdmin'
import type { ProductInput, VariantInput } from '../../hooks/useAdmin'
import { useCategories, useProduct } from '../../hooks/useProducts'
import { useToast } from '../../components/ui/Toast'
import { Button } from '../../components/ui/Button'

const inputCls = 'w-full rounded-sm border border-border px-3 py-2 text-sm outline-none placeholder:text-placeholder focus:border-brand focus:ring-2 focus:ring-brand/30'

/** Bỏ dấu tiếng Việt → slug ASCII. Mã SKU gợi ý: {san-pham}-{phan-loai}, merchant vẫn sửa tay được. */
function toSlug(s: string) {
  const noAccent = s
    .normalize('NFD')
    .split('')
    .filter((ch) => ch.charCodeAt(0) < 0x300 || ch.charCodeAt(0) > 0x36f)
    .join('')
    .toLowerCase()
    .replace(/đ/g, 'd') // Đ/đ không phân rã được — map thủ công
  return noAccent.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

function suggestSku(productName: string, variantName: string) {
  const base = [toSlug(productName), toSlug(variantName)].filter(Boolean).join('-')
  return base.slice(0, 48).replace(/-+$/g, '')
}

/** ISO "…Z" → giá trị input datetime-local "YYYY-MM-DDTHH:mm" (giờ máy). */
function toLocalInput(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function ProductForm() {
  const { id } = useParams()
  const isEdit = !!id
  const { data: existing } = useProduct(isEdit ? id : undefined)
  const { data: cats } = useCategories(100)
  const create = useCreateProduct()
  const update = useUpdateProduct()
  const navigate = useNavigate()
  const { toast } = useToast()

  const [name, setName] = useState('')
  const [brand, setBrand] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [variants, setVariants] = useState<VariantInput[]>([{ sku: '', name: 'Mặc định', price: 0 }])
  const [images, setImages] = useState<Record<number, File[]>>({})
  const [error, setError] = useState('')

  // edit: hydrate form khi product cũ tải về
  useEffect(() => {
    if (existing) {
      setName(existing.name)
      setBrand(existing.brand ?? '')
      setDescription(existing.description ?? '')
      setCategoryId(existing.categoryId ?? '')
      setVariants(
        existing.variants.map((v) => ({
          sku: v.sku,
          name: v.name,
          price: v.price,
          salePrice: v.salePrice ?? undefined,
          saleEndTime: v.saleEndTime ? toLocalInput(v.saleEndTime) : undefined,
          attributes: v.attributes,
        }))
      )
    }
  }, [existing])

  function setVariant(i: number, patch: Partial<VariantInput>) {
    setVariants((vs) => vs.map((v, idx) => (idx === i ? { ...v, ...patch } : v)))
  }

  /** Gõ tên phân loại → tự điền SKU gợi ý (chỉ khi ô SKU còn trống). */
  function onVariantName(i: number, vname: string) {
    setVariants((vs) =>
      vs.map((v, idx) => (idx === i ? { ...v, name: vname, sku: v.sku || suggestSku(name.trim(), vname.trim()) } : v))
    )
  }

  // Gõ/đổi tên sản phẩm → các biến thể chưa có SKU được gợi ý mã (không đè SKU đã nhập tay).
  useEffect(() => {
    if (!name.trim()) return
    setVariants((vs) => vs.map((v) => (v.sku.trim() ? v : { ...v, sku: suggestSku(name.trim(), v.name.trim()) })))
  }, [name])

  function onFiles(i: number, files: FileList | null) {
    if (!files) return
    setImages((m) => ({ ...m, [i]: Array.from(files) }))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    // Sale hợp lệ cần đủ giá KM > 0 + thời hạn; thiếu 1 trong 2 → không sale (gửi undefined để xoá)
    const clean = variants
      .filter((v) => v.sku.trim() && v.price > 0)
      .map((v) => {
        const on = !!v.salePrice && v.salePrice > 0 && !!v.saleEndTime
        return {
          ...v,
          salePrice: on ? v.salePrice : undefined,
          saleEndTime: on ? new Date(v.saleEndTime as string).toISOString() : undefined,
        }
      })
    if (!name.trim() || clean.length === 0) {
      setError('Cần tên sản phẩm và ít nhất một biến thể hợp lệ')
      return
    }
    setError('')
    // Edit: bỏ images khỏi JSON để giữ ảnh cũ; file mới ở part images_i sẽ được append
    const input: ProductInput = {
      name: name.trim(),
      brand: brand.trim() || undefined,
      description: description.trim() || undefined,
      categoryId: categoryId || undefined,
      variants: clean,
    }
    if (isEdit && id) await update.mutateAsync({ id, input, images })
    else await create.mutateAsync({ input, images })
    toast(isEdit ? 'Đã cập nhật sản phẩm' : 'Đã tạo sản phẩm')
    navigate('/admin/products')
  }

  return (
    <form onSubmit={submit} className="mx-auto max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">{isEdit ? 'Sửa sản phẩm' : 'Sản phẩm mới'}</h1>
        <Link to="/admin/products" className="text-sm text-brand">
          ← Danh sách
        </Link>
      </div>

      <div className="space-y-3 rounded-md border border-border bg-card p-4">
        <input className={inputCls} placeholder="Tên sản phẩm *" value={name} onChange={(e) => setName(e.target.value)} />
        <div className="grid gap-3 sm:grid-cols-2">
          <input className={inputCls} placeholder="Thương hiệu" value={brand} onChange={(e) => setBrand(e.target.value)} />
          <select className={inputCls} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="">Chưa phân loại</option>
            {(cats?.content ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <textarea
          className={inputCls}
          rows={3}
          placeholder="Mô tả"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </div>

      <div className="space-y-3 rounded-md border border-border bg-card p-4">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold">Biến thể</h2>
          <button
            type="button"
            onClick={() => setVariants((vs) => [...vs, { sku: '', name: '', price: 0 }])}
            className="text-sm text-brand"
          >
            + Thêm biến thể
          </button>
        </div>
        {variants.map((v, i) => (
          <div key={i} className="space-y-2 rounded-sm border border-border p-3">
            <div className="grid gap-2 sm:grid-cols-3">
              <input className={inputCls} placeholder={`SKU * (vd: ${v.sku || 'SP-' + (i + 1)})`} value={v.sku} onChange={(e) => setVariant(i, { sku: e.target.value })} />
              <input className={inputCls} placeholder="Tên phân loại *" value={v.name} onChange={(e) => onVariantName(i, e.target.value)} />
              <input
                className={inputCls}
                type="number"
                min={0}
                placeholder="Giá *"
                value={v.price || ''}
                onChange={(e) => setVariant(i, { price: Number(e.target.value) })}
              />
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              <input
                className={inputCls}
                type="number"
                min={0}
                placeholder="Giá khuyến mãi (để trống: không sale)"
                value={v.salePrice ?? ''}
                onChange={(e) => setVariant(i, { salePrice: e.target.value ? Number(e.target.value) : undefined })}
              />
              <input
                className={inputCls}
                type="datetime-local"
                value={v.saleEndTime ?? ''}
                onChange={(e) => setVariant(i, { saleEndTime: e.target.value || undefined })}
              />
            </div>
            <label className="block text-sm text-gray-500">
              Ảnh (có thể chọn nhiều)
              <input type="file" accept="image/*" multiple className="mt-1 block w-full text-sm" onChange={(e) => onFiles(i, e.target.files)} />
            </label>
            {variants.length > 1 && (
              <button type="button" onClick={() => setVariants((vs) => vs.filter((_, idx) => idx !== i))} className="text-sm text-red-500">
                Bỏ biến thể
              </button>
            )}
          </div>
        ))}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
      <Button type="submit" disabled={create.isPending || update.isPending}>
        {create.isPending || update.isPending ? 'Đang lưu…' : isEdit ? 'Lưu thay đổi' : 'Tạo sản phẩm'}
      </Button>
    </form>
  )
}
