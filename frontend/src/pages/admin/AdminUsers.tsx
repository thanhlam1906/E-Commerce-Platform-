import { useState } from 'react'
import { useAdminUsers, useUpdateUserRole, useUpdateUserStatus } from '../../hooks/useAdmin'
import { useAuth } from '../../hooks/useAuth'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState } from '../../components/ui/EmptyState'
import { useToast } from '../../components/ui/Toast'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { formatDateTime } from '../../lib/format'
import type { Role, User } from '../../types'

const ROLES: Role[] = ['CUSTOMER', 'PRODUCT_ADMIN', 'ORDER_ADMIN', 'SUPER_ADMIN']
const selectCls = 'rounded-sm border border-border px-2 py-1 text-sm outline-none focus:border-brand focus:ring-2 focus:ring-brand/30'

export default function AdminUsers() {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useAdminUsers(page)
  const updateRole = useUpdateUserRole()
  const updateStatus = useUpdateUserStatus()
  const { user: me } = useAuth()
  const { toast } = useToast()
  const [toggle, setToggle] = useState<User | null>(null)

  const users = data?.content ?? []

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold">Người dùng</h1>
      {isLoading ? (
        <div className="py-10 text-center text-gray-500">Đang tải…</div>
      ) : users.length === 0 ? (
        <EmptyState message="Không có người dùng" />
      ) : (
        <div className="overflow-x-auto rounded-md border border-border bg-card">
          <table className="w-full text-sm">
            <thead className="border-b text-left text-gray-500">
              <tr>
                <th className="px-4 py-2">Người dùng</th>
                <th className="px-4 py-2">Vai trò</th>
                <th className="px-4 py-2">Trạng thái</th>
                <th className="px-4 py-2">Ngày tạo</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id} className="border-b last:border-0">
                  <td className="px-4 py-2">
                    <p className="font-medium">{u.fullName || u.email}</p>
                    <p className="text-xs text-gray-500">{u.email}</p>
                  </td>
                  <td className="px-4 py-2">
                    <select
                      className={selectCls}
                      value={u.role}
                      disabled={u.id === me?.id}
                      onChange={(e) =>
                        updateRole.mutate(
                          { id: u.id, role: e.target.value as Role },
                          { onSuccess: () => toast(`Đã đổi vai trò → ${e.target.value}`) }
                        )
                      }
                    >
                      {ROLES.map((r) => (
                        <option key={r} value={r}>
                          {r}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="px-4 py-2">
                    <button
                      className={`rounded px-2 py-1 text-xs font-semibold ${u.active ? 'bg-green-100 text-green-700' : 'bg-gray-200 text-gray-600'}`}
                      disabled={u.id === me?.id}
                      onClick={() => setToggle(u)}
                    >
                      {u.active ? 'Hoạt động' : 'Đã khóa'}
                    </button>
                  </td>
                  <td className="px-4 py-2 text-gray-500">{formatDateTime(u.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="mt-4">
        <Pagination page={page} totalPages={data?.totalPages ?? 0} onPage={setPage} />
      </div>
      <ConfirmDialog
        open={!!toggle}
        title={toggle?.active ? 'Khóa người dùng' : 'Mở khóa người dùng'}
        message={`${toggle?.active ? 'Khóa' : 'Mở khóa'} tài khoản ${toggle?.email}?`}
        confirmLabel={toggle?.active ? 'Khóa' : 'Mở khóa'}
        onClose={() => setToggle(null)}
        onConfirm={() =>
          toggle &&
          updateStatus.mutate(
            { id: toggle.id, active: !toggle.active },
            { onSuccess: () => toast('Đã cập nhật trạng thái') }
          )
        }
      />
    </div>
  )
}
