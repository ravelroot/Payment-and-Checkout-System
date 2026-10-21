import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import { Icon } from "../components/Icon";
import { Field, Page, PageHeader, ProductImage, Skeleton } from "../components/Ui";
import { useToast } from "../components/Toast";
import { AddressFields, AddressText, emptyAddress, fieldErrors, type AddressInput } from "../features/AddressForm";
import { initialPayment, PaymentForm, paymentBody, paymentProblem, type PaymentDraft } from "../features/PaymentForm";
import { post } from "../lib/api";
import { money } from "../lib/format";
import type { Address, OrderDetail, PaymentOptions } from "../lib/types";
import { useApi } from "../lib/useApi";
import { Summary } from "./CartPage";

export function CheckoutPage() {
  const { user } = useAuth();
  const { cart, coupon, refresh } = useCart();
  const toast = useToast();
  const navigate = useNavigate();
  const addresses = useApi<Address[]>("/api/account/addresses");
  const options = useApi<PaymentOptions>("/api/payments/options");
  const [addressId, setAddressId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState<AddressInput>(emptyAddress(user?.fullName));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [payment, setPayment] = useState<PaymentDraft>(initialPayment(null));
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const list = addresses.data;
    if (!list) return;
    if (list.length === 0) setCreating(true);
    else if (addressId === null) setAddressId((list.find((a) => a.isDefault) ?? list[0]).id);
  }, [addresses.data]);

  useEffect(() => {
    if (options.data) setPayment(initialPayment(options.data));
  }, [options.data]);

  if (cart && cart.items.length === 0 && !busy) return <Navigate to="/carrinho" replace />;

  const submit = async () => {
    const problem = paymentProblem(payment);
    if (problem) {
      toast.show(problem, "error");
      return;
    }
    if (!creating && addressId === null) {
      toast.show("Escolha o endereço de entrega.", "error");
      return;
    }
    setBusy(true);
    setErrors({});
    let target = addressId;
    if (creating) {
      try {
        const saved = await post<Address>("/api/account/addresses", { ...draft, isDefault: (addresses.data?.length ?? 0) === 0 });
        target = saved.id;
        setAddressId(saved.id);
        setCreating(false);
        addresses.reload();
      } catch (e) {
        setErrors(fieldErrors(e));
        toast.error(e);
        setBusy(false);
        return;
      }
    }
    let order: OrderDetail;
    try {
      order = await post<OrderDetail>("/api/orders", { addressId: target, couponCode: coupon, notes: notes.trim() || null });
    } catch (e) {
      toast.error(e);
      setBusy(false);
      return;
    }
    await refresh().catch(() => null);
    try {
      const paid = await post<OrderDetail>(`/api/orders/${order.number}/payments`, paymentBody(payment));
      const last = paid.payments[0];
      if (last?.checkoutUrl) {
        window.location.assign(last.checkoutUrl);
        return;
      }
      if (last?.status === "FAILED") toast.show(`Pagamento recusado: ${last.failureReason ?? "tente outro cartão"}`, "error");
      else if (last?.status === "PAID") toast.show(`Pedido ${order.number} confirmado`);
      else toast.show(`Pedido ${order.number} realizado`);
    } catch (e) {
      toast.error(e);
    }
    navigate(`/pedidos/${order.number}`, { replace: true, state: { fresh: true } });
  };

  return (
    <Page>
      <div className="wrap">
        <Link to="/carrinho" className="back-link">
          <Icon name="arrowLeft" size={16} /> Carrinho
        </Link>
        <PageHeader title="Entrega e pagamento" />
        <div className="checkout-layout">
          <div className="stack lg">
            <section className="card pad">
              <div className="section-head">
                <h3>Endereço de entrega</h3>
                {(addresses.data?.length ?? 0) > 0 && (
                  <button className="btn quiet sm" onClick={() => setCreating((v) => !v)}>
                    {creating ? "Usar um endereço salvo" : "Novo endereço"}
                  </button>
                )}
              </div>
              {addresses.loading ? (
                <Skeleton h={72} />
              ) : (
                <AnimatePresence mode="wait" initial={false}>
                  {creating ? (
                    <motion.div key="new" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
                      <AddressFields value={draft} onChange={setDraft} errors={errors} />
                      <p className="hint">O endereço fica salvo na sua conta.</p>
                    </motion.div>
                  ) : (
                    <motion.div key="list" className="choice-list" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
                      {addresses.data?.map((a) => (
                        <label key={a.id} className={`choice ${addressId === a.id ? "on" : ""}`}>
                          <input type="radio" name="address" checked={addressId === a.id} onChange={() => setAddressId(a.id)} />
                          <div>
                            <strong>
                              {a.label} {a.isDefault && <span className="chip">Principal</span>}
                            </strong>
                            <span className="muted small">{a.recipient}</span>
                            <AddressText a={a} />
                          </div>
                        </label>
                      ))}
                    </motion.div>
                  )}
                </AnimatePresence>
              )}
            </section>
            <section className="card pad">
              <div className="section-head">
                <h3>Pagamento</h3>
              </div>
              {options.data && cart ? (
                <PaymentForm options={options.data} value={payment} onChange={setPayment} total={cart.total} />
              ) : (
                <Skeleton h={96} />
              )}
            </section>
            <section className="card pad">
              <Field label="Observações para a entrega (opcional)">
                <textarea className="input" rows={2} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </Field>
            </section>
          </div>
          <div className="stack">
            {cart && (
              <div className="card pad">
                <ul className="mini-lines">
                  {cart.items.map((l) => (
                    <li key={l.productId}>
                      <ProductImage src={l.imageUrl} alt={l.name} className="thumb" />
                      <span>
                        {l.name}
                        <span className="muted small"> × {l.quantity}</span>
                      </span>
                      <span className="num">{money(l.lineTotal)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <Summary
              action={
                <button className="btn lg block" onClick={submit} disabled={busy || !cart}>
                  {busy ? <span className="spinner" /> : <>Finalizar pedido · {money(cart?.total)}</>}
                </button>
              }
            />
          </div>
        </div>
      </div>
    </Page>
  );
}
