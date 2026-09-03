import { Outlet } from 'react-router-dom'
import { CategoryNav } from './CategoryNav'
import { TopBar } from './TopBar'

export function AppLayout() {
  return (
    <div className="flex min-h-[100dvh] flex-col">
      <TopBar />
      <CategoryNav />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-4">
        <Outlet />
      </main>
      <footer className="border-t border-border bg-white py-6 text-center text-sm text-gray-500">
        VoltStack Shop © 2026
      </footer>
    </div>
  )
}
