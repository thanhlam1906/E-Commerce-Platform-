import type { ProductVariant } from '../../types'

export function VariantPicker({
  variants,
  value,
  onChange,
}: {
  variants: ProductVariant[]
  value: ProductVariant
  onChange: (v: ProductVariant) => void
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {variants.map((v) => (
        <button
          key={v.sku}
          type="button"
          onClick={() => onChange(v)}
          className={`rounded-sm border px-3 py-1.5 text-sm ${value.sku === v.sku ? 'border-brand bg-brand-light text-brand' : 'border-border hover:border-brand'}`}
        >
          {v.name}
        </button>
      ))}
    </div>
  )
}
