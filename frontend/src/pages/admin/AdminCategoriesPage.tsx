import { useState } from "react";
import { Icon } from "../../components/Icon";
import { Confirm, Modal } from "../../components/Modal";
import { Empty, Field, Page, PageHeader, Skeleton } from "../../components/Ui";
import { useToast } from "../../components/Toast";
import { fieldErrors } from "../../features/AddressForm";
import { del, post, put } from "../../lib/api";
import type { Category } from "../../lib/types";
import { useApi } from "../../lib/useApi";

type Draft = { id: number | null; name: string; description: string; sortOrder: string; active: boolean };

export function AdminCategoriesPage() {
  const toast = useToast();
  const { data, reload } = useApi<Category[]>("/api/admin/categories");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [removing, setRemoving] = useState<Category | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const edit = (c?: Category) => {
    setErrors({});
    setDraft(
      c
        ? { id: c.id, name: c.name, description: c.description ?? "", sortOrder: String(c.sortOrder), active: c.active }
        : { id: null, name: "", description: "", sortOrder: String((data?.length ?? 0) + 1), active: true }
    );
  };

  const save = async () => {
    if (!draft) return;
    setBusy(true);
    setErrors({});
    const body = { name: draft.name, description: draft.description.trim() || null, sortOrder: Number(draft.sortOrder || 0), active: draft.active };
    try {
      if (draft.id) await put(`/api/admin/categories/${draft.id}`, body);
      else await post("/api/admin/categories", body);
      toast.show(draft.id ? "Categoria salva" : "Categoria criada");
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
      await del(`/api/admin/categories/${removing.id}`);
      toast.show("Categoria excluída");
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
          title="Categorias"
          subtitle="A ordem define a sequência dos filtros na loja."
          actions={
            <button className="btn" onClick={() => edit()}>
              <Icon name="plus" size={16} /> Nova categoria
            </button>
          }
        />
        <div className="card">
          {!data ? (
            <div className="pad stack">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} h={40} />
              ))}
            </div>
          ) : data.length === 0 ? (
            <Empty icon="layers" title="Nenhuma categoria" text="Crie categorias para organizar a vitrine." />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th className="right">Ordem</th>
                    <th>Nome</th>
                    <th className="right">Produtos</th>
                    <th>Situação</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.map((c) => (
                    <tr key={c.id} className="link" tabIndex={0} onKeyDown={(e) => e.key === "Enter" && e.currentTarget.click()} onClick={() => edit(c)}>
                      <td className="right num muted">{c.sortOrder}</td>
                      <td>
                        {c.name}
                        {c.description && <div className="muted small">{c.description}</div>}
                      </td>
                      <td className="right num">{c.products}</td>
                      <td>
                        <span className={`pill ${c.active ? "ok" : "line"}`}>
                          <span className="dot" />
                          {c.active ? "Ativa" : "Oculta"}
                        </span>
                      </td>
                      <td className="right">
                        <button
                          className="icon-btn"
                          aria-label={`Excluir ${c.name}`}
                          onClick={(e) => {
                            e.stopPropagation();
                            setRemoving(c);
                          }}
                        >
                          <Icon name="trash" size={17} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
      <Modal
        open={draft !== null}
        onClose={() => setDraft(null)}
        title={draft?.id ? "Editar categoria" : "Nova categoria"}
        actions={
          <>
            <button className="btn ghost" onClick={() => setDraft(null)}>
              Voltar
            </button>
            <button className="btn" onClick={save} disabled={busy}>
              {busy ? <span className="spinner" /> : draft?.id ? "Salvar categoria" : "Criar categoria"}
            </button>
          </>
        }
      >
        {draft && (
          <div className="form-grid">
            <Field label="Nome" error={errors.name} className="span-3">
              <input className="input" value={draft.name} maxLength={80} onChange={(e) => setDraft({ ...draft, name: e.target.value })} autoFocus />
            </Field>
            <Field label="Ordem" className="span-1">
              <input className="input num" inputMode="numeric" value={draft.sortOrder} onChange={(e) => setDraft({ ...draft, sortOrder: e.target.value.replace(/\D/g, "") })} />
            </Field>
            <Field label="Descrição" className="span-4">
              <textarea className="input" rows={2} maxLength={400} value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
            </Field>
            <label className="check span-4">
              <input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
              Mostrar na loja
            </label>
          </div>
        )}
      </Modal>
      <Confirm
        open={removing !== null}
        title="Excluir categoria?"
        message={removing ? `${removing.products} produtos ficam sem categoria. Eles continuam à venda.` : ""}
        confirmText="Excluir"
        danger
        busy={busy}
        onConfirm={remove}
        onClose={() => setRemoving(null)}
      />
    </Page>
  );
}
