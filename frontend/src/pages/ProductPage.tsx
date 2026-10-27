import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import { Icon } from "../components/Icon";
import { Empty, Page, Price, ProductImage, Skeleton, Stepper } from "../components/Ui";
import { ShippingEstimate } from "../features/ShippingEstimate";
import { money } from "../lib/format";
import { tint, useFavorites, useRecent } from "../lib/prefs";
import type { ProductDetail } from "../lib/types";
import { useApi } from "../lib/useApi";

export function ProductPage() {
  const { slug } = useParams();
  const { user } = useAuth();
  const { data: p, loading, error } = useApi<ProductDetail>(`/api/catalog/products/${slug}`);
  const { add, setOpen } = useCart();
  const favorites = useFavorites(user?.id);
  const recent = useRecent(user?.id);
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [plans, setPlans] = useState(false);

  useEffect(() => {
    if (p) recent.push({ id: p.id, slug: p.slug, name: p.name, price: p.price, imageUrl: p.imageUrl, categorySlug: p.categorySlug });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [p?.id]);

  if (error) {
    return (
      <Page>
        <div className="wrap">
          <Empty title="Produto não encontrado" text="Ele pode ter saído do catálogo." action={<Link className="btn ghost" to="/">Voltar para a loja</Link>} />
        </div>
      </Page>
    );
  }

  const total = (p?.price ?? 0) * qty;
  const off = p?.compareAtPrice && p.compareAtPrice > p.price ? p.compareAtPrice - p.price : 0;

  return (
    <Page className="product-page">
      <div className="wrap">
        <nav className="crumbs" aria-label="Caminho">
          <Link to="/">Loja</Link>
          {p?.category && (
            <>
              <span aria-hidden>/</span>
              <Link to={`/?categoria=${p.categorySlug}`}>{p.category}</Link>
            </>
          )}
          {p && (
            <>
              <span aria-hidden>/</span>
              <span className="muted">{p.name}</span>
            </>
          )}
        </nav>
        {loading || !p ? (
          <div className="product-layout">
            <div className="skeleton" style={{ aspectRatio: "1 / 1", borderRadius: 10 }} />
            <div className="stack">
              <Skeleton h={14} w={120} />
              <Skeleton h={40} w="80%" />
              <Skeleton h={28} w={160} />
              <Skeleton h={90} />
            </div>
          </div>
        ) : (
          <div className="product-layout">
            <motion.div layoutId={`img-${p.slug}`} className="product-hero" style={{ background: tint(p.categorySlug) }}>
              <ProductImage src={p.imageUrl} alt={p.name} />
              <button
                className={`fav-button lg ${favorites.has(p.id) ? "on" : ""}`}
                onClick={() => favorites.toggle(p.id)}
                aria-pressed={favorites.has(p.id)}
                aria-label={favorites.has(p.id) ? "Tirar dos favoritos" : "Guardar nos favoritos"}
              >
                <Icon name={favorites.has(p.id) ? "heartFill" : "heart"} size={19} />
              </button>
            </motion.div>
            <motion.div
              className="product-info"
              initial={{ opacity: 0, x: 12 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.45, delay: 0.05, ease: [0.22, 1, 0.36, 1] }}
            >
              <span className="eyebrow">
                {p.category ?? "Produto"} · SKU <span className="num">{p.sku}</span>
              </span>
              <h1 className="display">{p.name}</h1>
              <div className="price-block">
                <Price value={p.price} compareAt={p.compareAtPrice} size="lg" />
                {off > 0 && <span className="pill promo num">Economize {money(off)}</span>}
              </div>
              <div className="installments">
                <button className="installments-head" onClick={() => setPlans((v) => !v)} aria-expanded={plans}>
                  <span>
                    <Icon name="card" size={16} /> até <strong className="num">10x de {money(p.price / 10)}</strong> sem juros
                  </span>
                  <span className="text-link">{plans ? "Ocultar parcelas" : "Ver parcelas"}</span>
                </button>
                {plans && (
                  <ol className="installments-grid num">
                    {Array.from({ length: 10 }, (_, i) => i + 1)
                      .filter((n) => n === 1 || total / n >= 10)
                      .map((n) => (
                        <li key={n}>
                          <span>{n}x</span>
                          <strong>{money(total / n)}</strong>
                        </li>
                      ))}
                  </ol>
                )}
                <span className="muted small">
                  <Icon name="pix" size={14} /> No PIX, aprovação na hora e envio mais rápido.
                </span>
              </div>
              {p.description && <p className="product-description">{p.description}</p>}
              <div className="stock-line">
                <span className={`dot ${p.stock === 0 ? "out" : p.stock <= p.lowStockThreshold ? "low" : "in"}`} />
                {p.stock === 0 ? "Esgotado" : p.stock <= p.lowStockThreshold ? `Restam ${p.stock} unidades` : "Em estoque, pronto para envio"}
              </div>
              <div className="buy-row">
                <Stepper value={qty} onChange={setQty} max={Math.max(1, Math.min(99, p.stock))} />
                <button
                  className="btn lg grow"
                  disabled={p.stock === 0 || busy}
                  onClick={async () => {
                    setBusy(true);
                    await add(p.id, qty, p.name);
                    setBusy(false);
                    setOpen(true);
                  }}
                >
                  {busy ? <span className="spinner" /> : <>Adicionar ao carrinho · <span className="num">{money(total)}</span></>}
                </button>
              </div>
              <ShippingEstimate subtotal={total} />
              <ul className="perks">
                <li>
                  <Icon name="truck" size={18} /> Frete grátis acima de R$ 299, postagem em até 2 dias úteis
                </li>
                <li>
                  <Icon name="shield" size={18} /> Pagamento protegido: o número do cartão nunca é gravado
                </li>
                <li>
                  <Icon name="refresh" size={18} /> 7 dias para devolver, sem perguntas
                </li>
              </ul>
            </motion.div>
          </div>
        )}
      </div>
    </Page>
  );
}
