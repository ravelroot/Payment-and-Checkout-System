import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Icon } from "../../components/Icon";
import { Modal } from "../../components/Modal";
import { Empty, Field, Page, PageHeader, Skeleton, StatusPill } from "../../components/Ui";
import { useToast } from "../../components/Toast";
import { Items, Payments, Progress, Shipping, Timeline, Totals } from "../../features/OrderParts";
import { put } from "../../lib/api";
import { dateTime, statusLabel } from "../../lib/format";
import type { OrderDetail, OrderStatus, PaymentOptions } from "../../lib/types";
import { useApi } from "../../lib/useApi";

const actionLabel: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "Voltar para aguardando",
  PAID: "Marcar como pago",
  PROCESSING: "Iniciar separação",
  SHIPPED: "Registrar envio",
  DELIVERED: "Confirmar entrega",
  CANCELED: "Cancelar pedido",
  REFUNDED: "Estornar pedido"
};

export function AdminOrderPage() {
  const { number } = useParams();
  const toast = useToast();
  const { data: order, setData, error, reload } = useApi<OrderDetail>(`/api/admin/orders/${number}`);
  const options = useApi<PaymentOptions>("/api/payments/options");
  const [target, setTarget] = useState<OrderStatus | null>(null);
  const [note, setNote] = useState("");
  const [tracking, setTracking] = useState("");
  const [busy, setBusy] = useState(false);

  if (error) {
    return (
      <Page>
        <div className="wrap">
          <Empty title="Pedido não encontrado" action={<Link className="btn ghost" to="/admin/pedidos">Voltar para pedidos</Link>} />
        </div>
      </Page>
    );
  }

  const open = (s: OrderStatus) => {
    setTarget(s);
    setNote("");
    setTracking(order?.trackingCode ?? "");
  };

  const apply = async () => {
    if (!target) return;
    if (target === "SHIPPED" && !tracking.trim()) return toast.show("Informe o código de rastreio.", "error");
    setBusy(true);
    try {
      setData(
        await put<OrderDetail>(`/api/admin/orders/${number}/status`, {
          status: target,
          note: note.trim() || null,
          trackingCode: target === "SHIPPED" ? tracking.trim() : null
        })
      );
      toast.show(`Pedido ${statusLabel[target].toLowerCase()}`);
      setTarget(null);
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const destructive = (s: OrderStatus) => s === "CANCELED" || s === "REFUNDED";

  return (
    <Page>
      <div className="wrap wide">
        <Link to="/admin/pedidos" className="back-link">
          <Icon name="arrowLeft" size={16} /> Pedidos
        </Link>
        {!order ? (
          <div className="stack">
            <Skeleton h={40} w={240} />
            <Skeleton h={260} r={20} />
          </div>
        ) : (
          <>
            <PageHeader
              title={<span className="num">Pedido {order.number}</span>}
              subtitle={`Feito em ${dateTime(order.placedAt)}`}
              actions={
                <>
                  <StatusPill status={order.status} label={order.statusLabel} />
                  {order.nextStatuses.map((s) => (
                    <button key={s} className={destructive(s) ? "btn quiet" : "btn"} onClick={() => open(s)}>
                      {actionLabel[s]}
                    </button>
                  ))}
                </>
              }
            />
            <div className="card pad">
              <Progress order={order} />
            </div>
            <div className="checkout-layout">
              <div className="stack lg">
                <section className="card pad">
                  <h3>Itens</h3>
                  <Items order={order} link={false} />
                </section>
                <section className="card pad">
                  <h3>Pagamentos</h3>
                  <Payments order={order} simulated={Boolean(options.data?.simulated)} onChange={() => reload()} />
                </section>
                <section className="card pad">
                  <h3>Histórico</h3>
                  <Timeline order={order} />
                </section>
              </div>
              <div className="stack">
                {order.customer && (
                  <section className="card pad">
                    <h3>Cliente</h3>
                    <div className="stack sm">
                      <strong>{order.customer.name}</strong>
                      <span className="muted small">{order.customer.email}</span>
                      {order.customer.phone && <span className="muted small num">{order.customer.phone}</span>}
                      <Link className="text-link small" to={`/admin/pedidos?q=${encodeURIComponent(order.customer.email)}`}>
                        Ver pedidos deste cliente
                      </Link>
                    </div>
                  </section>
                )}
                <section className="card pad">
                  <h3>Entrega</h3>
                  <Shipping order={order} />
                </section>
                <section className="card pad summary">
                  <h3>Resumo</h3>
                  <Totals order={order} />
                </section>
              </div>
            </div>
          </>
        )}
      </div>
      <Modal
        open={target !== null}
        onClose={() => setTarget(null)}
        title={target ? actionLabel[target] : ""}
        actions={
          <>
            <button className="btn ghost" onClick={() => setTarget(null)}>
              Voltar
            </button>
            <button className={target && destructive(target) ? "btn danger" : "btn"} onClick={apply} disabled={busy}>
              {busy ? <span className="spinner" /> : target ? actionLabel[target] : ""}
            </button>
          </>
        }
      >
        <div className="stack">
          {target && destructive(target) && (
            <p className="muted">
              Os itens voltam para o estoque{order?.paidAt ? " e o valor pago é estornado ao cliente" : ""}. Essa ação não pode ser desfeita.
            </p>
          )}
          {target === "SHIPPED" && (
            <Field label="Código de rastreio">
              <input className="input num" value={tracking} maxLength={40} onChange={(e) => setTracking(e.target.value.toUpperCase())} placeholder="AA123456789BR" />
            </Field>
          )}
          <Field label="Observação (opcional)" hint="Aparece no histórico do pedido para o cliente.">
            <textarea className="input" rows={2} maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} />
          </Field>
        </div>
      </Modal>
    </Page>
  );
}
