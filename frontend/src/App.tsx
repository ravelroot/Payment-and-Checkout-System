import { MotionConfig } from "motion/react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth, RequireRole } from "./auth/Guards";
import { SessionWatcher } from "./auth/SessionWatcher";
import { CartProvider } from "./cart/CartContext";
import { ToastProvider } from "./components/Toast";
import { AdminLayout } from "./layouts/AdminLayout";
import { StoreLayout } from "./layouts/StoreLayout";
import { AccountPage } from "./pages/AccountPage";
import { AdminCategoriesPage } from "./pages/admin/AdminCategoriesPage";
import { AdminCouponsPage } from "./pages/admin/AdminCouponsPage";
import { AdminOrderPage } from "./pages/admin/AdminOrderPage";
import { AdminOrdersPage } from "./pages/admin/AdminOrdersPage";
import { AdminProductPage } from "./pages/admin/AdminProductPage";
import { AdminProductsPage } from "./pages/admin/AdminProductsPage";
import { AdminUsersPage } from "./pages/admin/AdminUsersPage";
import { DashboardPage } from "./pages/admin/DashboardPage";
import { CartPage } from "./pages/CartPage";
import { CheckoutPage } from "./pages/CheckoutPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { OrderDetailPage } from "./pages/OrderDetailPage";
import { OrdersPage } from "./pages/OrdersPage";
import { ProductPage } from "./pages/ProductPage";
import { StorePage } from "./pages/StorePage";

export function App() {
  return (
    <BrowserRouter>
      <MotionConfig reducedMotion="user">
        <ToastProvider>
          <AuthProvider>
            <CartProvider>
              <Routes>
                <Route path="/entrar" element={<LoginPage />} />
                <Route element={<RequireAuth />}>
                  <Route element={<StoreLayout />}>
                    <Route index element={<StorePage />} />
                    <Route path="produto/:slug" element={<ProductPage />} />
                    <Route path="carrinho" element={<CartPage />} />
                    <Route path="checkout" element={<CheckoutPage />} />
                    <Route path="pedidos" element={<OrdersPage />} />
                    <Route path="pedidos/:number" element={<OrderDetailPage />} />
                    <Route path="conta" element={<AccountPage />} />
                    <Route path="*" element={<NotFoundPage />} />
                  </Route>
                  <Route element={<RequireRole roles={["ADMIN", "STAFF"]} />}>
                    <Route path="admin" element={<AdminLayout />}>
                      <Route index element={<DashboardPage />} />
                      <Route path="pedidos" element={<AdminOrdersPage />} />
                      <Route path="pedidos/:number" element={<AdminOrderPage />} />
                      <Route path="produtos" element={<AdminProductsPage />} />
                      <Route path="produtos/:id" element={<AdminProductPage />} />
                      <Route path="categorias" element={<AdminCategoriesPage />} />
                      <Route path="cupons" element={<AdminCouponsPage />} />
                      <Route element={<RequireRole roles={["ADMIN"]} />}>
                        <Route path="usuarios" element={<AdminUsersPage />} />
                      </Route>
                      <Route path="*" element={<NotFoundPage />} />
                    </Route>
                  </Route>
                </Route>
              </Routes>
              <SessionWatcher />
            </CartProvider>
          </AuthProvider>
        </ToastProvider>
      </MotionConfig>
    </BrowserRouter>
  );
}
