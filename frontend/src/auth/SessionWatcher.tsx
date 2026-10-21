import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { api } from "../lib/api";
import { clock } from "../lib/format";
import { useAuth } from "./AuthContext";

const WARN_MS = 60_000;
const PING_EVERY_MS = 60_000;

export function SessionWatcher() {
  const { user, idleMinutes, logout, timing, touch } = useAuth();
  const [now, setNow] = useState(Date.now());
  const pinging = useRef(false);

  useEffect(() => {
    if (!user) return;
    let last = 0;
    const onActivity = () => {
      const t = Date.now();
      if (t - last < 800) return;
      last = t;
      touch();
    };
    const events = ["pointerdown", "pointermove", "keydown", "wheel", "touchstart", "scroll"] as const;
    events.forEach((e) => window.addEventListener(e, onActivity, { passive: true }));
    return () => events.forEach((e) => window.removeEventListener(e, onActivity));
  }, [user, touch]);

  useEffect(() => {
    if (!user) return;
    const id = window.setInterval(() => {
      const t = Date.now();
      setNow(t);
      const s = timing.current;
      const idleMs = idleMinutes * 60_000;
      if (s.expiresAt && t >= s.expiresAt) {
        logout("EXPIRED");
        return;
      }
      if (t - s.lastActivity >= idleMs) {
        logout("IDLE");
        return;
      }
      const activeSincePing = s.lastActivity > s.lastPing;
      if (activeSincePing && t - s.lastPing >= PING_EVERY_MS && !pinging.current) {
        pinging.current = true;
        s.lastPing = t;
        api("/api/auth/ping", { method: "POST" })
          .catch(() => {})
          .finally(() => {
            pinging.current = false;
          });
      }
    }, 1000);
    return () => window.clearInterval(id);
  }, [user, idleMinutes, logout, timing]);

  if (!user) return null;
  const s = timing.current;
  const idleLeft = idleMinutes * 60_000 - (now - s.lastActivity);
  const maxLeft = s.expiresAt - now;
  const idleWarning = idleLeft <= WARN_MS && idleLeft > 0;
  const maxWarning = !idleWarning && maxLeft <= WARN_MS && maxLeft > 0;

  return (
    <>
      <AnimatePresence>
        {idleWarning && (
          <motion.div className="overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <motion.div
              className="modal"
              role="alertdialog"
              aria-labelledby="idle-title"
              initial={{ opacity: 0, y: 12, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            >
              <h2 id="idle-title">Você ainda está aí?</h2>
              <p className="muted">
                Por segurança, a sessão termina depois de {idleMinutes} minutos sem atividade. Faltam{" "}
                <strong className="num">{clock(idleLeft)}</strong>.
              </p>
              <div className="actions">
                <button className="btn ghost" onClick={() => logout("LOGOUT")}>
                  Sair agora
                </button>
                <button
                  className="btn"
                  autoFocus
                  onClick={() => {
                    touch();
                    timing.current.lastPing = Date.now();
                    api("/api/auth/ping", { method: "POST" }).catch(() => {});
                  }}
                >
                  Continuar conectado
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {maxWarning && (
          <motion.div
            className="session-banner"
            role="status"
            initial={{ opacity: 0, y: -16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -16 }}
            transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
          >
            <span className="dot" />
            Sua sessão chega ao limite em <strong className="num">{clock(maxLeft)}</strong>. Depois disso, é só entrar de novo.
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

export function useSessionClock() {
  const { timing, user } = useAuth();
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!user) return;
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [user]);
  return Math.max(0, timing.current.expiresAt - now);
}
