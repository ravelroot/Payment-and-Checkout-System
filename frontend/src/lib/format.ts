const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const zone = "America/Sao_Paulo";
const dateFmt = new Intl.DateTimeFormat("pt-BR", { timeZone: zone, day: "2-digit", month: "2-digit", year: "numeric" });
const dateTimeFmt = new Intl.DateTimeFormat("pt-BR", {
  timeZone: zone,
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit"
});
const shortFmt = new Intl.DateTimeFormat("pt-BR", { timeZone: zone, day: "2-digit", month: "short" });

export const money = (v: number | null | undefined) => brl.format(Number(v ?? 0));
export const date = (v: string | null | undefined) => (v ? dateFmt.format(new Date(v)) : "—");
export const dateTime = (v: string | null | undefined) => (v ? dateTimeFmt.format(new Date(v)).replace(",", " ·") : "—");
export const shortDate = (v: string) => shortFmt.format(new Date(v.length === 10 ? v + "T12:00:00Z" : v)).replace(".", "");

export function relative(v: string) {
  const diff = Date.now() - new Date(v).getTime();
  const m = Math.round(diff / 60000);
  if (m < 1) return "agora";
  if (m < 60) return `há ${m} min`;
  const h = Math.round(m / 60);
  if (h < 24) return `há ${h} h`;
  const d = Math.round(h / 24);
  return d === 1 ? "ontem" : `há ${d} dias`;
}

export function clock(ms: number) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  const mm = Math.floor(s / 60);
  const ss = s % 60;
  return `${mm}:${String(ss).padStart(2, "0")}`;
}

export const digits = (v: string) => v.replace(/\D/g, "");

export function maskCep(v: string) {
  const d = digits(v).slice(0, 8);
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
}

export function maskCard(v: string) {
  return digits(v).slice(0, 19).replace(/(\d{4})(?=\d)/g, "$1 ");
}

export function maskExpiry(v: string) {
  const d = digits(v).slice(0, 4);
  return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
}

export function maskPhone(v: string) {
  const d = digits(v).slice(0, 11);
  if (d.length <= 2) return d;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

export const roleLabel = { ADMIN: "Administrador", STAFF: "Equipe", CUSTOMER: "Cliente" } as const;

export const methodLabel = { CARD: "Cartão de crédito", PIX: "PIX", BOLETO: "Boleto", CHECKOUT: "Stripe Checkout" } as const;

export const paymentStatusLabel = {
  PENDING: "Pendente",
  PAID: "Pago",
  FAILED: "Recusado",
  CANCELED: "Cancelado",
  REFUNDED: "Estornado"
} as const;

export const statusLabel = {
  PENDING_PAYMENT: "Aguardando pagamento",
  PAID: "Pago",
  PROCESSING: "Em separação",
  SHIPPED: "Enviado",
  DELIVERED: "Entregue",
  CANCELED: "Cancelado",
  REFUNDED: "Estornado"
} as const;

export function statusTone(s: string) {
  switch (s) {
    case "PAID":
    case "DELIVERED":
      return "ok";
    case "PENDING_PAYMENT":
    case "PENDING":
      return "warn";
    case "CANCELED":
    case "REFUNDED":
    case "FAILED":
      return "danger";
    case "SHIPPED":
      return "ink";
    default:
      return "";
  }
}
