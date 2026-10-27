import { useRef, useState } from "react";
import { Field } from "../components/Ui";
import { ApiError, get } from "../lib/api";
import { digits, maskCep } from "../lib/format";
import type { Address } from "../lib/types";

export type AddressInput = Omit<Address, "id" | "isDefault"> & { isDefault?: boolean };

export const emptyAddress = (recipient = ""): AddressInput => ({
  label: "Casa",
  recipient,
  zipCode: "",
  street: "",
  number: "",
  complement: "",
  district: "",
  city: "",
  state: "",
  isDefault: false
});

export function AddressFields({
  value,
  onChange,
  errors = {},
  showDefault
}: {
  value: AddressInput;
  onChange: (v: AddressInput) => void;
  errors?: Record<string, string>;
  showDefault?: boolean;
}) {
  const set = <K extends keyof AddressInput>(k: K, v: AddressInput[K]) => onChange({ ...value, [k]: v });
  const [cepState, setCepState] = useState<{ kind: "idle" | "busy" | "ok" | "error"; text?: string }>({ kind: "idle" });
  const last = useRef("");
  const lookup = async (masked: string) => {
    const d = digits(masked);
    onChange({ ...value, zipCode: masked });
    if (d.length !== 8 || d === last.current) return;
    last.current = d;
    setCepState({ kind: "busy" });
    try {
      const r = await get<{ street: string; district: string; city: string; state: string }>(`/api/address/cep/${d}`);
      onChange({
        ...value,
        zipCode: masked,
        street: r.street || value.street,
        district: r.district || value.district,
        city: r.city || value.city,
        state: r.state || value.state
      });
      setCepState({ kind: "ok", text: `${r.city}/${r.state}` });
    } catch (e) {
      setCepState({ kind: "error", text: e instanceof ApiError ? e.message : "Preencha o endereço manualmente." });
    }
  };
  const cepHint =
    cepState.kind === "busy" ? (
      <span className="row tight">
        <span className="spinner sm" /> Buscando endereço…
      </span>
    ) : cepState.kind === "ok" ? (
      <span className="ok-text">Endereço preenchido · {cepState.text}</span>
    ) : cepState.kind === "error" ? (
      <span className="warn-text">{cepState.text}</span>
    ) : (
      "Preenchemos rua, bairro e cidade pelo CEP."
    );
  return (
    <div className="form-grid">
      <Field label="Apelido" className="span-2">
        <input className="input" value={value.label} maxLength={40} onChange={(e) => set("label", e.target.value)} placeholder="Casa, Trabalho…" />
      </Field>
      <Field label="CEP" error={errors.zipCode} hint={cepHint} className="span-2">
        <input className="input num" inputMode="numeric" autoComplete="postal-code" value={value.zipCode} onChange={(e) => lookup(maskCep(e.target.value))} placeholder="00000-000" />
      </Field>
      <Field label="Quem recebe" error={errors.recipient} className="span-4">
        <input className="input" value={value.recipient} maxLength={120} onChange={(e) => set("recipient", e.target.value)} />
      </Field>
      <Field label="Rua" error={errors.street} className="span-3">
        <input className="input" value={value.street} maxLength={160} onChange={(e) => set("street", e.target.value)} />
      </Field>
      <Field label="Número" error={errors.number} className="span-1">
        <input className="input" value={value.number} maxLength={20} onChange={(e) => set("number", e.target.value)} />
      </Field>
      <Field label="Complemento" className="span-2">
        <input className="input" value={value.complement ?? ""} maxLength={80} onChange={(e) => set("complement", e.target.value)} />
      </Field>
      <Field label="Bairro" error={errors.district} className="span-2">
        <input className="input" value={value.district} maxLength={80} onChange={(e) => set("district", e.target.value)} />
      </Field>
      <Field label="Cidade" error={errors.city} className="span-3">
        <input className="input" value={value.city} maxLength={80} onChange={(e) => set("city", e.target.value)} />
      </Field>
      <Field label="UF" error={errors.state} className="span-1">
        <input className="input" value={value.state} maxLength={2} onChange={(e) => set("state", e.target.value.toUpperCase().replace(/[^A-Z]/g, ""))} />
      </Field>
      {showDefault && (
        <label className="check span-4">
          <input type="checkbox" checked={Boolean(value.isDefault)} onChange={(e) => set("isDefault", e.target.checked)} />
          Usar como endereço principal
        </label>
      )}
    </div>
  );
}

export function fieldErrors(e: unknown, prefix = ""): Record<string, string> {
  if (!(e instanceof ApiError)) return {};
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(e.fields)) out[prefix && k.startsWith(prefix) ? k.slice(prefix.length) : k] = v;
  return out;
}

export function AddressText({ a }: { a: Pick<Address, "street" | "number" | "complement" | "district" | "city" | "state" | "zipCode"> }) {
  return (
    <span className="address-text">
      {a.street}, {a.number}
      {a.complement ? ` · ${a.complement}` : ""}
      <br />
      {a.district} · {a.city}/{a.state} · <span className="num">{a.zipCode}</span>
    </span>
  );
}
