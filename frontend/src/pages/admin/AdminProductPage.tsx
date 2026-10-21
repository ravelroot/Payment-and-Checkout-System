import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Icon } from "../../components/Icon";
import { Confirm, Modal } from "../../components/Modal";
import { Empty, Field, Page, PageHeader, ProductImage, Skeleton } from "../../components/Ui";
import { useToast } from "../../components/Toast";
import { fieldErrors } from "../../features/AddressForm";
import { del, post, put } from "../../lib/api";
import { dateTime } from "../../lib/format";
import type { Category, Movement, ProductDetail } from "../../lib/types";
import { useApi } from "../../lib/useApi";

type Form = {
  name: string;
  sku: string;
  description: string;
  price: string;
  compareAtPrice: string;
  categoryId: string;
  imageUrl: string;
  stock: string;
  lowStockThreshold: string;
  weightGrams: string;
  active: boolean;
  featured: boolean;
};

const blank: Form = {
  name: "",
  sku: "",
  description: "",
  price: "",
  compareAtPrice: "",
  categoryId: "",
  imageUrl: "",
  stock: "0",
  lowStockThreshold: "5",
  weightGrams: "0",
  active: true,
  featured: false
};

const movementLabel: Record<Movement["type"], string> = {
  IN: "Entrada",
  OUT: "Saída",
  SALE: "Venda",
  RETURN: "Devolução",
  ADJUST: "Ajuste"
};

const decimal = (v: string) => (v.trim() === "" ? null : Number(v.replace(/\./g, "").replace(",", ".")));
const show = (v: number | null | undefined) => (v === null || v === undefined ? "" : String(v).replace(".", ","));

export function AdminProductPage() {
  const { id } = useParams();
  const isNew = id === "novo";
  const navigate = useNavigate();
  const toast = useToast();
  const product = useApi<ProductDetail>(isNew ? null : `/api/admin/products/${id}`);
  const movements = useApi<Movement[]>(isNew ? null : `/api/admin/products/${id}/movements`);
  const categories = useApi<Category[]>("/api/admin/categories");
  const [form, setForm] = useState<Form>(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [stockOpen, setStockOpen] = useState(false);
  const [removeOpen, setRemoveOpen] = useState(false);
  const p = product.data;

  useEffect(() => {
    if (!p) return;
    setForm({
      name: p.name,
      sku: p.sku,
      description: p.description ?? "",
      price: show(p.price),
      compareAtPrice: show(p.compareAtPrice),
      categoryId: p.categoryId ? String(p.categoryId) : "",
      imageUrl: p.imageUrl ?? "",
      stock: String(p.stock),
      lowStockThreshold: String(p.lowStockThreshold),
      weightGrams: String(p.weightGrams),
      active: p.active,
      featured: p.featured
    });
  }, [p]);

  if (product.error) {
    return (
      <Page>
        <div className="wrap">
          <Empty title="Produto não encontrado" action={<Link className="btn ghost" to="/admin/produtos">Voltar para produtos</Link>} />
        </div>
      </Page>
    );
  }

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }));

  const save = async () => {
    setBusy(true);
    setErrors({});
    const body = {
      name: form.name,
      sku: form.sku.trim() || null,
      description: form.description.trim() || null,
      price: decimal(form.price),
      compareAtPrice: decimal(form.compareAtPrice),
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      imageUrl: form.imageUrl.trim() || null,
      stock: isNew ? Number(form.stock || 0) : undefined,
      lowStockThreshold: Number(form.lowStockThreshold || 0),
      weightGrams: Number(form.weightGrams || 0),
      active: form.active,
      featured: form.featured,
      version: p?.version ?? null
    };
    try {
      if (isNew) {
        const created = await post<ProductDetail>("/api/admin/products", body);
        toast.show("Produto criado");
        navigate(`/admin/produtos/${created.id}`, { replace: true });
      } else {
        product.setData(await put<ProductDetail>(`/api/admin/products/${id}`, body));
        toast.show("Produto salvo");
      }
    } catch (e) {
      setErrors(fieldErrors(e));
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      const r = await del<{ deleted: boolean }>(`/api/admin/products/${id}`);
      toast.show(r?.deleted ? "Produto excluído" : "Produto desativado, pois já aparece em pedidos");
      navigate("/admin/produtos", { replace: true });
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const loading = !isNew && !p;

  return (
    <Page>
      <div className="wrap wide">
        <Link to="/admin/produtos" className="back-link">
          <Icon name="arrowLeft" size={16} /> Produtos
        </Link>
        <PageHeader
          title={isNew ? "Novo produto" : p?.name ?? "Produto"}
          subtitle={p ? <span className="num">{p.sku}</span> : undefined}
          actions={
            <>
              {p && (
                <Link className="btn ghost" to={`/produto/${p.slug}`}>
                  <Icon name="eye" size={16} /> Ver na loja
                </Link>
              )}
              <button className="btn" onClick={save} disabled={busy || loading}>
                {busy ? <span className="spinner" /> : isNew ? "Criar produto" : "Salvar produto"}
              </button>
            </>
          }
        />
        {loading ? (
          <Skeleton h={420} r={20} />
        ) : (
          <div className="checkout-layout">
            <div className="stack lg">
              <section className="card pad">
                <div className="form-grid">
                  <Field label="Nome" error={errors.name} className="span-3">
                    <input className="input" value={form.name} maxLength={140} onChange={(e) => set("name", e.target.value)} />
                  </Field>
                  <Field label="SKU" hint={isNew ? "Vazio gera um automático." : undefined} className="span-1">
                    <input className="input num" value={form.sku} maxLength={40} onChange={(e) => set("sku", e.target.value.toUpperCase())} />
                  </Field>
                  <Field label="Descrição" className="span-4">
                    <textarea className="input" rows={4} maxLength={2000} value={form.description} onChange={(e) => set("description", e.target.value)} />
                  </Field>
                  <Field label="Preço (R$)" error={errors.price} className="span-1">
                    <input className="input num" inputMode="decimal" value={form.price} onChange={(e) => set("price", e.target.value)} placeholder="0,00" />
                  </Field>
                  <Field label="Preço anterior (R$)" hint="Aparece riscado." className="span-1">
                    <input className="input num" inputMode="decimal" value={form.compareAtPrice} onChange={(e) => set("compareAtPrice", e.target.value)} />
                  </Field>
                  <Field label="Categoria" className="span-2">
                    <select className="input" value={form.categoryId} onChange={(e) => set("categoryId", e.target.value)}>
                      <option value="">Sem categoria</option>
                      {categories.data?.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Imagem (URL)" hint="Endereço público ou um arquivo de /products/." className="span-4">
                    <input className="input" value={form.imageUrl} maxLength={400} onChange={(e) => set("imageUrl", e.target.value)} placeholder="/products/caneca.svg" />
                  </Field>
                  {isNew && (
                    <Field label="Estoque inicial" error={errors.stock} className="span-1">
                      <input className="input num" inputMode="numeric" value={form.stock} onChange={(e) => set("stock", e.target.value.replace(/\D/g, ""))} />
                    </Field>
                  )}
                  <Field label="Estoque mínimo" className="span-1">
                    <input className="input num" inputMode="numeric" value={form.lowStockThreshold} onChange={(e) => set("lowStockThreshold", e.target.value.replace(/\D/g, ""))} />
                  </Field>
                  <Field label="Peso (g)" className="span-1">
                    <input className="input num" inputMode="numeric" value={form.weightGrams} onChange={(e) => set("weightGrams", e.target.value.replace(/\D/g, ""))} />
                  </Field>
                </div>
                <div className="row">
                  <label className="check">
                    <input type="checkbox" checked={form.active} onChange={(e) => set("active", e.target.checked)} />
                    Visível na loja
                  </label>
                  <label className="check">
                    <input type="checkbox" checked={form.featured} onChange={(e) => set("featured", e.target.checked)} />
                    Destaque na vitrine
                  </label>
                </div>
              </section>
              {p && (
                <section className="card">
                  <div className="panel-title">
                    <h3>Movimentos de estoque</h3>
                  </div>
                  {movements.data && movements.data.length ? (
                    <div className="table-wrap">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>Quando</th>
                            <th>Tipo</th>
                            <th className="right">Quantidade</th>
                            <th className="right">Saldo</th>
                            <th>Motivo</th>
                          </tr>
                        </thead>
                        <tbody>
                          {movements.data.map((m) => (
                            <tr key={m.id}>
                              <td className="muted small">{dateTime(m.createdAt)}</td>
                              <td>{movementLabel[m.type]}</td>
                              <td className="right num">{m.quantity > 0 ? `+${m.quantity}` : m.quantity}</td>
                              <td className="right num">{m.stockAfter}</td>
                              <td className="muted small">{m.reason ?? "—"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <Empty icon="layers" title="Sem movimentos" />
                  )}
                </section>
              )}
            </div>
            <div className="stack">
              <section className="card pad">
                <ProductImage src={form.imageUrl || null} alt={form.name || "Produto"} className="preview" />
              </section>
              {p && (
                <section className="card pad">
                  <div className="section-head">
                    <h3>Estoque</h3>
                    <button className="btn ghost sm" onClick={() => setStockOpen(true)}>
                      Movimentar
                    </button>
                  </div>
                  <div className="big-number num">{p.stock}</div>
                  <span className="muted small">
                    {p.stock <= p.lowStockThreshold ? "Abaixo do mínimo" : "Em dia"} · mínimo {p.lowStockThreshold}
                  </span>
                </section>
              )}
              {p && (
                <button className="btn quiet" onClick={() => setRemoveOpen(true)}>
                  <Icon name="trash" size={16} /> Excluir produto
                </button>
              )}
            </div>
          </div>
        )}
      </div>
      {p && (
        <StockModal
          open={stockOpen}
          onClose={() => setStockOpen(false)}
          product={p}
          onDone={(v) => {
            product.setData(v);
            movements.reload();
          }}
        />
      )}
      <Confirm
        open={removeOpen}
        title="Excluir produto?"
        message="Se o produto já aparece em pedidos, ele é apenas desativado para manter o histórico."
        confirmText="Excluir"
        danger
        busy={busy}
        onConfirm={remove}
        onClose={() => setRemoveOpen(false)}
      />
    </Page>
  );
}

function StockModal({ open, onClose, product, onDone }: { open: boolean; onClose: () => void; product: ProductDetail; onDone: (p: ProductDetail) => void }) {
  const toast = useToast();
  const [type, setType] = useState<"IN" | "OUT" | "ADJUST">("IN");
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const n = Number(quantity || 0);
  const after = type === "IN" ? product.stock + n : type === "OUT" ? product.stock - n : n;

  const save = async () => {
    setBusy(true);
    try {
      onDone(await post<ProductDetail>(`/api/admin/products/${product.id}/stock`, { type, quantity: n, reason: reason.trim() || null }));
      toast.show("Estoque atualizado");
      setQuantity("");
      setReason("");
      onClose();
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Movimentar estoque"
      actions={
        <>
          <button className="btn ghost" onClick={onClose}>
            Voltar
          </button>
          <button className="btn" onClick={save} disabled={busy || quantity === "" || after < 0}>
            {busy ? <span className="spinner" /> : "Registrar"}
          </button>
        </>
      }
    >
      <div className="stack">
        <div className="segmented" role="tablist">
          {(["IN", "OUT", "ADJUST"] as const).map((t) => (
            <button key={t} role="tab" aria-selected={type === t} className={type === t ? "on" : ""} onClick={() => setType(t)}>
              {t === "IN" ? "Entrada" : t === "OUT" ? "Saída" : "Contagem"}
            </button>
          ))}
        </div>
        <Field label={type === "ADJUST" ? "Quantidade contada" : "Quantidade"} hint={`Saldo atual ${product.stock} · depois ${after}`} error={after < 0 ? "O estoque não pode ficar negativo." : undefined}>
          <input className="input num" inputMode="numeric" value={quantity} onChange={(e) => setQuantity(e.target.value.replace(/\D/g, ""))} autoFocus />
        </Field>
        <Field label="Motivo (opcional)">
          <input className="input" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} placeholder="Reposição do fornecedor, avaria…" />
        </Field>
      </div>
    </Modal>
  );
}
