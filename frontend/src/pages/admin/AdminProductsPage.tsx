import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Icon } from "../../components/Icon";
import { Empty, Page, PageHeader, Pagination, ProductImage, Skeleton } from "../../components/Ui";
import { qs } from "../../lib/api";
import { money } from "../../lib/format";
import type { Category, Page as PageOf, ProductDetail } from "../../lib/types";
import { useApi, useDebounced } from "../../lib/useApi";

const sorts = [
  { value: "name", label: "Nome" },
  { value: "stock", label: "Menor estoque" },
  { value: "newest", label: "Mais recentes" },
  { value: "price_desc", label: "Maior preço" }
];

export function AdminProductsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const [q, setQ] = useState(params.get("q") ?? "");
  const term = useDebounced(q.trim(), 300);
  const category = params.get("categoria") ?? "";
  const sort = params.get("ordem") ?? "name";
  const page = Number(params.get("pagina") ?? 0);
  const categories = useApi<Category[]>("/api/admin/categories");
  const { data, loading } = useApi<PageOf<ProductDetail>>("/api/admin/products" + qs({ q: term, category, sort, page, size: 20 }));

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("pagina" in patch)) next.delete("pagina");
    setParams(next, { replace: true });
  };

  return (
    <Page>
      <div className="wrap wide">
        <PageHeader
          title="Produtos"
          subtitle={data ? `${data.total} produtos` : undefined}
          actions={
            <Link className="btn" to="/admin/produtos/novo">
              <Icon name="plus" size={16} /> Novo produto
            </Link>
          }
        />
        <div className="toolbar">
          <label className="search">
            <Icon name="search" size={18} />
            <input
              className="input"
              placeholder="Nome ou SKU"
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                update({ q: e.target.value.trim() || null });
              }}
              aria-label="Buscar produtos"
            />
          </label>
          <select className="input auto" value={category} onChange={(e) => update({ categoria: e.target.value || null })} aria-label="Categoria">
            <option value="">Todas as categorias</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.slug}>
                {c.name}
              </option>
            ))}
          </select>
          <select className="input auto" value={sort} onChange={(e) => update({ ordem: e.target.value })} aria-label="Ordenar">
            {sorts.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
        <div className="card">
          {loading && !data ? (
            <div className="pad stack">
              {[0, 1, 2, 3, 4].map((i) => (
                <Skeleton key={i} h={44} />
              ))}
            </div>
          ) : data && data.items.length ? (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Produto</th>
                    <th>Categoria</th>
                    <th className="right">Preço</th>
                    <th className="right">Estoque</th>
                    <th>Situação</th>
                  </tr>
                </thead>
                <tbody className={loading ? "loading" : ""}>
                  {data.items.map((p) => (
                    <tr key={p.id} className="link" tabIndex={0} onKeyDown={(e) => e.key === "Enter" && e.currentTarget.click()} onClick={() => navigate(`/admin/produtos/${p.id}`)}>
                      <td>
                        <div className="cell-product">
                          <ProductImage src={p.imageUrl} alt={p.name} className="thumb" />
                          <div>
                            {p.name}
                            <div className="muted small num">{p.sku}</div>
                          </div>
                        </div>
                      </td>
                      <td className="muted">{p.category ?? "—"}</td>
                      <td className="right num">{money(p.price)}</td>
                      <td className="right">
                        <span className={`num ${p.stock <= p.lowStockThreshold ? "low" : ""}`}>{p.stock}</span>
                      </td>
                      <td>
                        {p.active ? (
                          <span className="pill ok">
                            <span className="dot" />
                            Ativo
                          </span>
                        ) : (
                          <span className="pill line">
                            <span className="dot" />
                            Inativo
                          </span>
                        )}
                        {p.featured && <span className="chip">Destaque</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty title="Nenhum produto encontrado" text="Ajuste a busca ou cadastre um produto novo." />
          )}
        </div>
        {data && <Pagination page={data.page} pages={data.pages} total={data.total} onPage={(p) => update({ pagina: String(p) })} />}
      </div>
    </Page>
  );
}
