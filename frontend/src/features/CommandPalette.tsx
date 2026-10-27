import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Icon } from "../components/Icon";
import { StatusPill } from "../components/Ui";
import { get, qs } from "../lib/api";
import { money } from "../lib/format";
import { useHotkey, useTheme } from "../lib/prefs";
import type { OrderSummary, Page } from "../lib/types";
import { useDebounced } from "../lib/useApi";

interface Command {
  id: string;
  label: string;
  hint?: string;
  icon: string;
  group: string;
  run: () => void;
  extra?: React.ReactNode;
}

const normalize = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "");

export function CommandPalette({ open, onOpenChange }: { open: boolean; onOpenChange: (v: boolean) => void }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const theme = useTheme();
  const [q, setQ] = useState("");
  const [active, setActive] = useState(0);
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const term = useDebounced(q.trim(), 200);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLUListElement>(null);

  useHotkey(
    (e) => (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k",
    (e) => {
      e.preventDefault();
      onOpenChange(!open);
    }
  );

  useEffect(() => {
    if (open) {
      setQ("");
      setActive(0);
      setTimeout(() => input.current?.focus(), 10);
    }
  }, [open]);

  useEffect(() => {
    if (!open || term.length < 2) {
      setOrders([]);
      return;
    }
    const ctl = new AbortController();
    get<Page<OrderSummary>>("/api/admin/orders" + qs({ q: term, size: 5 }), ctl.signal)
      .then((r) => setOrders(r.items))
      .catch(() => null);
    return () => ctl.abort();
  }, [term, open]);

  const go = (to: string) => () => {
    onOpenChange(false);
    navigate(to);
  };

  const base: Command[] = useMemo(() => {
    const nav: Command[] = [
      { id: "dash", label: "Visão geral", icon: "chart", group: "Ir para", run: go("/admin") },
      { id: "orders", label: "Pedidos", icon: "receipt", group: "Ir para", run: go("/admin/pedidos") },
      { id: "await", label: "Pedidos aguardando pagamento", icon: "clock", group: "Ir para", run: go("/admin/pedidos?status=PENDING_PAYMENT") },
      { id: "ship", label: "Pedidos pagos para enviar", icon: "truck", group: "Ir para", run: go("/admin/pedidos?status=PAID") },
      { id: "products", label: "Produtos", icon: "box", group: "Ir para", run: go("/admin/produtos") },
      { id: "new-product", label: "Novo produto", icon: "plus", group: "Ações", run: go("/admin/produtos/novo") },
      { id: "cats", label: "Categorias", icon: "layers", group: "Ir para", run: go("/admin/categorias") },
      { id: "coupons", label: "Cupons", icon: "ticket", group: "Ir para", run: go("/admin/cupons") },
      { id: "store", label: "Ver loja", icon: "store", group: "Ir para", run: go("/") }
    ];
    if (user?.role === "ADMIN") {
      nav.push(
        { id: "users", label: "Usuários", icon: "users", group: "Ir para", run: go("/admin/usuarios") },
        { id: "integrations", label: "Integrações", hint: "Pagamentos, webhooks e CEP", icon: "plug", group: "Ir para", run: go("/admin/integracoes") }
      );
    }
    nav.push(
      {
        id: "theme",
        label: theme.mode === "dark" ? "Usar tema claro" : "Usar tema escuro",
        icon: theme.mode === "dark" ? "sun" : "moon",
        group: "Ações",
        run: () => theme.setMode(theme.mode === "dark" ? "light" : "dark")
      },
      { id: "account", label: "Minha conta", icon: "user", group: "Ações", run: go("/conta") }
    );
    return nav;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.role, theme.mode]);

  const results: Command[] = useMemo(() => {
    const n = normalize(q.trim());
    const found = n ? base.filter((c) => normalize(c.label + " " + (c.hint ?? "")).includes(n)) : base;
    const fromOrders: Command[] = orders.map((o) => ({
      id: "o" + o.id,
      label: `Pedido ${o.number}`,
      hint: `${o.customerName ?? "—"} · ${money(o.total)}`,
      icon: "receipt",
      group: "Pedidos",
      run: go(`/admin/pedidos/${o.number}`),
      extra: <StatusPill status={o.status} label={o.statusLabel} />
    }));
    const direct: Command[] =
      /^vl?\d{3,}$/i.test(q.trim()) && !orders.some((o) => o.number.toLowerCase() === q.trim().toLowerCase())
        ? [{ id: "direct", label: `Abrir pedido ${q.trim().toUpperCase()}`, icon: "arrowRight", group: "Pedidos", run: go(`/admin/pedidos/${q.trim().toUpperCase()}`) }]
        : [];
    const rank = (g: string) => ["Pedidos", "Ir para", "Ações"].indexOf(g);
    return [...fromOrders, ...direct, ...[...found].sort((a, b) => rank(a.group) - rank(b.group))];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, base, orders]);

  useEffect(() => setActive(0), [q, orders.length]);

  useEffect(() => {
    list.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active]);

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(results.length - 1, a + 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(0, a - 1));
    } else if (e.key === "Enter") {
      e.preventDefault();
      results[active]?.run();
    } else if (e.key === "Escape") {
      onOpenChange(false);
    }
  };

  let lastGroup = "";
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="overlay palette-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }} onMouseDown={() => onOpenChange(false)}>
          <motion.div
            className="palette"
            role="dialog"
            aria-label="Paleta de comandos"
            initial={{ opacity: 0, y: -8, scale: 0.99 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <div className="palette-input">
              <Icon name="search" size={18} />
              <input
                ref={input}
                value={q}
                onChange={(e) => setQ(e.target.value)}
                onKeyDown={onKey}
                placeholder="Ir para uma tela, buscar pedido por número, cliente ou e-mail…"
                aria-label="Comando"
                aria-controls="palette-list"
              />
              <kbd className="kbd">Esc</kbd>
            </div>
            <ul className="palette-list" id="palette-list" ref={list} role="listbox">
              {results.length === 0 && <li className="palette-empty muted">Nada encontrado para “{q}”.</li>}
              {results.map((c, i) => {
                const head = c.group !== lastGroup ? c.group : null;
                lastGroup = c.group;
                return (
                  <li key={c.id} role="none">
                    {head && <span className="palette-group">{head}</span>}
                    <button
                      role="option"
                      aria-selected={i === active}
                      data-index={i}
                      className={i === active ? "on" : ""}
                      onMouseEnter={() => setActive(i)}
                      onClick={c.run}
                    >
                      <Icon name={c.icon} size={17} />
                      <span className="grow">
                        {c.label}
                        {c.hint && <span className="muted small"> · {c.hint}</span>}
                      </span>
                      {c.extra}
                      {i === active && <kbd className="kbd">↵</kbd>}
                    </button>
                  </li>
                );
              })}
            </ul>
            <div className="palette-foot muted small">
              <span>
                <kbd className="kbd">↑</kbd> <kbd className="kbd">↓</kbd> navegar
              </span>
              <span>
                <kbd className="kbd">↵</kbd> abrir
              </span>
              <span>
                <kbd className="kbd">Ctrl</kbd> <kbd className="kbd">K</kbd> abrir ou fechar
              </span>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
