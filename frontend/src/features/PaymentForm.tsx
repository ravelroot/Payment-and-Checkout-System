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
  CARD: "Até 10x sem juros",
  PIX: "QR Code, aprovação na hora",
  BOLETO: "Compensa em até 1 dia útil",
  CHECKOUT: "Página segura do provedor"
};

export const providerName = (p: PaymentOptions["provider"] | string) =>
  p === "STRIPE" ? "Stripe" : p === "MERCADO_PAGO" ? "Mercado Pago" : "Simulado";

export type CardBrand = "Visa" | "Mastercard" | "Elo" | "Amex" | "Hipercard" | null;

export function cardBrand(number: string): CardBrand {
  const n = digits(number);
  if (/^(4011|4312|4389|4514|4576|5041|5066|5067|509|6277|6362|6363|650|6516|6550)/.test(n)) return "Elo";
  if (/^(606282|3841)/.test(n)) return "Hipercard";
  if (/^3[47]/.test(n)) return "Amex";
  if (/^4/.test(n)) return "Visa";
  if (/^(5[1-5]|2[2-7])/.test(n)) return "Mastercard";
  return null;
}

export function luhn(number: string) {
  const n = digits(number);
  if (n.length < 13) return false;
  let sum = 0;
  for (let i = 0; i < n.length; i++) {
    let d = Number(n[n.length - 1 - i]);
    if (i % 2 === 1) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
  }
  return sum % 10 === 0;
}

function CardPreview({ value, flipped }: { value: PaymentDraft; flipped: boolean }) {
  const brand = cardBrand(value.number);
  const shown = (digits(value.number) + "•".repeat(16)).slice(0, 16).replace(/(.{4})/g, "$1 ").trim();
  return (
    <div className={`card-preview ${flipped ? "flipped" : ""} ${brand ? "brand-" + brand.toLowerCase() : ""}`} aria-hidden>
      <div className="card-face front">
        <div className="row between-tight">
          <span className="card-chip" />
          <span className="card-brand">{brand ?? "Cartão"}</span>
        </div>
        <span className="card-number num">{shown}</span>
        <div className="row between-tight">
          <span className="card-holder">{value.holder || "NOME IMPRESSO"}</span>
          <span className="num">{value.expiry || "MM/AA"}</span>
        </div>
      </div>
      <div className="card-face back">
        <span className="card-stripe" />
        <span className="card-cvv num">{value.cvv ? "•".repeat(value.cvv.length) : "CVV"}</span>
      </div>
    </div>
  );
}

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
  if (!luhn(d.number)) return "Confira o número do cartão: ele não passou na validação.";
  const [mm, yy] = d.expiry.split("/").map(Number);
  if (mm < 1 || mm > 12) return "Mês de validade inválido.";
  const now = new Date();
  if (yy + 2000 < now.getFullYear() || (yy + 2000 === now.getFullYear() && mm < now.getMonth() + 1)) return "Este cartão está vencido.";
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
  const [cvvFocus, setCvvFocus] = useState(false);
  const typed = digits(value.number).length;
  const valid = typed >= 13 && luhn(value.number);
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
            <strong>{m === "CHECKOUT" ? providerName(options.provider) : methodLabel[m]}</strong>
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
            <div className="card-entry">
            <CardPreview value={value} flipped={cvvFocus} />
            <div className="form-grid">
              <Field
                label="Número do cartão"
                className="span-4"
                error={typed >= 13 && !valid ? "Número inválido. Confira os dígitos." : undefined}
                hint={valid ? <span className="ok-text"><Icon name="check" size={13} /> {cardBrand(value.number) ?? "Cartão"} · número válido</span> : undefined}
              >
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
                <input
                  className="input num"
                  inputMode="numeric"
                  autoComplete="cc-csc"
                  value={value.cvv}
                  onFocus={() => setCvvFocus(true)}
                  onBlur={() => setCvvFocus(false)}
                  onChange={(e) => set("cvv", digits(e.target.value).slice(0, 4))}
                  placeholder="123"
                />
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
            </div>
          )}
          {value.method === "PIX" && (
            <div className="method-note">
              <Icon name="pix" size={18} />
              <p>
                Ao finalizar, você recebe um <strong>QR Code</strong> e o código copia e cola, válidos por 30 minutos. A confirmação é
                imediata e o pedido segue para separação.
              </p>
            </div>
          )}
          {value.method === "BOLETO" && (
            <div className="method-note">
              <Icon name="barcode" size={18} />
              <p>Ao finalizar, você recebe a linha digitável do boleto com vencimento em 3 dias. O pedido é reservado até a compensação.</p>
            </div>
          )}
          {value.method === "CHECKOUT" && (
            <div className="method-note">
              <Icon name="external" size={18} />
              <p>
                Você será levado à página segura do <strong>{providerName(options.provider)}</strong> para pagar com cartão, PIX ou boleto. Ao
                voltar, entre de novo para acompanhar o pedido.
              </p>
            </div>
          )}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
