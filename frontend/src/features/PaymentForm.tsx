import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { Icon } from "../components/Icon";
import { Field } from "../components/Ui";
import { digits, maskCard, maskExpiry, methodLabel, money } from "../lib/format";
import type { PaymentMethod, PaymentOptions } from "../lib/types";

export interface PaymentDraft {
  method: PaymentMethod;
  installments: number;
  holder: string;
  number: string;
  expiry: string;
  cvv: string;
}

export const initialPayment = (o: PaymentOptions | null): PaymentDraft => ({
  method: o?.methods[0] ?? "CARD",
  installments: 1,
  holder: "",
  number: "",
  expiry: "",
  cvv: ""
});

const methodIcon: Record<PaymentMethod, string> = { CARD: "card", PIX: "pix", BOLETO: "barcode", CHECKOUT: "external" };
const methodHint: Record<PaymentMethod, string> = {
  CARD: "Aprovação na hora",
  PIX: "Código copia e cola",
  BOLETO: "Compensa em até 1 dia útil",
  CHECKOUT: "Página segura da Stripe"
};

export function paymentBody(d: PaymentDraft) {
  return {
    method: d.method,
    installments: d.method === "CARD" ? d.installments : 1,
    card:
      d.method === "CARD"
        ? { holder: d.holder.trim(), number: digits(d.number), expiry: d.expiry, cvv: digits(d.cvv) }
        : null
  };
}

export function paymentProblem(d: PaymentDraft): string | null {
  if (d.method !== "CARD") return null;
  if (d.holder.trim().length < 3) return "Informe o nome impresso no cartão.";
  if (digits(d.number).length < 13) return "Informe o número do cartão.";
  if (!/^\d{2}\/\d{2}$/.test(d.expiry)) return "Informe a validade no formato MM/AA.";
  if (digits(d.cvv).length < 3) return "Informe o código de segurança.";
  return null;
}

export function PaymentForm({
  options,
  value,
  onChange,
  total
}: {
  options: PaymentOptions;
  value: PaymentDraft;
  onChange: (v: PaymentDraft) => void;
  total: number;
}) {
  const [showCards, setShowCards] = useState(false);
  const set = <K extends keyof PaymentDraft>(k: K, v: PaymentDraft[K]) => onChange({ ...value, [k]: v });
  const installments = Array.from({ length: Math.max(1, options.maxInstallments) }, (_, i) => i + 1).filter(
    (n) => n === 1 || total / n >= 10
  );
  return (
    <div className="stack">
      <div className="method-grid" role="radiogroup" aria-label="Forma de pagamento">
        {options.methods.map((m) => (
          <button
            key={m}
            type="button"
            role="radio"
            aria-checked={value.method === m}
            className={`method ${value.method === m ? "on" : ""}`}
            onClick={() => set("method", m)}
          >
            <Icon name={methodIcon[m]} />
            <strong>{methodLabel[m]}</strong>
            <span className="muted small">{methodHint[m]}</span>
          </button>
        ))}
      </div>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={value.method}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -4 }}
          transition={{ duration: 0.22 }}
        >
          {value.method === "CARD" && (
            <div className="form-grid">
              <Field label="Número do cartão" className="span-4">
                <input
                  className="input num"
                  inputMode="numeric"
                  autoComplete="cc-number"
                  value={value.number}
                  onChange={(e) => set("number", maskCard(e.target.value))}
                  placeholder="0000 0000 0000 0000"
                />
              </Field>
              <Field label="Nome impresso" className="span-4">
                <input className="input" autoComplete="cc-name" value={value.holder} onChange={(e) => set("holder", e.target.value.toUpperCase())} />
              </Field>
              <Field label="Validade" className="span-1">
                <input className="input num" inputMode="numeric" autoComplete="cc-exp" value={value.expiry} onChange={(e) => set("expiry", maskExpiry(e.target.value))} placeholder="MM/AA" />
              </Field>
              <Field label="CVV" className="span-1">
                <input className="input num" inputMode="numeric" autoComplete="cc-csc" value={value.cvv} onChange={(e) => set("cvv", digits(e.target.value).slice(0, 4))} placeholder="123" />
              </Field>
              <Field label="Parcelas" className="span-2">
                <select className="input" value={value.installments} onChange={(e) => set("installments", Number(e.target.value))}>
                  {installments.map((n) => (
                    <option key={n} value={n}>
                      {n}× de {money(total / n)} sem juros
                    </option>
                  ))}
                </select>
              </Field>
              {options.simulated && (
                <div className="notice span-4">
                  <Icon name="shield" size={18} />
                  <div>
                    Pagamento simulado: nenhum valor é cobrado.{" "}
                    <button type="button" className="link" tabIndex={0} onKeyDown={(e) => e.key === "Enter" && e.currentTarget.click()} onClick={() => setShowCards((v) => !v)}>
                      {showCards ? "Esconder cartões de teste" : "Ver cartões de teste"}
                    </button>
                    {showCards && (
                      <ul className="test-cards">
                        {options.testCards.map((c) => (
                          <li key={c.number}>
                            <button
                              type="button"
                              className="text-link num"
                              onClick={() =>
                                onChange({
                                  ...value,
                                  number: maskCard(c.number),
                                  holder: value.holder || "CLARA CLIENTE",
                                  expiry: value.expiry || "12/30",
                                  cvv: value.cvv || "123"
                                })
                              }
                            >
                              {maskCard(c.number)}
                            </button>
                            <span className="muted"> {c.result}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}
          {value.method === "PIX" && (
            <p className="muted">Ao finalizar, você recebe um código PIX copia e cola válido por 30 minutos.</p>
          )}
          {value.method === "BOLETO" && <p className="muted">Ao finalizar, você recebe a linha digitável do boleto com vencimento em 3 dias.</p>}
          {value.method === "CHECKOUT" && (
            <p className="muted">Você será levado à página de pagamento da Stripe. Ao voltar, entre de novo para acompanhar o pedido.</p>
          )}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
