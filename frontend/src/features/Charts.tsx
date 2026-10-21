import { motion } from "motion/react";
import { useMemo, useRef, useState } from "react";
import { money, shortDate } from "../lib/format";

export function SalesChart({ data, height = 200 }: { data: { date: string; revenue: number; orders: number }[]; height?: number }) {
  const [hover, setHover] = useState<number | null>(null);
  const ref = useRef<SVGSVGElement>(null);
  const w = 720;
  const h = height;
  const pad = { t: 16, r: 8, b: 28, l: 8 };
  const max = Math.max(1, ...data.map((d) => d.revenue));
  const step = (w - pad.l - pad.r) / Math.max(1, data.length);
  const bar = Math.max(2, step * 0.62);
  const y = (v: number) => pad.t + (h - pad.t - pad.b) * (1 - v / max);
  const ticks = useMemo(() => {
    const every = Math.ceil(data.length / 6);
    return data.map((d, i) => ({ i, d })).filter(({ i }) => i % every === 0 || i === data.length - 1);
  }, [data]);
  const total = data.reduce((s, d) => s + d.revenue, 0);
  const point = hover !== null ? data[hover] : null;

  const onMove = (e: React.PointerEvent) => {
    const r = ref.current?.getBoundingClientRect();
    if (!r) return;
    const x = ((e.clientX - r.left) / r.width) * w - pad.l;
    const i = Math.floor(x / step);
    setHover(i >= 0 && i < data.length ? i : null);
  };

  return (
    <div className="chart">
      <div className="chart-readout" aria-live="polite">
        {point ? (
          <>
            <strong className="num">{money(point.revenue)}</strong>
            <span className="muted small">
              {shortDate(point.date)} · {point.orders} {point.orders === 1 ? "pedido" : "pedidos"}
            </span>
          </>
        ) : (
          <>
            <strong className="num">{money(total)}</strong>
            <span className="muted small">no período</span>
          </>
        )}
      </div>
      <svg
        ref={ref}
        viewBox={`0 0 ${w} ${h}`}
        className="chart-svg"
        role="img"
        aria-label={`Vendas por dia, total ${money(total)}`}
        onPointerMove={onMove}
        onPointerLeave={() => setHover(null)}
      >
        {[0.25, 0.5, 0.75, 1].map((f) => (
          <line key={f} x1={pad.l} x2={w - pad.r} y1={y(max * f)} y2={y(max * f)} className="grid-line" />
        ))}
        {data.map((d, i) => {
          const x = pad.l + i * step + (step - bar) / 2;
          const top = y(d.revenue);
          const bh = Math.max(d.revenue > 0 ? 2 : 0, h - pad.b - top);
          return (
            <motion.rect
              key={d.date}
              x={x}
              width={bar}
              rx={Math.min(2, bar / 2)}
              className={`bar ${hover === i ? "on" : ""} ${hover !== null && hover !== i ? "dim" : ""}`}
              initial={{ y: h - pad.b, height: 0 }}
              animate={{ y: h - pad.b - bh, height: bh }}
              transition={{ duration: 0.4, ease: [0.2, 0, 0, 1] }}
            />
          );
        })}
        {ticks.map(({ i, d }) => (
          <text
            key={d.date}
            x={i === 0 ? pad.l : i === data.length - 1 ? w - pad.r : pad.l + i * step + step / 2}
            y={h - 8}
            textAnchor={i === 0 ? "start" : i === data.length - 1 ? "end" : "middle"}
            className="axis"
          >
            {shortDate(d.date)}
          </text>
        ))}
      </svg>
    </div>
  );
}

export function StatusBar({ items }: { items: { status: string; label: string; count: number }[] }) {
  const total = items.reduce((s, i) => s + i.count, 0) || 1;
  const tone: Record<string, string> = {
    PENDING_PAYMENT: "#c99a4a",
    PAID: "#6fa595",
    PROCESSING: "#3f8270",
    SHIPPED: "#2c6d5b",
    DELIVERED: "var(--accent)",
    CANCELED: "var(--stone)",
    REFUNDED: "var(--mauve)"
  };
  return (
    <div className="status-bar">
      <div className="status-track" role="img" aria-label="Pedidos por situação">
        {items
          .filter((i) => i.count > 0)
          .map((i) => (
            <motion.span
              key={i.status}
              style={{ background: tone[i.status] }}
              initial={{ width: 0 }}
              animate={{ width: `${(i.count / total) * 100}%` }}
              transition={{ duration: 0.4, ease: [0.2, 0, 0, 1] }}
              title={`${i.label}: ${i.count}`}
            />
          ))}
      </div>
      <ul className="legend">
        {items.map((i) => (
          <li key={i.status}>
            <span className="swatch" style={{ background: tone[i.status] }} />
            {i.label}
            <span className="num muted">{i.count}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function RankBars({ items }: { items: { name: string; quantity: number; revenue: number }[] }) {
  const max = Math.max(1, ...items.map((i) => i.revenue));
  return (
    <ol className="rank">
      {items.map((i) => (
        <li key={i.name}>
          <div className="rank-text">
            <span>{i.name}</span>
            <span className="num muted small">
              {i.quantity} un · {money(i.revenue)}
            </span>
          </div>
          <div className="rank-track">
            <motion.span
              initial={{ width: 0 }}
              animate={{ width: `${(i.revenue / max) * 100}%` }}
              transition={{ duration: 0.4, ease: [0.2, 0, 0, 1] }}
            />
          </div>
        </li>
      ))}
    </ol>
  );
}
