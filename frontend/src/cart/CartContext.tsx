import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../components/Toast";
import { del, get, post, put, qs } from "../lib/api";
import type { CartView } from "../lib/types";

interface CartState {
  cart: CartView | null;
  coupon: string | null;
  setCoupon: (code: string | null) => Promise<CartView | null>;
  refresh: () => Promise<CartView | null>;
  add: (productId: number, quantity?: number, name?: string) => Promise<void>;
  setQuantity: (productId: number, quantity: number) => Promise<void>;
  remove: (productId: number) => Promise<void>;
  open: boolean;
  setOpen: (v: boolean) => void;
  bump: number;
}

const CartContext = createContext<CartState | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const toast = useToast();
  const [cart, setCart] = useState<CartView | null>(null);
  const [coupon, setCouponState] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [bump, setBump] = useState(0);

  const query = useCallback((c: string | null) => qs({ coupon: c }), []);

  const refresh = useCallback(async () => {
    if (!user) return null;
    const v = await get<CartView>("/api/cart" + query(coupon));
    setCart(v);
    return v;
  }, [user, coupon, query]);

  useEffect(() => {
    if (user) refresh().catch(() => {});
    else {
      setCart(null);
      setCouponState(null);
      setOpen(false);
    }
  }, [user]);

  const setCoupon = useCallback(
    async (code: string | null) => {
      const v = await get<CartView>("/api/cart" + query(code));
      setCart(v);
      setCouponState(v.couponCode ? v.couponCode : null);
      return v;
    },
    [query]
  );

  const add = useCallback(
    async (productId: number, quantity = 1, name?: string) => {
      try {
        const v = await post<CartView>("/api/cart/items" + query(coupon), { productId, quantity });
        setCart(v);
        setBump((b) => b + 1);
        toast.show(name ? `${name} foi para o carrinho` : "Adicionado ao carrinho");
      } catch (e) {
        toast.error(e);
      }
    },
    [coupon, query, toast]
  );

  const setQuantity = useCallback(
    async (productId: number, quantity: number) => {
      try {
        setCart(await put<CartView>(`/api/cart/items/${productId}` + query(coupon), { quantity }));
      } catch (e) {
        toast.error(e);
      }
    },
    [coupon, query, toast]
  );

  const remove = useCallback(
    async (productId: number) => {
      try {
        setCart(await del<CartView>(`/api/cart/items/${productId}` + query(coupon)));
      } catch (e) {
        toast.error(e);
      }
    },
    [coupon, query, toast]
  );

  const value = useMemo(
    () => ({ cart, coupon, setCoupon, refresh, add, setQuantity, remove, open, setOpen, bump }),
    [cart, coupon, setCoupon, refresh, add, setQuantity, remove, open, bump]
  );
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("CartProvider ausente");
  return ctx;
}
