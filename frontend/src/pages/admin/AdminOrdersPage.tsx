import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Icon } from "../../components/Icon";
import { Empty, Page, PageHeader, Pagination, Skeleton, StatusPill } from "../../components/Ui";
import { qs } from "../../lib/api";
import { dateTime, money, statusLabel } from "../../lib/format";
import type { OrderStatus, OrderSummary, Page as PageOf } from "../../lib/types";
import { useApi, useDebounced } from "../../lib/useApi";

const statuses = Object.keys(statusLabel) as OrderStatus[];

export function AdminOrdersPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const status = (params.get("status") as OrderStatus | null) ?? null;
  const page = Number(params.get("pagina") ?? 0);
  const from = params.get("de") ?? "";
  const to = params.get("ate") ?? "";
  const [q, setQ] = useState(params.get("q") ?? "");
  const term = useDebounced(q.trim(), 300);
  const { data, loading } = useApi<PageOf<OrderSummary>>("/api/admin/orders" + qs({ status, q: term, from, to, page, size: 20 }));

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
        <PageHeader title="Pedidos" subtitle={data ? `${data.total} pedidos` : undefined} />
        <div className="toolbar">
          <label className="search">
            <Icon name="search" size={18} />
            <input
              className="input"
              placeholder="Número, nome ou e-mail do cliente"
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                update({ q: e.target.value.trim() || null });
              }}
              aria-label="Buscar pedidos"
            />
          </label>
          <select className="input auto" value={status ?? ""} onChange={(e) => update({ status: e.target.value || null })} aria-label="Situação">
            <option value="">Todas as situações</option>
            {statuses.map((s) => (
              <option key={s} value={s}>
                {statusLabel[s]}
              </option>
            ))}
          </select>
          <input className="input auto" type="date" value={from} onChange={(e) => update({ de: e.target.value || null })} aria-label="De" />
          <input className="input auto" type="date" value={to} onChange={(e) => update({ ate: e.target.value || null })} aria-label="Até" />
          {(status || from || to || q) && (
            <button
              className="btn quiet sm"
              onClick={() => {
                setQ("");
                setParams(new URLSearchParams(), { replace: true });
              }}
            >
              Limpar filtros
            </button>
          )}
        </div>
        <div className="card">
          {loading && !data ? (
            <div className="pad stack">
              {[0, 1, 2, 3, 4].map((i) => (
                <Skeleton key={i} h={36} />
              ))}
            </div>
          ) : data && data.items.length ? (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Pedido</th>
                    <th>Cliente</th>
                    <th>Situação</th>
                    <th className="right">Itens</th>
                    <th className="right">Total</th>
                  </tr>
                </thead>
                <tbody className={loading ? "loading" : ""}>
                  {data.items.map((o) => (
                    <tr key={o.id} className="link" tabIndex={0} onKeyDown={(e) => e.key === "Enter" && e.currentTarget.click()} onClick={() => navigate(`/admin/pedidos/${o.number}`)}>
                      <td>
                        <strong className="num">{o.number}</strong>
                        <div className="muted small">{dateTime(o.placedAt)}</div>
                      </td>
                      <td>
                        {o.customerName}
                        <div className="muted small">{o.customerEmail}</div>
                      </td>
                      <td>
                        <StatusPill status={o.status} label={o.statusLabel} />
                      </td>
                      <td className="right num">{o.items}</td>
                      <td className="right num">{money(o.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty icon="receipt" title="Nenhum pedido encontrado" text="Ajuste a busca ou os filtros." />
          )}
        </div>
        {data && <Pagination page={data.page} pages={data.pages} total={data.total} onPage={(p) => update({ pagina: String(p) })} />}
      </div>
    </Page>
  );
}
