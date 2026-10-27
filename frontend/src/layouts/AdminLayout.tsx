import { useState } from "react";
import { NavLink, Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Icon } from "../components/Icon";
import { Logo } from "../components/Logo";
import { CommandPalette } from "../features/CommandPalette";
import { AnimatedOutlet } from "./AnimatedOutlet";
import { UserMenu } from "./UserMenu";

const items = [
  { to: "/admin", label: "Visão geral", icon: "chart", end: true },
  { to: "/admin/pedidos", label: "Pedidos", icon: "receipt" },
  { to: "/admin/produtos", label: "Produtos", icon: "box" },
  { to: "/admin/categorias", label: "Categorias", icon: "layers" },
  { to: "/admin/cupons", label: "Cupons", icon: "ticket" }
];

const mac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform);

export function AdminLayout() {
  const { user } = useAuth();
  const [palette, setPalette] = useState(false);
  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <Link to="/admin" className="brand">
          <Logo size={20} />
        </Link>
        <span className="sidebar-tag">Painel</span>
        <button className="palette-trigger" onClick={() => setPalette(true)}>
          <Icon name="search" size={16} />
          <span className="grow">Buscar ou ir…</span>
          <kbd className="kbd">{mac ? "⌘" : "Ctrl"} K</kbd>
        </button>
        <nav aria-label="Painel">
          <span className="nav-group">Operação</span>
          {items.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.end}>
              <Icon name={i.icon} size={19} />
              {i.label}
            </NavLink>
          ))}
          {user?.role === "ADMIN" && (
            <>
              <span className="nav-group">Administração</span>
              <NavLink to="/admin/usuarios">
                <Icon name="users" size={19} />
                Usuários
              </NavLink>
              <NavLink to="/admin/integracoes">
                <Icon name="plug" size={19} />
                Integrações
              </NavLink>
            </>
          )}
          <hr />
          <NavLink to="/" end>
            <Icon name="store" size={19} />
            Ver loja
          </NavLink>
        </nav>
        <span className="spacer" />
        <UserMenu placement="up" />
      </aside>
      <div className="admin-main">
        <AnimatedOutlet />
      </div>
      <CommandPalette open={palette} onOpenChange={setPalette} />
    </div>
  );
}
