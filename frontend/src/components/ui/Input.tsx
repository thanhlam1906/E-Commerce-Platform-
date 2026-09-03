import type { InputHTMLAttributes } from 'react'

export function Input({ className = '', ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`w-full h-10 rounded-sm border border-border bg-white px-3 text-sm outline-none placeholder:text-placeholder focus:border-brand focus:ring-2 focus:ring-brand/30 ${className}`}
      {...props}
    />
  )
}
