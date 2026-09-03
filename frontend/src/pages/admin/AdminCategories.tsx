import { useState } from 'react'
import type { FormEvent } from 'react'
import { useCategoriesAdmin, useCreateCategory, useDeleteCategory, useUpdateCategory } from '../../hooks/useAdmin'
import type { CategoryInput } from '../../hooks/useAdmin'
import { useToast } from '../../components/ui/Toast'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import type { Category } from '../../types'

const inputCls = 'w-full rounded-sm border border-border px-3 py-2 text-sm outline-none placeholder:text-placeholder focus:border-brand focus:ring-2 focus:ring-brand/30'

function slugify(s: string) {
  return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')
}

export default function AdminCategories() {
  const { data, isLoading } = useCategoriesAdmin()
  const create = useCreateCategory()
  const update = useUpdateCategory()
  const del = useDeleteCategory()
  const { toast } = useToast()
  const [editing, setEditing] = useState<Category | null>(null)
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [parentId, setParentId] = useState('')

  const cats = data?.content ?? []

  function openNew() {
    setEditing(null)
    setName('')
    setSlug('')
    setParentId('')
  }

  function openEdit(c: Category) {
    setEditing(c)
    setName(c.name)
    setSlug(c.slug)
    setParentId(c.parentId ?? '')
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    const body: CategoryInput = { name: name.trim(), slug: slug.trim() || slugify(name), parentId: parentId || undefined }
    if (editing) update.mutate({ id: editing.id, body }, { onSuccess: () => toast('Đã cập nhật danh mục') })
    else create.mutate(body, { onSuccess: () => toast('Đã tạo danh mục') })
    setEditing(null)
  }

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold">Danh mục</h1>

      <form onSubmit={submit} className="mb-4 grid gap-3 rounded-md border border-border bg-card p-4 sm:grid-cols-[1fr_1fr_1fr_auto]">
        <input className={inputCls} placeholder="Tên danh mục *" value={name} onChange={(e) => setName(e.target.value)} />
        <input className={inputCls} placeholder="Slug (để trống tự sinh)" value={slug} onChange={(e) => setSlug(e.target.value)} />
        <select className={inputCls} value={parentId} onChange={(e) => setParentId(e.target.value)}>
          <option value="">Danh mục cha (không có)</option>
          {cats.filter((c) => c.id !== editing?.id).map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <Button type="submit">{editing ? 'Cập nhật' : 'Thêm mới'}</Button>
        {editing && (
          <button type="button" onClick={openNew} className="text-sm text-gray-500">
            Hủy sửa
          </button>
        )}
      </form>

      {isLoading ? (
        <div className="py-10 text-center text-gray-500">Đang tải…</div>
      ) : cats.length === 0 ? (
        <EmptyState message="Chưa có danh mục" />
      ) : (
        <div className="overflow-x-auto rounded-md border border-border bg-card">
          <table className="w-full text-sm">
            <thead className="border-b text-left text-gray-500">
              <tr>
                <th className="px-4 py-2">Tên</th>
                <th className="px-4 py-2">Slug</th>
                <th className="px-4 py-2">Danh mục cha</th>
                <th className="px-4 py-2 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {cats.map((c) => (
                <tr key={c.id} className="border-b last:border-0">
                  <td className="px-4 py-2 font-medium">{c.name}</td>
                  <td className="px-4 py-2 text-gray-500">{c.slug}</td>
                  <td className="px-4 py-2 text-gray-500">{cats.find((p) => p.id === c.parentId)?.name ?? '—'}</td>
                  <td className="px-4 py-2 text-right">
                    <button className="mr-3 text-brand" onClick={() => openEdit(c)}>
                      Sửa
                    </button>
                    <button className="text-red-500" onClick={() => setDeleting(c)}>
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
        title="Xóa danh mục"
        message={`Xóa danh mục "${deleting?.name}"?`}
        confirmLabel="Xóa"
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && del.mutate(deleting.id, { onSuccess: () => toast('Đã xóa danh mục') })}
      />
    </div>
  )
}
