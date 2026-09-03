import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import type { Role } from '../../types'

const can = {
  products: (r: Role) => r === 'PRODUCT_ADMIN' || r === 'SUPER_ADMIN',
  orders: (r: Role) => r === 'ORDER_ADMIN' || r === 'SUPER_ADMIN',
  users: (r: Role) => r === 'SUPER_ADMIN',
}

export default function AdminLayout() {
  const { user } = useAuth()
  if (!user) return null
  const role = user.role
  const items = [
    can.products(role) && { to: '/admin/products', label: 'Sản phẩm' },
    can.products(role) && { to: '/admin/categories', label: 'Danh mục' },
    can.orders(role) && { to: '/admin/orders', label: 'Đơn hàng' },
    can.orders(role) && { to: '/admin/inventory', label: 'Tồn kho' },
    can.users(role) && { to: '/admin/users', label: 'Người dùng' },
  ].filter(Boolean) as { to: string; label: string }[]

  return (
    <div className="grid gap-4 lg:grid-cols-[180px_1fr]">
      <nav className="flex gap-2 lg:flex-col">
        {items.map((it) => (
          <NavLink
            key={it.to}
            to={it.to}
            className={({ isActive }) =>
              `rounded-sm px-3 py-2 text-sm ${isActive ? 'bg-brand text-white' : 'bg-white hover:text-brand'}`
            }
          >
            {it.label}
          </NavLink>
        ))}
      </nav>
      <div className="min-w-0">
        <Outlet />
      </div>
    </div>
  )
}
