import { Link } from "react-router-dom";
import { Mark } from "../components/Logo";
import { Page } from "../components/Ui";

export function NotFoundPage() {
  return (
    <Page className="not-found">
      <Mark size={56} />
      <h1>Página não encontrada</h1>
      <p className="muted">O endereço pode ter mudado ou nunca ter existido.</p>
      <Link className="btn" to="/">
        Voltar para a loja
      </Link>
    </Page>
  );
}
