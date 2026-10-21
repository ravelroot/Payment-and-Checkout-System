import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "../components/Icon";
import { ProductImage, StatusPill } from "../components/Ui";
import { useToast } from "../components/Toast";
import { post } from "../lib/api";
import { dateTime, methodLabel, money, paymentStatusLabel } from "../lib/format";
import type { OrderDetail, OrderStatus, PaymentView } from "../lib/types";

const flow: OrderStatus[] = ["PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED"];
const flowLabel: Record<string, string> = {
  PENDING_PAYMENT: "Pedido feito",
  PAID: "Pago",
  PROCESSING: "Em separação",
  SHIPPED: "Enviado",
  DELIVERED: "Entregue"
};

export function Progress({ order }: { order: OrderDetail }) {
  const ended = order.status === "CANCELED" || order.status === "REFUNDED";
  const reached = new Set(order.history.map((h) => h.to));
  reached.add("PENDING_PAYMENT");
  const current = ended ? -1 : flow.indexOf(order.status);
  return (
    <ol className={`progress ${ended ? "ended" : ""}`} aria-label="Andamento do pedido">
      {flow.map((s, i) => {
        const done = ended ? reached.has(s) : i <= current;
        return (
          <li key={s} className={done ? "done" : ""} aria-current={i === current ? "step" : undefined}>
            <span className="progress-dot">
              {done && <Icon name="check" size={12} />}
            </span>
            <span className="small">{flowLabel[s]}</span>
          </li>
        );
      })}
    </ol>
  );
}

export function Items({ order, link = true }: { order: OrderDetail; link?: boolean }) {
  return (
    <ul className="mini-lines">
      {order.items.map((it) => (
        <li key={it.sku}>
          <ProductImage src={it.imageUrl} alt={it.name} className="thumb" />
          <span>
            {link && it.slug ? <Link to={`/produto/${it.slug}`}>{it.name}</Link> : it.name}
            <span className="muted small">
              {" "}
              × {it.quantity} · <span className="num">{money(it.unitPrice)}</span>
            </span>
          </span>
          <span className="num">{money(it.lineTotal)}</span>
        </li>
      ))}
    </ul>
  );
}

export function Totals({ order }: { order: OrderDetail }) {
  return (
    <>
      <dl className="kv">
        <dt>Subtotal</dt>
        <dd className="num">{money(order.subtotal)}</dd>
        {order.discount > 0 && (
          <>
            <dt>Desconto{order.couponCode ? ` (${order.couponCode})` : ""}</dt>
            <dd className="num">− {money(order.discount)}</dd>
          </>
        )}
        <dt>Frete</dt>
        <dd className="num">{order.shipping === 0 ? "Grátis" : money(order.shipping)}</dd>
      </dl>
      <hr className="divider" />
      <div className="total-line">
        <span>Total</span>
        <strong className="num">{money(order.total)}</strong>
      </div>
    </>
  );
}

export function Shipping({ order }: { order: OrderDetail }) {
  return (
    <div className="stack sm">
      <strong>{order.recipient}</strong>
      <span className="address-text">
        {order.street}, {order.streetNumber}
        {order.complement ? ` · ${order.complement}` : ""}
        <br />
        {order.district} · {order.city}/{order.state} · <span className="num">{order.zipCode}</span>
      </span>
      {order.trackingCode && (
        <span className="chip">
          <Icon name="truck" size={14} /> Rastreio <span className="num">{order.trackingCode}</span>
        </span>
      )}
      {order.notes && <span className="muted small">“{order.notes}”</span>}
    </div>
  );
}

export function Timeline({ order }: { order: OrderDetail }) {
  return (
    <ol className="timeline">
      {[...order.history].reverse().map((h, i) => (
        <li key={i}>
          <span className="timeline-dot" />
          <div>
            <strong>{h.toLabel}</strong>
            <span className="muted small">
              {dateTime(h.at)} · {h.by}
            </span>
            {h.note && <span className="small">{h.note}</span>}
          </div>
        </li>
      ))}
    </ol>
  );
}

function CopyLine({ value, label }: { value: string; label: string }) {
  const toast = useToast();
  return (
    <div className="copy-line">
      <code className="num">{value}</code>
      <button
        className="btn ghost sm"
        onClick={async () => {
          try {
            await navigator.clipboard.writeText(value);
            toast.show(`${label} copiado`);
          } catch {
            toast.show("Não foi possível copiar. Selecione o texto e copie manualmente.", "error");
          }
        }}
      >
        <Icon name="copy" size={15} /> Copiar
      </button>
    </div>
  );
}

export function Payments({ order, simulated, onChange }: { order: OrderDetail; simulated: boolean; onChange: (o: PaymentView) => void }) {
  const toast = useToast();
  const [busy, setBusy] = useState<number | null>(null);
  if (order.payments.length === 0) return <p className="muted">Nenhum pagamento registrado.</p>;
  const simulate = async (p: PaymentView, approve: boolean) => {
    setBusy(p.id);
    try {
      const v = await post<PaymentView>(`/api/payments/${p.id}/simulate`, { approve });
      toast.show(approve ? "Pagamento confirmado" : "Pagamento marcado como recusado");
      onChange(v);
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(null);
    }
  };
  return (
    <ul className="payments">
      {order.payments.map((p) => (
        <li key={p.id}>
          <div className="payment-head">
            <span className="payment-method">
              <Icon name={p.method === "CARD" ? "card" : p.method === "PIX" ? "pix" : p.method === "BOLETO" ? "barcode" : "external"} size={18} />
              {methodLabel[p.method]}
              {p.cardBrand && (
                <span className="muted small">
                  {" "}
                  {p.cardBrand} •••• {p.cardLast4}
                  {p.installments > 1 ? ` · ${p.installments}×` : ""}
                </span>
              )}
            </span>
            <StatusPill status={p.status} label={paymentStatusLabel[p.status]} />
          </div>
          <span className="muted small">
            <span className="num">{money(p.amount)}</span> · {dateTime(p.createdAt)}
            {p.refundedAmount > 0 && (
              <>
                {" "}
                · estornado <span className="num">{money(p.refundedAmount)}</span>
              </>
            )}
          </span>
          {p.failureReason && <span className="field-error">{p.failureReason}</span>}
          {p.status === "PENDING" && p.pixCode && <CopyLine value={p.pixCode} label="Código PIX" />}
          {p.status === "PENDING" && p.boletoLine && <CopyLine value={p.boletoLine} label="Linha digitável" />}
          {p.status === "PENDING" && p.expiresAt && <span className="muted small">Válido até {dateTime(p.expiresAt)}</span>}
          {p.status === "PENDING" && p.checkoutUrl && (
            <a className="btn ghost sm" href={p.checkoutUrl}>
              Abrir página de pagamento <Icon name="external" size={14} />
            </a>
          )}
          {p.status === "PENDING" && simulated && p.provider === "SIMULATED" && (
            <div className="row">
              <button className="btn sm" disabled={busy === p.id} onClick={() => simulate(p, true)}>
                Simular pagamento
              </button>
              <button className="btn quiet sm" disabled={busy === p.id} onClick={() => simulate(p, false)}>
                Simular recusa
              </button>
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
