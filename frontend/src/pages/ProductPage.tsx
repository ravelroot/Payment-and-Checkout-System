import { motion } from "motion/react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useCart } from "../cart/CartContext";
import { Icon } from "../components/Icon";
import { Empty, Page, Price, ProductImage, Skeleton, Stepper } from "../components/Ui";
import { money } from "../lib/format";
import type { ProductDetail } from "../lib/types";
import { useApi } from "../lib/useApi";

export function ProductPage() {
  const { slug } = useParams();
  const { data: p, loading, error } = useApi<ProductDetail>(`/api/catalog/products/${slug}`);
  const { add, setOpen } = useCart();
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);

  if (error) {
    return (
      <Page>
        <div className="wrap">
          <Empty title="Produto não encontrado" text="Ele pode ter saído do catálogo." action={<Link className="btn ghost" to="/">Voltar para a loja</Link>} />
        </div>
      </Page>
    );
  }

  return (
    <Page className="product-page">
      <div className="wrap">
        <Link to="/" className="back-link">
          <Icon name="arrowLeft" size={16} /> Loja
        </Link>
        {loading || !p ? (
          <div className="product-layout">
            <div className="skeleton" style={{ aspectRatio: "1 / 1", borderRadius: 24 }} />
            <div className="stack">
              <Skeleton h={14} w={120} />
              <Skeleton h={40} w="80%" />
              <Skeleton h={28} w={160} />
              <Skeleton h={90} />
            </div>
          </div>
        ) : (
          <div className="product-layout">
            <motion.div layoutId={`img-${p.slug}`} className="product-hero">
              <ProductImage src={p.imageUrl} alt={p.name} />
            </motion.div>
            <motion.div
              className="product-info"
              initial={{ opacity: 0, x: 12 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.45, delay: 0.05, ease: [0.22, 1, 0.36, 1] }}
            >
              {p.category && (
                <Link to={`/?categoria=${p.categorySlug}`} className="muted small">
                  {p.category}
                </Link>
              )}
              <h1>{p.name}</h1>
              <Price value={p.price} compareAt={p.compareAtPrice} size="lg" />
              <p className="muted small num">
                ou 10x de {money(p.price / 10)} sem juros · SKU {p.sku}
              </p>
              {p.description && <p className="product-description">{p.description}</p>}
              <div className="stock-line">
                <span className={`dot ${p.stock === 0 ? "out" : p.stock <= p.lowStockThreshold ? "low" : "in"}`} />
                {p.stock === 0 ? "Esgotado" : p.stock <= p.lowStockThreshold ? `Restam ${p.stock} unidades` : "Em estoque"}
              </div>
              <div className="buy-row">
                <Stepper value={qty} onChange={setQty} max={Math.max(1, Math.min(99, p.stock))} />
                <button
                  className="btn lg"
                  disabled={p.stock === 0 || busy}
                  onClick={async () => {
                    setBusy(true);
                    await add(p.id, qty, p.name);
                    setBusy(false);
                    setOpen(true);
                  }}
                >
                  {busy ? <span className="spinner" /> : <>Adicionar ao carrinho · <span className="num">{money(p.price * qty)}</span></>}
                </button>
              </div>
              <ul className="perks">
                <li>
                  <Icon name="truck" size={18} /> Frete grátis acima de R$ 299, envio em até 2 dias úteis
                </li>
                <li>
                  <Icon name="shield" size={18} /> Pagamento seguro com cartão, PIX ou boleto
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
