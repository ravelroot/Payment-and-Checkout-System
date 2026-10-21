import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { Icon } from "../../components/Icon";
import { Modal } from "../../components/Modal";
import { Empty, Field, Page, PageHeader, Pagination, Skeleton } from "../../components/Ui";
import { useToast } from "../../components/Toast";
import { fieldErrors } from "../../features/AddressForm";
import { post, put, qs } from "../../lib/api";
import { dateTime, maskPhone, relative, roleLabel } from "../../lib/format";
import type { AdminUser, Page as PageOf, Role } from "../../lib/types";
import { useApi, useDebounced } from "../../lib/useApi";

type Draft = { id: number | null; fullName: string; email: string; role: Role; active: boolean; phone: string; document: string; password: string };

const roles = Object.keys(roleLabel) as Role[];

export function AdminUsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get("q") ?? "");
  const term = useDebounced(q.trim(), 300);
  const role = (params.get("perfil") as Role | null) ?? null;
  const page = Number(params.get("pagina") ?? 0);
  const { data, loading, reload } = useApi<PageOf<AdminUser>>("/api/admin/users" + qs({ q: term, role, page, size: 20 }));
  const [draft, setDraft] = useState<Draft | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [newPassword, setNewPassword] = useState("");

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("pagina" in patch)) next.delete("pagina");
    setParams(next, { replace: true });
  };

  const edit = (u?: AdminUser) => {
    setErrors({});
    setNewPassword("");
    setDraft(
      u
        ? { id: u.id, fullName: u.fullName, email: u.email, role: u.role, active: u.active, phone: u.phone ?? "", document: u.document ?? "", password: "" }
        : { id: null, fullName: "", email: "", role: "CUSTOMER", active: true, phone: "", document: "", password: "" }
    );
  };

  const save = async () => {
    if (!draft) return;
    setBusy(true);
    setErrors({});
    const body = {
      fullName: draft.fullName,
      email: draft.email,
      role: draft.role,
      active: draft.active,
      phone: draft.phone.trim() || null,
      document: draft.document.trim() || null,
      password: draft.id ? null : draft.password
    };
    try {
      if (draft.id) await put(`/api/admin/users/${draft.id}`, body);
      else await post("/api/admin/users", body);
      toast.show(draft.id ? "Usuário salvo" : "Usuário criado");
      setDraft(null);
      reload();
    } catch (e) {
      setErrors(fieldErrors(e));
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const resetPassword = async () => {
    if (!draft?.id) return;
    setBusy(true);
    try {
      await put(`/api/admin/users/${draft.id}/password`, { password: newPassword });
      toast.show("Senha redefinida. As sessões abertas dessa pessoa foram encerradas.");
      setNewPassword("");
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const revoke = async () => {
    if (!draft?.id) return;
    setBusy(true);
    try {
      const r = await post<{ revoked: number }>(`/api/admin/users/${draft.id}/sessions/revoke`);
      toast.show(r.revoked ? `${r.revoked} sessões encerradas` : "Nenhuma sessão aberta");
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const self = draft?.id === me?.id;

  return (
    <Page>
      <div className="wrap wide">
        <PageHeader
          title="Usuários"
          subtitle={data ? `${data.total} pessoas` : undefined}
          actions={
            <button className="btn" onClick={() => edit()}>
              <Icon name="plus" size={16} /> Novo usuário
            </button>
          }
        />
        <div className="toolbar">
          <label className="search">
            <Icon name="search" size={18} />
            <input
              className="input"
              placeholder="Nome ou e-mail"
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                update({ q: e.target.value.trim() || null });
              }}
              aria-label="Buscar usuários"
            />
          </label>
          <div className="segmented" role="tablist" aria-label="Perfil">
            <button role="tab" aria-selected={!role} className={!role ? "on" : ""} onClick={() => update({ perfil: null })}>
              Todos
            </button>
            {roles.map((r) => (
              <button key={r} role="tab" aria-selected={role === r} className={role === r ? "on" : ""} onClick={() => update({ perfil: r })}>
                {roleLabel[r]}
              </button>
            ))}
          </div>
        </div>
        <div className="card">
          {loading && !data ? (
            <div className="pad stack">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} h={40} />
              ))}
            </div>
          ) : data && data.items.length ? (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Pessoa</th>
                    <th>Perfil</th>
                    <th className="right">Pedidos</th>
                    <th>Último acesso</th>
                    <th>Situação</th>
                  </tr>
                </thead>
                <tbody className={loading ? "loading" : ""}>
                  {data.items.map((u) => (
                    <tr key={u.id} className="link" tabIndex={0} onKeyDown={(e) => e.key === "Enter" && e.currentTarget.click()} onClick={() => edit(u)}>
                      <td>
                        <div className="cell-product">
                          <span className="avatar">{u.fullName.slice(0, 1)}</span>
                          <div>
                            {u.fullName}
                            {u.id === me?.id && <span className="chip">Você</span>}
                            <div className="muted small">{u.email}</div>
                          </div>
                        </div>
                      </td>
                      <td>{roleLabel[u.role]}</td>
                      <td className="right num">{u.orders}</td>
                      <td className="muted small">{u.lastLoginAt ? relative(u.lastLoginAt) : "Nunca"}</td>
                      <td>
                        <span className={`pill ${u.active ? "ok" : "line"}`}>
                          <span className="dot" />
                          {u.active ? "Ativo" : "Bloqueado"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty icon="users" title="Ninguém encontrado" text="Ajuste a busca ou o filtro de perfil." />
          )}
        </div>
        {data && <Pagination page={data.page} pages={data.pages} total={data.total} onPage={(p) => update({ pagina: String(p) })} />}
      </div>
      <Modal
        open={draft !== null}
        onClose={() => setDraft(null)}
        title={draft?.id ? "Editar usuário" : "Novo usuário"}
        width={620}
        actions={
          <>
            <button className="btn ghost" onClick={() => setDraft(null)}>
              Voltar
            </button>
            <button className="btn" onClick={save} disabled={busy}>
              {busy ? <span className="spinner" /> : draft?.id ? "Salvar usuário" : "Criar usuário"}
            </button>
          </>
        }
      >
        {draft && (
          <div className="stack">
            <div className="form-grid">
              <Field label="Nome completo" error={errors.fullName} className="span-4">
                <input className="input" value={draft.fullName} maxLength={120} onChange={(e) => setDraft({ ...draft, fullName: e.target.value })} />
              </Field>
              <Field label="E-mail" error={errors.email} className="span-4">
                <input className="input" type="email" value={draft.email} maxLength={160} onChange={(e) => setDraft({ ...draft, email: e.target.value })} />
              </Field>
              <Field label="Perfil" hint={self ? "Você não pode tirar o seu próprio acesso de administrador." : undefined} className="span-2">
                <select className="input" value={draft.role} disabled={self} onChange={(e) => setDraft({ ...draft, role: e.target.value as Role })}>
                  {roles.map((r) => (
                    <option key={r} value={r}>
                      {roleLabel[r]}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Telefone" className="span-2">
                <input className="input num" value={draft.phone} onChange={(e) => setDraft({ ...draft, phone: maskPhone(e.target.value) })} />
              </Field>
              {!draft.id && (
                <Field label="Senha inicial" error={errors.password} hint="Mínimo de 8 caracteres, com letras e números." className="span-4">
                  <input className="input" type="password" autoComplete="new-password" value={draft.password} onChange={(e) => setDraft({ ...draft, password: e.target.value })} />
                </Field>
              )}
              <label className="check span-4">
                <input type="checkbox" checked={draft.active} disabled={self} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
                Pode entrar na Velo
              </label>
            </div>
            {draft.id && !self && (
              <div className="subpanel">
                <h4>Acesso</h4>
                <div className="row">
                  <input
                    className="input grow"
                    type="password"
                    autoComplete="new-password"
                    placeholder="Nova senha"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    aria-label="Nova senha"
                  />
                  <button className="btn ghost" disabled={busy || newPassword.length < 8} onClick={resetPassword}>
                    Redefinir senha
                  </button>
                </div>
                <button className="btn quiet sm" disabled={busy} onClick={revoke}>
                  <Icon name="logout" size={15} /> Encerrar sessões abertas
                </button>
                {data?.items.find((u) => u.id === draft.id)?.createdAt && (
                  <span className="muted small">Cadastro em {dateTime(data.items.find((u) => u.id === draft.id)!.createdAt)}</span>
                )}
              </div>
            )}
          </div>
        )}
      </Modal>
    </Page>
  );
}
