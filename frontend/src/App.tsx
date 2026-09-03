import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import AdminLayout from './components/layout/AdminLayout'
import { RequireAuth, RequireRole } from './guards'
import AuthCallback from './pages/AuthCallback'
import Home from './pages/Home'
import Profile from './pages/Profile'
import Addresses from './pages/profile/Addresses'
import ProductList from './pages/product/ProductList'
import ProductDetail from './pages/product/ProductDetail'
import Cart from './pages/cart/Cart'
import Checkout from './pages/checkout/Checkout'
import PaymentPending from './pages/checkout/PaymentPending'
import Orders from './pages/order/Orders'
import OrderDetail from './pages/order/OrderDetail'
import AdminProducts from './pages/admin/AdminProducts'
import ProductForm from './pages/admin/ProductForm'
import AdminCategories from './pages/admin/AdminCategories'
import AdminOrders from './pages/admin/AdminOrders'
import AdminInventory from './pages/admin/AdminInventory'
import AdminUsers from './pages/admin/AdminUsers'
import ForgotPassword from './pages/auth/ForgotPassword'
import Login from './pages/auth/Login'
import Register from './pages/auth/Register'
import ResetPassword from './pages/auth/ResetPassword'
import VerifyEmail from './pages/auth/VerifyEmail'
import NotFound from './pages/NotFound'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Home />} />
          <Route path="/products" element={<ProductList />} />
          <Route path="/products/:id" element={<ProductDetail />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/auth/login" element={<Login />} />
          <Route path="/auth/register" element={<Register />} />
          <Route path="/auth/forgot-password" element={<ForgotPassword />} />
          <Route path="/auth/reset-password" element={<ResetPassword />} />
          <Route path="/auth/verify-email" element={<VerifyEmail />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route
            path="/checkout"
            element={
              <RequireAuth>
                <Checkout />
              </RequireAuth>
            }
          />
          <Route
            path="/checkout/:orderId/pay"
            element={
              <RequireAuth>
                <PaymentPending />
              </RequireAuth>
            }
          />
          <Route
            path="/orders"
            element={
              <RequireAuth>
                <Orders />
              </RequireAuth>
            }
          />
          <Route
            path="/orders/:id"
            element={
              <RequireAuth>
                <OrderDetail />
              </RequireAuth>
            }
          />
          <Route
            path="/profile"
            element={
              <RequireAuth>
                <Profile />
              </RequireAuth>
            }
          />
          <Route
            path="/profile/addresses"
            element={
              <RequireAuth>
                <Addresses />
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAuth>
                <RequireRole roles={['PRODUCT_ADMIN', 'ORDER_ADMIN', 'SUPER_ADMIN']}>
                  <AdminLayout />
                </RequireRole>
              </RequireAuth>
            }
          >
            <Route
              path="products"
              element={
                <RequireRole roles={['PRODUCT_ADMIN', 'SUPER_ADMIN']}>
                  <AdminProducts />
                </RequireRole>
              }
            />
            <Route
              path="products/new"
              element={
                <RequireRole roles={['PRODUCT_ADMIN', 'SUPER_ADMIN']}>
                  <ProductForm />
                </RequireRole>
              }
            />
            <Route
              path="products/:id/edit"
              element={
                <RequireRole roles={['PRODUCT_ADMIN', 'SUPER_ADMIN']}>
                  <ProductForm />
                </RequireRole>
              }
            />
            <Route
              path="categories"
              element={
                <RequireRole roles={['PRODUCT_ADMIN', 'SUPER_ADMIN']}>
                  <AdminCategories />
                </RequireRole>
              }
            />
            <Route
              path="orders"
              element={
                <RequireRole roles={['ORDER_ADMIN', 'SUPER_ADMIN']}>
                  <AdminOrders />
                </RequireRole>
              }
            />
            <Route
              path="inventory"
              element={
                <RequireRole roles={['ORDER_ADMIN', 'SUPER_ADMIN']}>
                  <AdminInventory />
                </RequireRole>
              }
            />
            <Route
              path="users"
              element={
                <RequireRole roles={['SUPER_ADMIN']}>
                  <AdminUsers />
                </RequireRole>
              }
            />
          </Route>
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
