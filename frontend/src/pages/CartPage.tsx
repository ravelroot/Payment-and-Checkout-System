import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../cart/CartContext";
import { Empty, Page, PageHeader, Price, ProductImage, Stepper } from "../components/Ui";
import { useToast } from "../components/Toast";
import { money } from "../lib/format";

export function CartPage() {
  const { cart, setQuantity, remove } = useCart();
  const navigate = useNavigate();
  if (!cart) return <Page><div className="wrap" /></Page>;
  return (
    <Page>
      <div className="wrap">
        <PageHeader title="Carrinho" subtitle={cart.count ? `${cart.count} itens` : undefined} />
        {cart.items.length === 0 ? (
          <div className="card">
            <Empty icon="bag" title="Seu carrinho está vazio" text="Os produtos que você adicionar aparecem aqui." action={<Link className="btn" to="/">Ir para a loja</Link>} />
          </div>
        ) : (
          <div className="checkout-layout">
            <div className="card">
              <ul className="cart-lines">
                <AnimatePresence initial={false}>
                  {cart.items.map((l) => (
                    <motion.li
                      key={l.productId}
                      layout
                      exit={{ opacity: 0, height: 0, paddingTop: 0, paddingBottom: 0 }}
                      transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
                    >
                      <Link to={`/produto/${l.slug}`}>
                        <ProductImage src={l.imageUrl} alt={l.name} className="thumb lg" />
                      </Link>
                      <div className="cart-line-body">
                        <Link to={`/produto/${l.slug}`} className="line-name">
                          {l.name}
                        </Link>
                        <span className="muted small">
                          {l.sku} · <span className="num">{money(l.unitPrice)}</span> cada
                        </span>
                        {!l.available && <span className="field-error">Só restam {l.stock} unidades</span>}
                        <div className="row">
                          <Stepper value={l.quantity} max={Math.max(1, Math.min(99, l.stock))} onChange={(v) => setQuantity(l.productId, v)} />
                          <button className="btn quiet sm" onClick={() => remove(l.productId)}>
                            Remover
                          </button>
                        </div>
                      </div>
                      <Price value={l.lineTotal} />
                    </motion.li>
                  ))}
                </AnimatePresence>
              </ul>
            </div>
            <Summary action={<button className="btn lg block" onClick={() => navigate("/checkout")}>Continuar para entrega e pagamento</button>} />
          </div>
        )}
      </div>
    </Page>
  );
}

export function Summary({ action }: { action?: React.ReactNode }) {
  const { cart, coupon, setCoupon } = useCart();
  const toast = useToast();
  const [code, setCode] = useState(coupon ?? "");
  const [busy, setBusy] = useState(false);
  if (!cart) return null;
  const apply = async () => {
    setBusy(true);
    try {
      const v = await setCoupon(code.trim() || null);
      if (code.trim() && !v?.couponCode) toast.show(v?.couponMessage ?? "Cupom inválido", "error");
      else if (v?.couponCode) toast.show(`Cupom ${v.couponCode} aplicado`);
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <aside className="card pad summary">
      <h3>Resumo</h3>
      <div className="coupon">
        <input
          className="input"
          placeholder="Cupom de desconto"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          onKeyDown={(e) => e.key === "Enter" && apply()}
          aria-label="Cupom de desconto"
        />
        {coupon ? (
          <button
            className="btn ghost"
            onClick={async () => {
              setCode("");
              await setCoupon(null);
            }}
          >
            Remover
          </button>
        ) : (
          <button className="btn ghost" onClick={apply} disabled={busy || !code.trim()}>
            Aplicar
          </button>
        )}
      </div>
      {coupon && <span className="hint">Cupom {coupon}: {cart.couponMessage}</span>}
      {!coupon && <span className="hint">Experimente BEMVINDO10 ou VELO20.</span>}
      <dl className="kv">
        <dt>Subtotal</dt>
        <dd className="num">{money(cart.subtotal)}</dd>
        {cart.discount > 0 && (
          <>
            <dt>Desconto</dt>
            <dd className="num">− {money(cart.discount)}</dd>
          </>
        )}
        <dt>Frete</dt>
        <dd className="num">{cart.shipping === 0 ? "Grátis" : money(cart.shipping)}</dd>
      </dl>
      <hr className="divider" />
      <div className="total-line">
        <span>Total</span>
        <strong className="num">{money(cart.total)}</strong>
      </div>
      {action}
    </aside>
  );
}
