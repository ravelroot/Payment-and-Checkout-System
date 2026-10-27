import { useState } from "react";
import { Icon } from "../components/Icon";
import { ApiError, get } from "../lib/api";
import { digits, maskCep, money } from "../lib/format";

export interface ShippingQuote {
  cep: string;
  region: string;
  state: string | null;
  cost: number;
  free: boolean;
  freeAbove: number;
  minDays: number;
  maxDays: number;
}

const CEP_KEY = "velo.cep";

export function rememberedCep() {
  try {
    return localStorage.getItem(CEP_KEY) ?? "";
  } catch {
    return "";
  }
}

export function ShippingEstimate({ subtotal }: { subtotal: number }) {
  const [cep, setCep] = useState(rememberedCep());
  const [quote, setQuote] = useState<ShippingQuote | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const calc = async () => {
    if (digits(cep).length !== 8) {
      setError("Informe um CEP com 8 dígitos.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const q = await get<ShippingQuote>(`/api/shipping/quote?cep=${digits(cep)}&subtotal=${subtotal.toFixed(2)}`);
      setQuote(q);
      try {
        localStorage.setItem(CEP_KEY, cep);
      } catch {
        /* sem armazenamento */
      }
    } catch (e) {
      setQuote(null);
      setError(e instanceof ApiError ? e.message : "Não foi possível calcular agora.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="ship-estimate">
      <form
        className="ship-form"
        onSubmit={(e) => {
          e.preventDefault();
          calc();
        }}
      >
        <label htmlFor="ship-cep" className="label">
          <Icon name="truck" size={16} /> Frete e prazo
        </label>
        <div className="row">
          <input
            id="ship-cep"
            className="input num"
            inputMode="numeric"
            placeholder="00000-000"
            value={cep}
            onChange={(e) => setCep(maskCep(e.target.value))}
            aria-invalid={error ? true : undefined}
          />
          <button className="btn ghost" disabled={busy}>
            {busy ? <span className="spinner" /> : "Calcular"}
          </button>
        </div>
      </form>
      {error && <span className="field-error">{error}</span>}
      {quote && (
        <div className="ship-result">
          <div>
            <strong>{quote.free ? "Frete grátis" : money(quote.cost)}</strong>
            <span className="muted small">
              {quote.region}
              {quote.state ? ` · ${quote.state}` : ""}
            </span>
          </div>
          <span className="num">
            {quote.minDays} a {quote.maxDays} dias úteis
          </span>
          {!quote.free && (
            <span className="muted small span-all">
              Faltam {money(Math.max(0, quote.freeAbove - subtotal))} para o frete grátis.
            </span>
          )}
        </div>
      )}
    </div>
  );
}
