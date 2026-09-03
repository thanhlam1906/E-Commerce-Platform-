import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useImportStock, useProductsAdmin, useStock, useStockTransactions } from '../../hooks/useAdmin'
import { useToast } from '../../components/ui/Toast'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { formatDateTime } from '../../lib/format'
import type { StockResponse } from '../../types'

const inputCls = 'w-full rounded-sm border border-border px-3 py-2 text-sm outline-none placeholder:text-placeholder focus:border-brand focus:ring-2 focus:ring-brand/30'

export default function AdminInventory() {
  const { data, isLoading } = useStock(0)
  // SKU phải tồn tại trong catalog — danh sách biến thể là nguồn chọn, không cho gõ tay
  // (gõ lệch giữa màn tạo sản phẩm và nhập kho từng tạo "kho ma"). Catalog < 500 món OK.
  const catalog = useProductsAdmin(500)
  const [selected, setSelected] = useState<StockResponse | null>(null)
  const txn = useStockTransactions(selected?.sku)
  const imp = useImportStock()
  const { toast } = useToast()
  const [sku, setSku] = useState('')
  const [qty, setQty] = useState('')

  const rows = data?.content ?? []

  const variantOptions = useMemo(() => {
    const out: { sku: string; label: string }[] = []
    for (const p of catalog.data?.content ?? []) {
      for (const v of p.variants) out.push({ sku: v.sku, label: `${p.name} — ${v.name} (${v.sku})` })
    }
    return out.sort((a, b) => a.label.localeCompare(b.label))
  }, [catalog.data])

  const currentStock = rows.find((r) => r.sku === sku)
  const pickingDisabled = catalog.isLoading || catalog.data?.content.length === 0

  function importStock(e: FormEvent) {
    e.preventDefault()
    const q = Number(qty)
    if (!sku || !q || q < 1) return
    imp.mutate(
      { sku, quantity: q, reference: 'manual_import' },
      {
        onSuccess: (r) => {
          toast(`Đã nhập ${r.quantity} cho ${r.sku}`)
          setQty('')
        },
      }
    )
  }

  function showTxn(s: StockResponse) {
    setSelected(s)
  }

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold">Tồn kho</h1>

      <form onSubmit={importStock} className="mb-4 space-y-2 rounded-md border border-border bg-card p-4">
        <div className="flex flex-wrap items-end gap-2">
          <label className="min-w-72 flex-1 text-xs text-gray-500">
            Sản phẩm / biến thể
            <select
              className={`${inputCls} mt-1`}
              value={sku}
              disabled={pickingDisabled}
              onChange={(e) => setSku(e.target.value)}
            >
              <option value="">{pickingDisabled ? 'Không có sản phẩm nào để nhập kho' : '— Chọn sản phẩm / biến thể —'}</option>
              {variantOptions.map((o) => (
                <option key={o.sku} value={o.sku}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="w-28 text-xs text-gray-500">
            Số lượng
            <input className={`${inputCls} mt-1`} type="number" min={1} value={qty} onChange={(e) => setQty(e.target.value)} />
          </label>
          <Button type="submit" disabled={imp.isPending || !sku || !qty || Number(qty) < 1}>
            {imp.isPending ? 'Đang nhập…' : 'Nhập kho'}
          </Button>
        </div>
        <p className="text-xs text-gray-500">
          {currentStock
            ? `Tồn hiện của ${sku}: ${currentStock.quantity} (khả dụng ${currentStock.available})`
            : sku
              ? `${sku} chưa có tồn kho — đây là lần nhập đầu tiên`
              : 'Chỉ nhập kho được cho sản phẩm đã tạo trong mục Sản phẩm — mã SKU lấy từ đó, không gõ tay.'}
        </p>
      </form>

      {isLoading ? (
        <div className="py-10 text-center text-gray-500">Đang tải…</div>
      ) : rows.length === 0 ? (
        <EmptyState message="Chưa có tồn kho" />
      ) : (
        <div className="overflow-x-auto rounded-md border border-border bg-card">
          <table className="w-full text-sm">
            <thead className="border-b text-left text-gray-500">
              <tr>
                <th className="px-4 py-2">SKU</th>
                <th className="px-4 py-2">Tồn</th>
                <th className="px-4 py-2">Đặt giữ</th>
                <th className="px-4 py-2">Khả dụng</th>
                <th className="px-4 py-2">Cập nhật</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.sku} className="cursor-pointer border-b last:border-0 hover:bg-brand-light" onClick={() => showTxn(s)}>
                  <td className="px-4 py-2 font-medium">{s.sku}</td>
                  <td className="px-4 py-2">{s.quantity}</td>
                  <td className="px-4 py-2">{s.reserved}</td>
                  <td className={`px-4 py-2 ${s.available <= 5 ? 'font-semibold text-red-600' : ''}`}>{s.available}</td>
                  <td className="px-4 py-2 text-gray-500">{formatDateTime(s.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selected && (
        <div className="mt-4 rounded-md border border-border bg-card p-4">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="font-semibold">Lịch sử giao dịch — {selected.sku}</h2>
            <button className="text-sm text-gray-500" onClick={() => setSelected(null)}>
              Đóng
            </button>
          </div>
          {txn.isLoading ? (
            <p className="text-sm text-gray-500">Đang tải…</p>
          ) : !txn.data || txn.data.length === 0 ? (
            <p className="text-sm text-gray-400">Chưa có giao dịch</p>
          ) : (
            <table className="w-full text-sm">
              <tbody>
                {txn.data.map((t) => (
                  <tr key={t.id} className="border-b last:border-0">
                    <td className="px-2 py-1.5 font-medium">{t.type}</td>
                    <td className="px-2 py-1.5">{t.quantity > 0 ? `+${t.quantity}` : t.quantity}</td>
                    <td className="px-2 py-1.5 text-gray-500">{t.reference}</td>
                    <td className="px-2 py-1.5 text-right text-gray-400">{formatDateTime(t.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}
