import { motion } from "motion/react";
import { NavLink, Link } from "react-router-dom";
import { useIsBackoffice } from "../auth/AuthContext";
import { useCart } from "../cart/CartContext";
import { Icon } from "../components/Icon";
import { Logo } from "../components/Logo";
import { AnimatedOutlet } from "./AnimatedOutlet";
import { CartDrawer } from "./CartDrawer";
import { UserMenu } from "./UserMenu";

export function StoreLayout() {
  const { cart, setOpen, bump } = useCart();
  const backoffice = useIsBackoffice();
  return (
    <div className="store-shell">
      <header className="store-header">
        <div className="wrap store-header-inner">
          <Link to="/" className="brand" aria-label="Velo — início">
            <Logo size={22} />
          </Link>
          <nav className="store-nav" aria-label="Principal">
            <NavLink to="/" end>
              Loja
            </NavLink>
            <NavLink to="/pedidos">Pedidos</NavLink>
            {backoffice && <NavLink to="/admin">Painel</NavLink>}
          </nav>
          <span className="spacer" />
          <button className="cart-button" onClick={() => setOpen(true)} aria-label={`Abrir carrinho, ${cart?.count ?? 0} itens`}>
            <Icon name="bag" />
            <motion.span
              key={bump}
              className="cart-count num"
              initial={bump ? { scale: 0.6 } : false}
              animate={{ scale: 1 }}
              transition={{ type: "spring", stiffness: 520, damping: 18 }}
            >
              {cart?.count ?? 0}
            </motion.span>
          </button>
          <UserMenu />
        </div>
      </header>
      <AnimatedOutlet />
      <footer className="store-footer">
        <div className="wrap row">
          <Logo size={16} />
          <span className="muted small">Loja de demonstração. Pagamentos simulados, nenhum valor é cobrado.</span>
        </div>
      </footer>
      <CartDrawer />
    </div>
  );
}
