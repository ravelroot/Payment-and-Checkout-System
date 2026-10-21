import { motion } from "motion/react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon } from "../components/Icon";
import { Empty, Page, PageHeader, Pagination, Skeleton, StatusPill } from "../components/Ui";
import { qs } from "../lib/api";
import { dateTime, money } from "../lib/format";
import type { OrderStatus, OrderSummary, Page as PageOf } from "../lib/types";
import { useApi } from "../lib/useApi";

const filters: { value: OrderStatus | null; label: string }[] = [
  { value: null, label: "Todos" },
  { value: "PENDING_PAYMENT", label: "Aguardando pagamento" },
  { value: "PAID", label: "Pagos" },
  { value: "SHIPPED", label: "Enviados" },
  { value: "DELIVERED", label: "Entregues" },
  { value: "CANCELED", label: "Cancelados" }
];

export function OrdersPage() {
  const [status, setStatus] = useState<OrderStatus | null>(null);
  const [page, setPage] = useState(0);
  const { data, loading } = useApi<PageOf<OrderSummary>>("/api/orders" + qs({ status, page, size: 10 }));
  return (
    <Page>
      <div className="wrap narrow">
        <PageHeader title="Meus pedidos" />
        <div className="chips" role="tablist" aria-label="Filtrar pedidos">
          {filters.map((f) => (
            <button
              key={f.label}
              role="tab"
              aria-selected={status === f.value}
              className={`chip-btn ${status === f.value ? "on" : ""}`}
              onClick={() => {
                setStatus(f.value);
                setPage(0);
              }}
            >
              {f.label}
            </button>
          ))}
        </div>
        {loading && !data ? (
          <div className="stack">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} h={78} r={18} />
            ))}
          </div>
        ) : data && data.items.length > 0 ? (
          <>
            <ul className="order-list">
              {data.items.map((o, i) => (
                <motion.li key={o.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(i, 8) * 0.03, duration: 0.3 }}>
                  <Link to={`/pedidos/${o.number}`} className="order-row">
                    <div>
                      <strong className="num">{o.number}</strong>
                      <span className="muted small">
                        {dateTime(o.placedAt)} · {o.items} {o.items === 1 ? "item" : "itens"}
                      </span>
                    </div>
                    <StatusPill status={o.status} label={o.statusLabel} />
                    <span className="num strong">{money(o.total)}</span>
                    <Icon name="arrowRight" size={18} />
                  </Link>
                </motion.li>
              ))}
            </ul>
            <Pagination page={data.page} pages={data.pages} total={data.total} onPage={setPage} />
          </>
        ) : (
          <div className="card">
            <Empty
              icon="receipt"
              title={status ? "Nenhum pedido nessa situação" : "Você ainda não fez pedidos"}
              text={status ? "Escolha outro filtro para ver os demais." : "Quando você comprar, os pedidos aparecem aqui."}
              action={!status ? <Link className="btn" to="/">Ir para a loja</Link> : undefined}
            />
          </div>
        )}
      </div>
    </Page>
  );
}
