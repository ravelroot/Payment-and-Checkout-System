import { NavLink, Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Icon } from "../components/Icon";
import { Logo } from "../components/Logo";
import { AnimatedOutlet } from "./AnimatedOutlet";
import { UserMenu } from "./UserMenu";

const items = [
  { to: "/admin", label: "Visão geral", icon: "chart", end: true },
  { to: "/admin/pedidos", label: "Pedidos", icon: "receipt" },
  { to: "/admin/produtos", label: "Produtos", icon: "box" },
  { to: "/admin/categorias", label: "Categorias", icon: "layers" },
  { to: "/admin/cupons", label: "Cupons", icon: "ticket" }
];

export function AdminLayout() {
  const { user } = useAuth();
  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <Link to="/admin" className="brand">
          <Logo size={20} />
        </Link>
        <span className="sidebar-tag">Painel</span>
        <nav aria-label="Painel">
          {items.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.end}>
              <Icon name={i.icon} size={19} />
              {i.label}
            </NavLink>
          ))}
          {user?.role === "ADMIN" && (
            <NavLink to="/admin/usuarios">
              <Icon name="users" size={19} />
              Usuários
            </NavLink>
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
    </div>
  );
}
