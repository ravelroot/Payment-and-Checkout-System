import { motion } from "motion/react";
import type { ReactNode } from "react";
import { money, statusTone } from "../lib/format";
import { Icon } from "./Icon";

export function Page({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <motion.main
      className={`page ${className}`}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.15, ease: "linear" }}
    >
      {children}
    </motion.main>
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
  back
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  back?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div className="page-header-text">
        {back}
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-header-actions">{actions}</div>}
    </header>
  );
}

export function Price({ value, compareAt, size = "md" }: { value: number; compareAt?: number | null; size?: "sm" | "md" | "lg" }) {
  return (
    <span className={`price ${size}`}>
      <span className="num">{money(value)}</span>
      {compareAt && compareAt > value && <s className="num">{money(compareAt)}</s>}
    </span>
  );
}

export function StatusPill({ status, label }: { status: string; label: string }) {
  return (
    <span className={`pill ${statusTone(status)}`}>
      <span className="dot" />
      {label}
    </span>
  );
}

export function Field({
  label,
  error,
  hint,
  children,
  className = ""
}: {
  label: string;
  error?: string;
  hint?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`field ${className}`}>
      <label>{label}</label>
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="hint">{hint}</span> : null}
    </div>
  );
}

export function Empty({ title, text, action, icon = "box" }: { title: string; text?: ReactNode; action?: ReactNode; icon?: string }) {
  return (
    <div className="empty">
      <div className="mark">
        <Icon name={icon} />
      </div>
      <h3>{title}</h3>
      {text && <p className="muted">{text}</p>}
      {action}
    </div>
  );
}

export function Stepper({ value, onChange, max = 99, min = 1 }: { value: number; onChange: (v: number) => void; max?: number; min?: number }) {
  return (
    <div className="stepper" role="group" aria-label="Quantidade">
      <button type="button" aria-label="Diminuir" disabled={value <= min} onClick={() => onChange(value - 1)}>
        <Icon name="minus" size={16} />
      </button>
      <span className="num">{value}</span>
      <button type="button" aria-label="Aumentar" disabled={value >= max} onClick={() => onChange(value + 1)}>
        <Icon name="plus" size={16} />
      </button>
    </div>
  );
}

export function ProductImage({ src, alt, className = "" }: { src: string | null; alt: string; className?: string }) {
  return (
    <div className={`product-image ${className}`}>
      {src ? <img src={src} alt={alt} loading="lazy" draggable={false} /> : <span className="product-image-empty">{alt.slice(0, 1)}</span>}
    </div>
  );
}

export function Pagination({ page, pages, onPage, total }: { page: number; pages: number; onPage: (p: number) => void; total: number }) {
  if (pages <= 1) return null;
  return (
    <div className="pagination">
      <span className="muted small">
        Página {page + 1} de {pages} · {total} itens
      </span>
      <div className="row">
        <button className="btn ghost sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
          <Icon name="arrowLeft" size={16} /> Anterior
        </button>
        <button className="btn ghost sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          Próxima <Icon name="arrowRight" size={16} />
        </button>
      </div>
    </div>
  );
}

export function Skeleton({ h = 16, w = "100%", r }: { h?: number; w?: number | string; r?: number }) {
  return <div className="skeleton" style={{ height: h, width: w, borderRadius: r }} />;
}
