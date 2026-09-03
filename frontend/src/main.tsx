import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'
import { ToastProvider, useToast } from './components/ui/Toast.tsx'
import { AuthProvider } from './hooks/useAuth.tsx'

// MutationCache.onError cần toast — QueryClient phải tạo trong cây dưới ToastProvider.
function Providers({ children }: { children: React.ReactNode }) {
  const { toast } = useToast()
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
        },
        mutationCache: new MutationCache({
          // Lỗi mutation mọi nơi (order/product/category/inventory/user…) đều hiện toast đỏ.
          onError: (err) => toast(err.message || 'Thao tác thất bại', 'error'),
        }),
      }),
  )
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ToastProvider>
      <Providers>
        <AuthProvider>
          <App />
        </AuthProvider>
      </Providers>
    </ToastProvider>
  </StrictMode>,
)
