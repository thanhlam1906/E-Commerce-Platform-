import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { MagnifyingGlass, ShoppingCart, Storefront, CaretDown } from '@phosphor-icons/react'
import { useAuth } from '../../hooks/useAuth'
import { useCart } from '../../hooks/useCart'

export function TopBar() {
  const { user, isAuthenticated, logout } = useAuth()
  const { data: cart } = useCart()
  const [keyword, setKeyword] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()

  const isAdmin = user != null && user.role !== 'CUSTOMER'

  function submit(e: FormEvent) {
    e.preventDefault()
    const kw = keyword.trim()
    navigate(kw ? `/products?keyword=${encodeURIComponent(kw)}` : '/products')
  }

  const searchForm = (
    <form onSubmit={submit} className="flex h-10 w-full items-center overflow-hidden rounded-sm bg-white shadow-sm">
      <input
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        placeholder="Tìm sản phẩm, thương hiệu…"
        className="h-full flex-1 bg-white px-3 text-sm text-gray-800 placeholder-gray-500 outline-none"
      />
      <button
        type="submit"
        aria-label="Tìm kiếm"
        className="flex h-full items-center gap-1.5 bg-brand px-4 text-sm font-semibold text-white hover:bg-brand-dark"
      >
        <MagnifyingGlass size={16} weight="bold" />
        <span className="hidden sm:inline">Tìm</span>
      </button>
    </form>
  )

  return (
    <header className="sticky top-0 z-40 bg-brand shadow-sm">
      <div className="mx-auto max-w-7xl px-4">
        <div className="flex h-14 items-center justify-between gap-4">
          <Link to="/" className="flex items-center gap-1.5 whitespace-nowrap text-lg font-bold text-white">
            <span className="rounded bg-white/15 p-1 text-brand-light">
              <Storefront size={20} weight="bold" />
            </span>
            VoltStack Shop
          </Link>
          <div className="hidden flex-1 md:block">{searchForm}</div>
          <div className="flex shrink-0 items-center gap-4 sm:gap-5">
            <Link to="/cart" className="relative flex items-center gap-1 text-sm font-medium text-white hover:opacity-90">
              <span className="relative">
                <ShoppingCart size={20} weight="bold" />
                {!!cart?.itemCount && (
                  <span className="absolute -right-2 -top-1.5 rounded-full bg-white px-1.5 py-0.5 text-[10px] font-bold text-brand">
                    {cart.itemCount}
                  </span>
                )}
              </span>
              <span className="hidden sm:inline">Giỏ hàng</span>
            </Link>
            {isAuthenticated && user ? (
              <div className="relative whitespace-nowrap">
                <button
                  className="flex max-w-[120px] items-center gap-1 truncate text-sm font-medium text-white hover:opacity-90"
                  onClick={() => setMenuOpen((v) => !v)}
                >
                  <span className="truncate">{user.fullName || user.email}</span>
                  <CaretDown size={12} weight="bold" />
                </button>
                {menuOpen && (
                  <div className="absolute right-0 z-50 mt-2 w-44 overflow-hidden rounded-md border border-border bg-card py-1 text-sm text-gray-800 shadow-lg">
                    <Link to="/profile" className="block px-4 py-2 hover:bg-gray-50">
                      Hồ sơ
                    </Link>
                    <Link to="/orders" className="block px-4 py-2 hover:bg-gray-50">
                      Đơn hàng
                    </Link>
                    <Link to="/profile/addresses" className="block px-4 py-2 hover:bg-gray-50">
                      Địa chỉ
                    </Link>
                    {isAdmin && (
                      <Link to="/admin/products" className="block px-4 py-2 hover:bg-gray-50">
                        Quản trị
                      </Link>
                    )}
                    <button
                      className="block w-full px-4 py-2 text-left text-red-600 hover:bg-gray-50"
                      onClick={() => {
                        logout()
                        setMenuOpen(false)
                      }}
                    >
                      Đăng xuất
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <Link to="/auth/login" className="text-sm font-medium text-white hover:opacity-90">
                Đăng nhập
              </Link>
            )}
          </div>
        </div>
        {/* Mobile: search xuống hàng riêng */}
        <div className="pb-2.5 md:hidden">{searchForm}</div>
      </div>
    </header>
  )
}
