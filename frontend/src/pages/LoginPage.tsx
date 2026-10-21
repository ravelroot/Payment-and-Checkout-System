import { AnimatePresence, motion } from "motion/react";
import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Icon } from "../components/Icon";
import { Mark, Wordmark } from "../components/Logo";
import { Field } from "../components/Ui";
import { ApiError } from "../lib/api";

export const DEMO_ACCOUNTS = [
  { label: "Administrador", email: "admin@velo.dev", password: "Velo@2026", text: "Tudo, incluindo usuários" },
  { label: "Equipe", email: "equipe@velo.dev", password: "Velo@2026", text: "Pedidos, produtos e cupons" },
  { label: "Cliente", email: "cliente@velo.dev", password: "Velo@2026", text: "Loja, carrinho e pedidos" }
];

export function LoginPage() {
  const { user, login, register, notice, clearNotice, idleMinutes, maxMinutes } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  if (user) return <Navigate to={user.role === "CUSTOMER" ? "/" : "/admin"} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    clearNotice();
    try {
      const me = mode === "login" ? await login(email, password) : await register(name, email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== "/entrar" ? from : me.role === "CUSTOMER" ? "/" : "/admin", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Não foi possível entrar.");
      if (err instanceof ApiError) setFields(err.fields);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <section className="login-panel">
        <div className="login-brand">
          <Mark size={22} title="Velo" />
          <Wordmark size={22} />
        </div>
        <div className="login-box">
          <div className="segmented" role="tablist">
            <button role="tab" aria-selected={mode === "login"} onClick={() => setMode("login")}>
              Entrar
            </button>
            <button role="tab" aria-selected={mode === "register"} onClick={() => setMode("register")}>
              Criar conta
            </button>
            <motion.span className="segmented-thumb" layout transition={{ duration: 0.2, ease: [0.2, 0, 0, 1] }} style={{ left: mode === "login" ? 3 : "50%" }} />
          </div>

          <h1>{mode === "login" ? "Entrar na sua conta" : "Criar conta"}</h1>

          <AnimatePresence>
            {(notice || error) && (
              <motion.div
                className={`notice ${error ? "error" : ""}`}
                role="alert"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                exit={{ opacity: 0, height: 0 }}
              >
                <span className="dot" />
                {error ?? notice}
              </motion.div>
            )}
          </AnimatePresence>

          <form className="stack" onSubmit={submit} noValidate>
            <AnimatePresence initial={false}>
              {mode === "register" && (
                <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}>
                  <Field label="Nome completo" error={fields.fullName}>
                    <input className="input" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required />
                  </Field>
                </motion.div>
              )}
            </AnimatePresence>
            <Field label="E-mail" error={fields.email}>
              <input
                className="input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                placeholder="voce@exemplo.com"
                required
                autoFocus
              />
            </Field>
            <Field
              label="Senha"
              error={fields.password}
              hint={mode === "register" ? "8 caracteres ou mais, com letras e números." : undefined}
            >
              <div className="input-affix">
                <input
                  className="input"
                  type={show ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete={mode === "login" ? "current-password" : "new-password"}
                  required
                />
                <button type="button" className="icon-btn" onClick={() => setShow((s) => !s)} aria-label={show ? "Esconder senha" : "Mostrar senha"}>
                  <Icon name={show ? "lock" : "eye"} size={18} />
                </button>
              </div>
            </Field>
            <button className="btn lg block" disabled={busy}>
              {busy ? <span className="spinner" /> : mode === "login" ? "Entrar" : "Criar conta e entrar"}
            </button>
          </form>

          {mode === "login" && (
            <div className="demo-accounts">
              <span className="label small">Contas de demonstração (senha Velo@2026)</span>
              {DEMO_ACCOUNTS.map((a) => (
                <button
                  key={a.email}
                  type="button"
                  className="demo-account"
                  onClick={() => {
                    setEmail(a.email);
                    setPassword(a.password);
                    setError(null);
                  }}
                >
                  <span>
                    <strong>{a.label}</strong>
                    <span className="muted small">{a.email}</span>
                  </span>
                  <span className="muted small demo-account-text">{a.text}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <p className="login-foot">
          A sessão dura até {maxMinutes} minutos e termina após {idleMinutes} minutos sem uso ou ao recarregar a página.
        </p>
      </section>
    </div>
  );
}
