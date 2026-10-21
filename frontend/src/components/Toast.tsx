import { AnimatePresence, motion } from "motion/react";
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";

type Toast = { id: number; text: string; kind: "info" | "error" };
type Ctx = { show: (text: string, kind?: Toast["kind"]) => void; error: (e: unknown) => void };

const ToastContext = createContext<Ctx | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);
  const seq = useRef(0);

  const show = useCallback((text: string, kind: Toast["kind"] = "info") => {
    const id = ++seq.current;
    setItems((list) => [...list.slice(-2), { id, text, kind }]);
    window.setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), kind === "error" ? 5200 : 3200);
  }, []);

  const error = useCallback(
    (e: unknown) => show(e instanceof Error ? e.message : "Algo deu errado. Tente de novo.", "error"),
    [show]
  );

  const value = useMemo(() => ({ show, error }), [show, error]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" aria-live="polite">
        <AnimatePresence initial={false}>
          {items.map((t) => (
            <motion.div
              key={t.id}
              layout
              className={`toast ${t.kind}`}
              initial={{ opacity: 0, y: 16, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            >
              <span className="dot" />
              {t.text}
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("ToastProvider ausente");
  return ctx;
}
