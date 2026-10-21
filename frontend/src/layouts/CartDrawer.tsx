import { AnimatePresence, motion } from "motion/react";
import { useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../cart/CartContext";
import { Icon } from "../components/Icon";
import { Empty, Price, ProductImage, Stepper } from "../components/Ui";
import { money } from "../lib/format";

export function CartDrawer() {
  const { cart, open, setOpen, setQuantity, remove } = useCart();
  const navigate = useNavigate();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, setOpen]);

  const missing = cart?.freeShippingAbove ? Math.max(0, cart.freeShippingAbove - cart.subtotal) : 0;

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            className="drawer-overlay"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setOpen(false)}
          />
          <motion.aside
            className="drawer"
            role="dialog"
            aria-label="Carrinho"
            initial={{ x: "100%" }}
            animate={{ x: 0 }}
            exit={{ x: "100%" }}
            transition={{ duration: 0.42, ease: [0.22, 1, 0.36, 1] }}
          >
            <header className="drawer-head">
              <h2>Carrinho</h2>
              <span className="muted">{cart?.count ?? 0} itens</span>
              <span className="spacer" />
              <button className="icon-btn" onClick={() => setOpen(false)} aria-label="Fechar carrinho">
                <Icon name="close" />
              </button>
            </header>
            {!cart || cart.items.length === 0 ? (
              <Empty
                icon="bag"
                title="Seu carrinho está vazio"
                text="Escolha alguma coisa na loja. Ela aparece aqui."
                action={
                  <button className="btn ghost" onClick={() => setOpen(false)}>
                    Continuar comprando
                  </button>
                }
              />
            ) : (
              <>
                {cart.freeShippingAbove && (
                  <div className="free-ship">
                    <span>{missing > 0 ? <>Faltam <strong>{money(missing)}</strong> para o frete grátis</> : <>Você ganhou <strong>frete grátis</strong></>}</span>
                    <div className="free-ship-bar">
                      <motion.div
                        initial={false}
                        animate={{ width: `${Math.min(100, (cart.subtotal / cart.freeShippingAbove) * 100)}%` }}
                        transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
                      />
                    </div>
                  </div>
                )}
                <ul className="drawer-lines">
                  <AnimatePresence initial={false}>
                    {cart.items.map((l) => (
                      <motion.li
                        key={l.productId}
                        layout
                        initial={{ opacity: 0, x: 16 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: 24, height: 0, marginBottom: 0 }}
                        transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
                      >
                        <Link to={`/produto/${l.slug}`} onClick={() => setOpen(false)}>
                          <ProductImage src={l.imageUrl} alt={l.name} className="thumb" />
                        </Link>
                        <div className="drawer-line-body">
                          <Link to={`/produto/${l.slug}`} onClick={() => setOpen(false)} className="line-name">
                            {l.name}
                          </Link>
                          <span className="muted small num">{money(l.unitPrice)} cada</span>
                          {!l.available && <span className="field-error">Estoque insuficiente ({l.stock})</span>}
                          <div className="row">
                            <Stepper value={l.quantity} max={Math.max(1, Math.min(99, l.stock))} onChange={(v) => setQuantity(l.productId, v)} />
                            <button className="btn quiet sm" onClick={() => remove(l.productId)}>
                              Remover
                            </button>
                          </div>
                        </div>
                        <Price value={l.lineTotal} size="sm" />
                      </motion.li>
                    ))}
                  </AnimatePresence>
                </ul>
                <footer className="drawer-foot">
                  <dl className="kv">
                    <dt>Subtotal</dt>
                    <dd className="num">{money(cart.subtotal)}</dd>
                    <dt>Frete</dt>
                    <dd className="num">{cart.shipping === 0 ? "Grátis" : money(cart.shipping)}</dd>
                  </dl>
                  <button
                    className="btn lg block"
                    onClick={() => {
                      setOpen(false);
                      navigate("/checkout");
                    }}
                  >
                    Finalizar compra · <span className="num">{money(cart.subtotal + cart.shipping)}</span>
                  </button>
                  <Link to="/carrinho" className="link-center" onClick={() => setOpen(false)}>
                    Ver carrinho completo
                  </Link>
                </footer>
              </>
            )}
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
