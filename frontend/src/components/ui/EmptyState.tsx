import type { ReactNode } from 'react'
import { Tray } from '@phosphor-icons/react'

export function EmptyState({ message, children }: { message: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-gray-100 text-gray-400">
        <Tray size={32} weight="regular" />
      </span>
      <p className="text-gray-500">{message}</p>
      {children}
    </div>
  )
}
