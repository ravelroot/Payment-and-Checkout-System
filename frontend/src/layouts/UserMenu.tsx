import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth, useIsBackoffice } from "../auth/AuthContext";
import { useSessionClock } from "../auth/SessionWatcher";
import { Icon } from "../components/Icon";
import { clock, roleLabel } from "../lib/format";

export function UserMenu({ placement = "down" }: { placement?: "down" | "up" }) {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const left = useSessionClock();
  const backoffice = useIsBackoffice();

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("mousedown", onDown);
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("mousedown", onDown);
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (!user) return null;
  const initials = user.fullName
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();

  return (
    <div className={`user-menu ${placement}`} ref={ref}>
      <button className="user-trigger" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="menu">
        <span className="avatar">{initials}</span>
        <span className="user-trigger-text">
          <strong>{user.fullName.split(" ")[0]}</strong>
          <span className="muted small">{roleLabel[user.role]}</span>
        </span>
        <Icon name="chevronDown" size={16} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            className="menu"
            role="menu"
            initial={{ opacity: 0, y: placement === "down" ? -6 : 6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: placement === "down" ? -4 : 4, scale: 0.98 }}
            transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="menu-head">
              <strong>{user.fullName}</strong>
              <span className="muted small">{user.email}</span>
            </div>
            <div className="menu-session">
              <Icon name="clock" size={16} />
              <span>
                Sessão termina em <strong className="num">{clock(left)}</strong>
              </span>
            </div>
            {backoffice && (
              <Link to="/admin" role="menuitem" onClick={() => setOpen(false)}>
                <Icon name="chart" size={18} /> Painel
              </Link>
            )}
            <Link to="/conta" role="menuitem" onClick={() => setOpen(false)}>
              <Icon name="user" size={18} /> Minha conta
            </Link>
            <Link to="/pedidos" role="menuitem" onClick={() => setOpen(false)}>
              <Icon name="receipt" size={18} /> Meus pedidos
            </Link>
            <button role="menuitem" onClick={() => logout("LOGOUT")}>
              <Icon name="logout" size={18} /> Sair
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
