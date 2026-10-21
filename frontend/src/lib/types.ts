export type Role = "ADMIN" | "STAFF" | "CUSTOMER";

export interface Me {
  id: number;
  fullName: string;
  email: string;
  role: Role;
}

export interface SessionInfo {
  token: string;
  user: Me;
  idleExpiresAt: string;
  expiresAt: string;
  idleMinutes: number;
  maxMinutes: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  pages: number;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  sortOrder: number;
  active: boolean;
  products: number;
}

export interface ProductCard {
  id: number;
  sku: string;
  name: string;
  slug: string;
  price: number;
  compareAtPrice: number | null;
  imageUrl: string | null;
  stock: number;
  featured: boolean;
  category: string | null;
  categorySlug: string | null;
}

export interface ProductDetail extends ProductCard {
  description: string | null;
  lowStockThreshold: number;
  weightGrams: number;
  active: boolean;
  categoryId: number | null;
  version: number | null;
  createdAt: string;
}

export interface CartLine {
  productId: number;
  slug: string;
  sku: string;
  name: string;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  stock: number;
  available: boolean;
}

export interface CartView {
  items: CartLine[];
  count: number;
  subtotal: number;
  shipping: number;
  discount: number;
  total: number;
  couponCode: string | null;
  couponMessage: string | null;
  freeShippingAbove: number | null;
}

export type OrderStatus = "PENDING_PAYMENT" | "PAID" | "PROCESSING" | "SHIPPED" | "DELIVERED" | "CANCELED" | "REFUNDED";
export type PaymentMethod = "CARD" | "PIX" | "BOLETO" | "CHECKOUT";
export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "CANCELED" | "REFUNDED";

export interface OrderSummary {
  id: number;
  number: string;
  status: OrderStatus;
  statusLabel: string;
  total: number;
  items: number;
  placedAt: string;
  customerId: number;
  customerName: string | null;
  customerEmail: string | null;
}

export interface PaymentView {
  id: number;
  provider: "SIMULATED" | "STRIPE";
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  installments: number;
  cardBrand: string | null;
  cardLast4: string | null;
  pixCode: string | null;
  boletoLine: string | null;
  checkoutUrl: string | null;
  failureReason: string | null;
  refundedAmount: number;
  paidAt: string | null;
  expiresAt: string | null;
  createdAt: string;
}

export interface OrderDetail {
  id: number;
  number: string;
  status: OrderStatus;
  statusLabel: string;
  subtotal: number;
  discount: number;
  shipping: number;
  total: number;
  couponCode: string | null;
  recipient: string;
  zipCode: string;
  street: string;
  streetNumber: string;
  complement: string | null;
  district: string;
  city: string;
  state: string;
  trackingCode: string | null;
  notes: string | null;
  placedAt: string;
  paidAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  canceledAt: string | null;
  paymentDeadline: string | null;
  items: {
    productId: number | null;
    sku: string;
    name: string;
    unitPrice: number;
    quantity: number;
    lineTotal: number;
    imageUrl: string | null;
    slug: string | null;
  }[];
  history: { from: OrderStatus | null; to: OrderStatus; toLabel: string; note: string | null; by: string; at: string }[];
  payments: PaymentView[];
  customer: { id: number; name: string; email: string; phone: string | null } | null;
  canPay: boolean;
  canCancel: boolean;
  nextStatuses: OrderStatus[];
}

export interface PaymentOptions {
  provider: "SIMULATED" | "STRIPE";
  methods: PaymentMethod[];
  maxInstallments: number;
  simulated: boolean;
  testCards: { number: string; result: string }[];
}

export interface Address {
  id: number;
  label: string;
  recipient: string;
  zipCode: string;
  street: string;
  number: string;
  complement: string | null;
  district: string;
  city: string;
  state: string;
  isDefault: boolean;
}

export interface Profile {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  phone: string | null;
  document: string | null;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AdminUser {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  active: boolean;
  phone: string | null;
  document: string | null;
  createdAt: string;
  lastLoginAt: string | null;
  orders: number;
}

export interface Coupon {
  id: number;
  code: string;
  description: string | null;
  type: "PERCENT" | "FIXED";
  value: number;
  minSubtotal: number | null;
  maxUses: number | null;
  usedCount: number;
  startsAt: string | null;
  endsAt: string | null;
  active: boolean;
}

export interface Movement {
  id: number;
  type: "IN" | "OUT" | "SALE" | "RETURN" | "ADJUST";
  quantity: number;
  stockAfter: number;
  reason: string | null;
  orderId: number | null;
  createdAt: string;
}

export interface Dashboard {
  days: number;
  kpis: {
    revenue: number;
    revenueChange: number | null;
    orders: number;
    averageTicket: number;
    newCustomers: number;
    toShip: number;
    awaitingPayment: number;
    lowStock: number;
  };
  sales: { date: string; revenue: number; orders: number }[];
  statuses: { status: OrderStatus; label: string; count: number }[];
  topProducts: { name: string; quantity: number; revenue: number }[];
  lowStock: { id: number; name: string; sku: string; stock: number; threshold: number }[];
  recentOrders: OrderSummary[];
}
