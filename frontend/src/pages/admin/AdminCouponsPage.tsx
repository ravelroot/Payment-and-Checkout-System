import { useState } from "react";
import { Icon } from "../../components/Icon";
import { Confirm, Modal } from "../../components/Modal";
import { Empty, Field, Page, PageHeader, Skeleton } from "../../components/Ui";
import { useToast } from "../../components/Toast";
import { fieldErrors } from "../../features/AddressForm";
import { del, post, put } from "../../lib/api";
import { date, money } from "../../lib/format";
import type { Coupon } from "../../lib/types";
import { useApi } from "../../lib/useApi";

type Draft = {
  id: number | null;
  code: string;
  description: string;
  type: Coupon["type"];
  value: string;
  minSubtotal: string;
  maxUses: string;
  startsAt: string;
  endsAt: string;
  active: boolean;
};

const num = (v: string) => (v.trim() === "" ? null : Number(v.replace(",", ".")));
const day = (v: string | null) => (v ? new Date(v).toLocaleDateString("sv-SE", { timeZone: "America/Sao_Paulo" }) : "");
const startOf = (v: string) => (v ? new Date(`${v}T00:00:00-03:00`).toISOString() : null);
const endOf = (v: string) => (v ? new Date(`${v}T23:59:59-03:00`).toISOString() : null);

export function AdminCouponsPage() {
  const toast = useToast();
  const { data, reload } = useApi<Coupon[]>("/api/admin/coupons");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [removing, setRemoving] = useState<Coupon | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const edit = (c?: Coupon) => {
    setErrors({});
    setDraft(
      c
        ? {
            id: c.id,
            code: c.code,
            description: c.description ?? "",
            type: c.type,
            value: String(c.value).replace(".", ","),
            minSubtotal: c.minSubtotal ? String(c.minSubtotal).replace(".", ",") : "",
            maxUses: c.maxUses ? String(c.maxUses) : "",
            startsAt: day(c.startsAt),
            endsAt: day(c.endsAt),
            active: c.active
          }
        : { id: null, code: "", description: "", type: "PERCENT", value: "", minSubtotal: "", maxUses: "", startsAt: "", endsAt: "", active: true }
    );
  };

  const save = async () => {
    if (!draft) return;
    setBusy(true);
    setErrors({});
    const body = {
      code: draft.code,
      description: draft.description.trim() || null,
      type: draft.type,
      value: num(draft.value),
      minSubtotal: num(draft.minSubtotal),
      maxUses: draft.maxUses ? Number(draft.maxUses) : null,
      startsAt: startOf(draft.startsAt),
      endsAt: endOf(draft.endsAt),
      active: draft.active
    };
    try {
      if (draft.id) await put(`/api/admin/coupons/${draft.id}`, body);
      else await post("/api/admin/coupons", body);
      toast.show(draft.id ? "Cupom salvo" : "Cupom criado");
      setDraft(null);
      reload();
    } catch (e) {
      setErrors(fieldErrors(e));
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!removing) return;
    setBusy(true);
    try {
      await del(`/api/admin/coupons/${removing.id}`);
      toast.show("Cupom excluído");
      setRemoving(null);
      reload();
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Page>
      <div className="wrap wide">
        <PageHeader
          title="Cupons"
          actions={
            <button className="btn" onClick={() => edit()}>
              <Icon name="plus" size={16} /> Novo cupom
            </button>
          }
        />
        {!data ? (
          <Skeleton h={160} r={18} />
        ) : data.length === 0 ? (
          <div className="card">
            <Empty icon="ticket" title="Nenhum cupom" text="Crie um cupom para oferecer desconto no carrinho." />
          </div>
        ) : (
          <div className="coupon-grid">
            {data.map((c) => (
              <button key={c.id} className={`coupon-card ${c.active ? "" : "off"}`} onClick={() => edit(c)}>
                <div className="coupon-top">
                  <strong className="num">{c.code}</strong>
                  <span className={`pill ${c.active ? "ok" : "line"}`}>
                    <span className="dot" />
                    {c.active ? "Ativo" : "Inativo"}
                  </span>
                </div>
                <span className="coupon-value num">{c.type === "PERCENT" ? `${String(c.value).replace(".", ",")}%` : money(c.value)}</span>
                <span className="muted small">{c.description ?? "Sem descrição"}</span>
                <span className="coupon-meta small">
                  {c.minSubtotal ? `Mínimo ${money(c.minSubtotal)}` : "Sem valor mínimo"}
                  <br />
                  {c.usedCount} usos{c.maxUses ? ` de ${c.maxUses}` : ""}
                  {c.endsAt ? ` · até ${date(c.endsAt)}` : ""}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
      <Modal
        open={draft !== null}
        onClose={() => setDraft(null)}
        title={draft?.id ? "Editar cupom" : "Novo cupom"}
        width={600}
        actions={
          <>
            {draft?.id && (
              <button
                className="btn quiet"
                onClick={() => {
                  const c = data?.find((x) => x.id === draft.id) ?? null;
                  setDraft(null);
                  setRemoving(c);
                }}
              >
                Excluir
              </button>
            )}
            <span className="spacer" />
            <button className="btn ghost" onClick={() => setDraft(null)}>
              Voltar
            </button>
            <button className="btn" onClick={save} disabled={busy}>
              {busy ? <span className="spinner" /> : draft?.id ? "Salvar cupom" : "Criar cupom"}
            </button>
          </>
        }
      >
        {draft && (
          <div className="form-grid">
            <Field label="Código" error={errors.code} className="span-2">
              <input className="input num" value={draft.code} maxLength={30} onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase().replace(/\s/g, "") })} autoFocus />
            </Field>
            <Field label="Tipo" className="span-1">
              <select className="input" value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value as Coupon["type"] })}>
                <option value="PERCENT">Percentual</option>
                <option value="FIXED">Valor fixo</option>
              </select>
            </Field>
            <Field label={draft.type === "PERCENT" ? "Desconto (%)" : "Desconto (R$)"} error={errors.value} className="span-1">
              <input className="input num" inputMode="decimal" value={draft.value} onChange={(e) => setDraft({ ...draft, value: e.target.value })} />
            </Field>
            <Field label="Descrição" className="span-4">
              <input className="input" value={draft.description} maxLength={200} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
            </Field>
            <Field label="Pedido mínimo (R$)" className="span-2">
              <input className="input num" inputMode="decimal" value={draft.minSubtotal} onChange={(e) => setDraft({ ...draft, minSubtotal: e.target.value })} />
            </Field>
            <Field label="Limite de usos" hint="Vazio é ilimitado." className="span-2">
              <input className="input num" inputMode="numeric" value={draft.maxUses} onChange={(e) => setDraft({ ...draft, maxUses: e.target.value.replace(/\D/g, "") })} />
            </Field>
            <Field label="Começa em" className="span-2">
              <input className="input" type="date" value={draft.startsAt} onChange={(e) => setDraft({ ...draft, startsAt: e.target.value })} />
            </Field>
            <Field label="Termina em" error={errors.endsAt} className="span-2">
              <input className="input" type="date" value={draft.endsAt} onChange={(e) => setDraft({ ...draft, endsAt: e.target.value })} />
            </Field>
            <label className="check span-4">
              <input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
              Cupom ativo
            </label>
          </div>
        )}
      </Modal>
      <Confirm
        open={removing !== null}
        title="Excluir cupom?"
        message={removing ? `${removing.code} deixa de valer. Pedidos que já usaram o cupom continuam com o desconto.` : ""}
        confirmText="Excluir"
        danger
        busy={busy}
        onConfirm={remove}
        onClose={() => setRemoving(null)}
      />
    </Page>
  );
}
