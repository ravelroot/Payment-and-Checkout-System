import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Empty, Page, PageHeader, Skeleton, StatusPill } from "../../components/Ui";
import { RankBars, SalesChart, StatusBar } from "../../features/Charts";
import { money, relative } from "../../lib/format";
import type { Dashboard } from "../../lib/types";
import { useApi } from "../../lib/useApi";

const ranges = [7, 30, 90];

export function DashboardPage() {
  const [days, setDays] = useState(30);
  const { data } = useApi<Dashboard>(`/api/admin/dashboard?days=${days}`);
  const navigate = useNavigate();
  return (
    <Page>
      <div className="wrap wide">
        <PageHeader
          title="Visão geral"
          subtitle={`Últimos ${days} dias`}
          actions={
            <div className="segmented" role="tablist" aria-label="Período">
              {ranges.map((r) => (
                <button key={r} role="tab" aria-selected={days === r} className={days === r ? "on" : ""} onClick={() => setDays(r)}>
                  {r} dias
                </button>
              ))}
            </div>
          }
        />
        {!data ? (
          <div className="stack">
            <div className="kpis">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} h={92} r={0} />
              ))}
            </div>
            <Skeleton h={300} r={8} />
          </div>
        ) : (
          <div className="stack lg">
            <div className="kpis">
              <Kpi label="Faturamento" value={money(data.kpis.revenue)} change={data.kpis.revenueChange ?? delta(data.kpis.revenue, data.previous?.revenue)} />
              <Kpi label="Pedidos pagos" value={String(data.kpis.orders)} change={delta(data.kpis.orders, data.previous?.orders ?? data.previous?.paidOrders)} />
              <Kpi label="Ticket médio" value={money(data.kpis.averageTicket)} change={delta(data.kpis.averageTicket, data.previous?.averageTicket)} />
              <Kpi label="Novos clientes" value={String(data.kpis.newCustomers)} change={delta(data.kpis.newCustomers, data.previous?.newCustomers)} />
            </div>
            <div className="attention">
              <Link to="/admin/pedidos?status=PAID" className="attention-item">
                <strong className="num">{data.kpis.toShip}</strong>
                <span>pedidos pagos esperando envio</span>
              </Link>
              <Link to="/admin/pedidos?status=PENDING_PAYMENT" className="attention-item">
                <strong className="num">{data.kpis.awaitingPayment}</strong>
                <span>aguardando pagamento</span>
              </Link>
              <Link to="/admin/produtos?ordem=stock" className="attention-item">
                <strong className="num">{data.kpis.lowStock}</strong>
                <span>produtos com estoque baixo</span>
              </Link>
            </div>
            <section className="card pad">
              <h3>Vendas por dia</h3>
              <SalesChart data={data.sales} />
            </section>
            <div className="grid-2">
              <section className="card pad">
                <h3>Mais vendidos</h3>
                {data.topProducts.length ? <RankBars items={data.topProducts} /> : <Empty title="Sem vendas no período" />}
              </section>
              <section className="card pad">
                <h3>Pedidos por situação</h3>
                <StatusBar items={data.statuses} />
                {data.paymentMix && data.paymentMix.length > 0 && (
                  <>
                    <h3 className="sub-title">Formas de pagamento</h3>
                    <PaymentMix items={data.paymentMix} />
                  </>
                )}
              </section>
            </div>
            <div className="grid-2">
              <section className="card">
                <div className="panel-title">
                  <h3>Pedidos recentes</h3>
                  <Link to="/admin/pedidos" className="btn quiet sm">
                    Ver todos
                  </Link>
                </div>
                <div className="table-wrap">
                  <table className="table">
                    <tbody>
                      {data.recentOrders.map((o) => (
                        <tr key={o.id} className="link" tabIndex={0} onKeyDown={(e) => e.key === "Enter" && e.currentTarget.click()} onClick={() => navigate(`/admin/pedidos/${o.number}`)}>
                          <td>
                            <strong className="num">{o.number}</strong>
                            <div className="muted small">{o.customerName}</div>
                          </td>
                          <td>
                            <StatusPill status={o.status} label={o.statusLabel} />
                          </td>
                          <td className="right">
                            <span className="num">{money(o.total)}</span>
                            <div className="muted small">{relative(o.placedAt)}</div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
              <section className="card">
                <div className="panel-title">
                  <h3>Estoque baixo</h3>
                </div>
                {data.lowStock.length === 0 ? (
                  <Empty icon="check" title="Estoque em dia" text="Nenhum produto abaixo do mínimo." />
                ) : (
                  <div className="table-wrap">
                    <table className="table">
                      <tbody>
                        {data.lowStock.map((p) => (
                          <tr key={p.id} className="link" tabIndex={0} onKeyDown={(e) => e.key === "Enter" && e.currentTarget.click()} onClick={() => navigate(`/admin/produtos/${p.id}`)}>
                            <td>
                              {p.name}
                              <div className="muted small num">{p.sku}</div>
                            </td>
                            <td className="right">
                              <span className={`pill ${p.stock === 0 ? "danger" : "warn"}`}>
                                <span className="dot" />
                                {p.stock === 0 ? "Esgotado" : `${p.stock} un`}
                              </span>
                              <div className="muted small">mínimo {p.threshold}</div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </div>
          </div>
        )}
      </div>
    </Page>
  );
}

function delta(now: number, before: number | undefined | null) {
  if (before === undefined || before === null) return null;
  if (before === 0) return now === 0 ? 0 : null;
  return ((now - before) / before) * 100;
}

const mixLabel: Record<string, string> = { CARD: "Cartão", PIX: "PIX", BOLETO: "Boleto", CHECKOUT: "Checkout externo" };

function PaymentMix({ items }: { items: { method: string; orders: number; amount: number }[] }) {
  const total = items.reduce((s, i) => s + i.amount, 0) || 1;
  return (
    <ul className="mix">
      {items.map((i) => (
        <li key={i.method}>
          <span className="mix-label">{mixLabel[i.method] ?? i.method}</span>
          <span className="mix-track">
            <span className="mix-fill" style={{ width: `${(i.amount / total) * 100}%` }} />
          </span>
          <span className="num mix-value">
            {Math.round((i.amount / total) * 100)}% · {i.orders} ped.
          </span>
        </li>
      ))}
    </ul>
  );
}

function Kpi({ label, value, change }: { label: string; value: string; change?: number | null }) {
  return (
    <div className="kpi">
      <span className="kpi-label">{label}</span>
      <strong className="num">{value}</strong>
      {change !== undefined && change !== null && (
        <span className={`kpi-change small ${change >= 0 ? "up" : "down"}`}>
          {change >= 0 ? "+" : "−"}
          {Math.abs(change).toFixed(1).replace(".", ",")}% vs. período anterior
        </span>
      )}
    </div>
  );
}
