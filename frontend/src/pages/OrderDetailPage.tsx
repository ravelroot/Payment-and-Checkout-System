import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Icon } from "../components/Icon";
import { Confirm, Modal } from "../components/Modal";
import { Empty, Page, PageHeader, Skeleton, StatusPill } from "../components/Ui";
import { useToast } from "../components/Toast";
import { Items, Payments, Progress, Shipping, Timeline, Totals } from "../features/OrderParts";
import { initialPayment, PaymentForm, paymentBody, paymentProblem, type PaymentDraft } from "../features/PaymentForm";
import { post } from "../lib/api";
import { clock, dateTime } from "../lib/format";
import type { OrderDetail, PaymentOptions } from "../lib/types";
import { useApi } from "../lib/useApi";

export function OrderDetailPage() {
  const { number } = useParams();
  const toast = useToast();
  const { data: order, setData, error, reload } = useApi<OrderDetail>(`/api/orders/${number}`);
  const options = useApi<PaymentOptions>("/api/payments/options");
  const [cancelOpen, setCancelOpen] = useState(false);
  const [payOpen, setPayOpen] = useState(false);
  const [payment, setPayment] = useState<PaymentDraft>(initialPayment(null));
  const [busy, setBusy] = useState(false);
  const left = useCountdown(order?.paymentDeadline ?? null);

  useEffect(() => {
    if (options.data) setPayment(initialPayment(options.data));
  }, [options.data]);

  if (error) {
    return (
      <Page>
        <div className="wrap narrow">
          <Empty title="Pedido não encontrado" action={<Link className="btn ghost" to="/pedidos">Ver meus pedidos</Link>} />
        </div>
      </Page>
    );
  }

  const cancel = async () => {
    setBusy(true);
    try {
      setData(await post<OrderDetail>(`/api/orders/${number}/cancel`));
      toast.show("Pedido cancelado");
      setCancelOpen(false);
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const pay = async () => {
    const problem = paymentProblem(payment);
    if (problem) return toast.show(problem, "error");
    setBusy(true);
    try {
      const v = await post<OrderDetail>(`/api/orders/${number}/payments`, paymentBody(payment));
      const last = v.payments[0];
      if (last?.checkoutUrl) {
        window.location.assign(last.checkoutUrl);
        return;
      }
      setData(v);
      setPayOpen(false);
      if (last?.status === "FAILED") toast.show(`Pagamento recusado: ${last.failureReason ?? "tente outro cartão"}`, "error");
      else toast.show(last?.status === "PAID" ? "Pagamento aprovado" : "Pagamento gerado");
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Page>
      <div className="wrap">
        <Link to="/pedidos" className="back-link">
          <Icon name="arrowLeft" size={16} /> Meus pedidos
        </Link>
        {!order ? (
          <div className="stack">
            <Skeleton h={40} w={240} />
            <Skeleton h={120} r={20} />
            <Skeleton h={220} r={20} />
          </div>
        ) : (
          <>
            <PageHeader
              title={<span className="num">Pedido {order.number}</span>}
              subtitle={`Feito em ${dateTime(order.placedAt)}`}
              actions={
                <>
                  <StatusPill status={order.status} label={order.statusLabel} />
                  {order.canCancel && (
                    <button className="btn quiet" onClick={() => setCancelOpen(true)}>
                      Cancelar pedido
                    </button>
                  )}
                </>
              }
            />
            {order.canPay && (
              <div className="notice warn">
                <Icon name="clock" size={18} />
                <div className="grow">
                  Aguardando pagamento.
                  {left !== null && left > 0 && (
                    <>
                      {" "}
                      O pedido é cancelado em <strong className="num">{clock(left)}</strong> se não for pago.
                    </>
                  )}
                </div>
                <button className="btn sm" onClick={() => setPayOpen(true)}>
                  Pagar agora
                </button>
              </div>
            )}
            <div className="card pad">
              <Progress order={order} />
            </div>
            <div className="checkout-layout">
              <div className="stack lg">
                <section className="card pad">
                  <h3>Itens</h3>
                  <Items order={order} />
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
      <Confirm
        open={cancelOpen}
        title="Cancelar este pedido?"
        message={order?.status === "PAID" ? "O valor pago volta para você pelo mesmo meio de pagamento." : "Os itens voltam para o estoque e o pedido não pode ser reaberto."}
        confirmText="Cancelar pedido"
        danger
        busy={busy}
        onConfirm={cancel}
        onClose={() => setCancelOpen(false)}
      />
      <Modal
        open={payOpen}
        onClose={() => setPayOpen(false)}
        title="Pagar pedido"
        width={560}
        actions={
          <>
            <button className="btn ghost" onClick={() => setPayOpen(false)}>
              Voltar
            </button>
            <button className="btn" onClick={pay} disabled={busy}>
              {busy ? <span className="spinner" /> : "Pagar"}
            </button>
          </>
        }
      >
        {options.data && order && <PaymentForm options={options.data} value={payment} onChange={setPayment} total={order.total} />}
      </Modal>
    </Page>
  );
}

function useCountdown(deadline: string | null) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!deadline) return;
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [deadline]);
  return deadline ? new Date(deadline).getTime() - now : null;
}
