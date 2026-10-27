import { useState } from "react";
import { Icon } from "../components/Icon";
import { useToast } from "../components/Toast";
import { download } from "../lib/api";

export function ExportButton({ path, name, label = "Exportar CSV" }: { path: string; name: string; label?: string }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  return (
    <button
      className="btn ghost"
      disabled={busy}
      title="Planilha compatível com Excel (separador ponto e vírgula)"
      onClick={async () => {
        setBusy(true);
        try {
          const file = await download(path, name);
          toast.show(`${file} baixado`);
        } catch (e) {
          toast.error(e);
        } finally {
          setBusy(false);
        }
      }}
    >
      {busy ? <span className="spinner" /> : <Icon name="download" size={16} />} {label}
    </button>
  );
}
