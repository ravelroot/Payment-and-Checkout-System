import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { Icon } from "../components/Icon";
import { Confirm, Modal } from "../components/Modal";
import { Empty, Field, Page, PageHeader, Skeleton } from "../components/Ui";
import { useToast } from "../components/Toast";
import { AddressFields, AddressText, emptyAddress, fieldErrors, type AddressInput } from "../features/AddressForm";
import { del, post, put } from "../lib/api";
import { dateTime, maskPhone, roleLabel } from "../lib/format";
import type { Address, Profile } from "../lib/types";
import { useApi } from "../lib/useApi";

export function AccountPage() {
  return (
    <Page>
      <div className="wrap narrow">
        <PageHeader title="Minha conta" />
        <div className="stack lg">
          <ProfileCard />
          <AddressesCard />
          <PasswordCard />
        </div>
      </div>
    </Page>
  );
}

function ProfileCard() {
  const { data, setData } = useApi<Profile>("/api/account");
  const { refreshMe } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ fullName: "", phone: "", document: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (data) setForm({ fullName: data.fullName, phone: data.phone ?? "", document: data.document ?? "" });
  }, [data]);

  if (!data) return <Skeleton h={220} r={20} />;
  const dirty = form.fullName !== data.fullName || form.phone !== (data.phone ?? "") || form.document !== (data.document ?? "");

  const save = async () => {
    setBusy(true);
    setErrors({});
    try {
      const v = await put<Profile>("/api/account", form);
      setData(v);
      await refreshMe();
      toast.show("Dados salvos");
    } catch (e) {
      setErrors(fieldErrors(e));
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card pad">
      <div className="section-head">
        <h3>Dados pessoais</h3>
        <span className="chip">{roleLabel[data.role]}</span>
      </div>
      <div className="form-grid">
        <Field label="Nome completo" error={errors.fullName} className="span-4">
          <input className="input" value={form.fullName} maxLength={120} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
        </Field>
        <Field label="E-mail" hint="Para trocar o e-mail, fale com o administrador." className="span-4">
          <input className="input" value={data.email} disabled />
        </Field>
        <Field label="Telefone" className="span-2">
          <input className="input num" inputMode="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: maskPhone(e.target.value) })} />
        </Field>
        <Field label="CPF" className="span-2">
          <input className="input num" inputMode="numeric" maxLength={20} value={form.document} onChange={(e) => setForm({ ...form, document: e.target.value })} />
        </Field>
      </div>
      <div className="row between">
        <span className="muted small">
          Conta criada em {dateTime(data.createdAt)}
          {data.lastLoginAt ? ` · último acesso ${dateTime(data.lastLoginAt)}` : ""}
        </span>
        <button className="btn" disabled={!dirty || busy} onClick={save}>
          {busy ? <span className="spinner" /> : "Salvar dados"}
        </button>
      </div>
    </section>
  );
}

function AddressesCard() {
  const { user } = useAuth();
  const toast = useToast();
  const { data, reload } = useApi<Address[]>("/api/account/addresses");
  const [editing, setEditing] = useState<{ id: number | null; value: AddressInput } | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [removing, setRemoving] = useState<Address | null>(null);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (!editing) return;
    setBusy(true);
    setErrors({});
    try {
      if (editing.id) await put(`/api/account/addresses/${editing.id}`, editing.value);
      else await post("/api/account/addresses", editing.value);
      toast.show(editing.id ? "Endereço atualizado" : "Endereço salvo");
      setEditing(null);
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
      await del(`/api/account/addresses/${removing.id}`);
      toast.show("Endereço excluído");
      setRemoving(null);
      reload();
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card pad">
      <div className="section-head">
        <h3>Endereços</h3>
        <button
          className="btn ghost sm"
          onClick={() => {
            setErrors({});
            setEditing({ id: null, value: { ...emptyAddress(user?.fullName), isDefault: (data?.length ?? 0) === 0 } });
          }}
        >
          <Icon name="plus" size={15} /> Novo endereço
        </button>
      </div>
      {!data ? (
        <Skeleton h={80} />
      ) : data.length === 0 ? (
        <Empty icon="home" title="Nenhum endereço salvo" text="Cadastre um endereço para agilizar as próximas compras." />
      ) : (
        <ul className="address-list">
          <AnimatePresence initial={false}>
            {data.map((a) => (
              <motion.li key={a.id} layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <div>
                  <strong>
                    {a.label} {a.isDefault && <span className="chip">Principal</span>}
                  </strong>
                  <span className="muted small">{a.recipient}</span>
                  <AddressText a={a} />
                </div>
                <div className="row">
                  <button
                    className="icon-btn"
                    aria-label={`Editar ${a.label}`}
                    onClick={() => {
                      setErrors({});
                      setEditing({ id: a.id, value: { ...a, complement: a.complement ?? "" } });
                    }}
                  >
                    <Icon name="edit" size={17} />
                  </button>
                  <button className="icon-btn" aria-label={`Excluir ${a.label}`} onClick={() => setRemoving(a)}>
                    <Icon name="trash" size={17} />
                  </button>
                </div>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      )}
      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing?.id ? "Editar endereço" : "Novo endereço"}
        width={620}
        actions={
          <>
            <button className="btn ghost" onClick={() => setEditing(null)}>
              Voltar
            </button>
            <button className="btn" onClick={save} disabled={busy}>
              {busy ? <span className="spinner" /> : "Salvar endereço"}
            </button>
          </>
        }
      >
        {editing && <AddressFields value={editing.value} onChange={(v) => setEditing({ ...editing, value: v })} errors={errors} showDefault />}
      </Modal>
      <Confirm
        open={removing !== null}
        title="Excluir endereço?"
        message={removing ? `${removing.label}: ${removing.street}, ${removing.number}. Pedidos antigos continuam com o endereço que tinham.` : ""}
        confirmText="Excluir"
        danger
        busy={busy}
        onConfirm={remove}
        onClose={() => setRemoving(null)}
      />
    </section>
  );
}

function PasswordCard() {
  const toast = useToast();
  const [form, setForm] = useState({ currentPassword: "", newPassword: "", confirm: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const mismatch = form.confirm.length > 0 && form.confirm !== form.newPassword;

  const save = async () => {
    setBusy(true);
    setErrors({});
    try {
      await put("/api/account/password", { currentPassword: form.currentPassword, newPassword: form.newPassword });
      toast.show("Senha alterada");
      setForm({ currentPassword: "", newPassword: "", confirm: "" });
    } catch (e) {
      setErrors(fieldErrors(e));
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card pad">
      <div className="section-head">
        <h3>Senha</h3>
      </div>
      <div className="form-grid">
        <Field label="Senha atual" error={errors.currentPassword} className="span-4">
          <input className="input" type="password" autoComplete="current-password" value={form.currentPassword} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
        </Field>
        <Field label="Nova senha" error={errors.newPassword} hint="Mínimo de 8 caracteres, com letras e números." className="span-2">
          <input className="input" type="password" autoComplete="new-password" value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
        </Field>
        <Field label="Repita a nova senha" error={mismatch ? "As senhas não são iguais." : undefined} className="span-2">
          <input className="input" type="password" autoComplete="new-password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} />
        </Field>
      </div>
      <div className="row end">
        <button className="btn" disabled={busy || !form.currentPassword || !form.newPassword || mismatch || !form.confirm} onClick={save}>
          {busy ? <span className="spinner" /> : "Alterar senha"}
        </button>
      </div>
    </section>
  );
}
