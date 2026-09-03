/** Guest session id — gửi qua header X-Session-Id cho giỏ hàng ẩn danh. */
const KEY = 'guest_session_id'

export function getSessionId(): string {
  let id = localStorage.getItem(KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(KEY, id)
  }
  return id
}
