import { useState } from "react";
import { Icon } from "../../components/Icon";
import { useToast } from "../../components/Toast";
import { Page, PageHeader, Skeleton } from "../../components/Ui";
import { ApiError, get, post } from "../../lib/api";
import { dateTime, digits, maskCep } from "../../lib/format";
import { useApi } from "../../lib/useApi";

type ProviderId = "SIMULATED" | "STRIPE" | "MERCADO_PAGO";

interface Provider {
  id: ProviderId;
  name: string;
  configured: boolean;
  active: boolean;
  webhookUrl: string | null;
  webhookSecretConfigured: boolean;
}

interface Delivery {
  id: string;
  type: string;
  url: string;
  status: "SUCCESS" | "FAILED";
  httpStatus: number | null;
  attempts: number;
  durationMs: number;
  at: string;
  error: string | null;
}

interface Integrations {
  activeProvider: ProviderId;
  providers: Provider[];
  webhook: { configured: boolean; url: string | null; signed: boolean; events: string[] };
  deliveries: Delivery[];
  cep: { provider: string; enabled: boolean };
}

const providerInfo: Record<ProviderId, { what: string; env: string[]; mark: string }> = {
  SIMULATED: {
    what: "Cartão, PIX e boleto sem cobrança real, com cartões de teste e confirmação manual. Ideal para demonstração e testes.",
    env: ["VELO_PAYMENT_PROVIDER=SIMULATED"],
    mark: "S"
  },
  STRIPE: {
    what: "Stripe Checkout: o cliente paga na página segura da Stripe e a confirmação chega pelo webhook assinado.",
    env: ["VELO_PAYMENT_PROVIDER=STRIPE", "STRIPE_SECRET_KEY", "STRIPE_WEBHOOK_SECRET"],
    mark: "St"
  },
  MERCADO_PAGO: {
    what: "Checkout Pro do Mercado Pago: cartão, PIX e boleto na página do Mercado Pago, com notificação validada por x-signature.",
    env: ["VELO_PAYMENT_PROVIDER=MERCADO_PAGO", "MERCADOPAGO_ACCESS_TOKEN", "MERCADOPAGO_WEBHOOK_SECRET"],
    mark: "MP"
  }
};

const eventLabel: Record<string, string> = {
  "order.created": "Pedido criado",
  "order.paid": "Pedido pago",
  "order.processing": "Em separação",
  "order.shipped": "Enviado",
  "order.delivered": "Entregue",
  "order.canceled": "Cancelado",
  "order.refunded": "Estornado",
  "test.ping": "Teste"
};

function CepTester() {
  const [cep, setCep] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
  const run = async () => {
    setBusy(true);
    try {
      const r = await get<{ cep: string; street: string | null; district: string | null; city: string; state: string }>(
        `/api/address/cep/${digits(cep)}`
      );
      setResult({ ok: true, text: [r.street, r.district, `${r.city}/${r.state}`, r.cep].filter(Boolean).join(" · ") });
    } catch (e) {
      setResult({ ok: false, text: e instanceof ApiError ? e.message : "Não foi possível consultar." });
    } finally {
      setBusy(false);
    }
  };
  return (
    <form
      className="cep-tester"
      onSubmit={(e) => {
        e.preventDefault();
        run();
      }}
    >
      <label className="label" htmlFor="cep-test">
        Testar consulta
      </label>
      <div className="row">
        <input id="cep-test" className="input num" inputMode="numeric" placeholder="00000-000" value={cep} onChange={(e) => setCep(maskCep(e.target.value))} />
        <button className="btn ghost" disabled={busy || digits(cep).length !== 8}>
          {busy ? <span className="spinner" /> : <Icon name="pin" size={16} />} Consultar
        </button>
      </div>
      {result && <span className={result.ok ? "ok-text" : "warn-text"}>{result.text}</span>}
    </form>
  );
}

function Copy({ value }: { value: string }) {
  const toast = useToast();
  return (
    <button
      className="icon-btn sm"
      aria-label="Copiar"
      title="Copiar"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          toast.show("Copiado");
        } catch {
          toast.show("Não foi possível copiar", "error");
        }
      }}
    >
      <Icon name="copy" size={15} />
    </button>
  );
}

export function AdminIntegrationsPage() {
  const toast = useToast();
  const { data, loading, reload } = useApi<Integrations>("/api/admin/integrations");
  const [sending, setSending] = useState(false);

  const test = async () => {
    setSending(true);
    try {
      const d = await post<Delivery>("/api/admin/integrations/webhook/test");
      toast.show(d.status === "SUCCESS" ? `Evento entregue (HTTP ${d.httpStatus})` : `Falhou: ${d.error ?? "HTTP " + d.httpStatus}`, d.status === "SUCCESS" ? "info" : "error");
      reload();
    } catch (e) {
      toast.show(e instanceof ApiError ? e.message : "Não foi possível enviar o teste.", "error");
    } finally {
      setSending(false);
    }
  };

  return (
    <Page>
      <div className="wrap wide">
        <PageHeader
          title="Integrações"
          subtitle="Provedores de pagamento, notificações para outros sistemas e serviços de endereço. Tudo é configurado por variáveis de ambiente."
          actions={
            <button className="btn ghost" onClick={reload}>
              <Icon name="refresh" size={16} /> Atualizar
            </button>
          }
        />
        {loading && !data ? (
          <div className="stack">
            <Skeleton h={160} />
            <Skeleton h={220} />
          </div>
        ) : data ? (
          <div className="stack lg">
            <section>
              <div className="section-head">
                <h2>Pagamentos</h2>
                <span className="muted small">
                  Em uso: <strong>{data.providers.find((p) => p.active)?.name ?? data.activeProvider}</strong>
                </span>
              </div>
              <div className="integration-grid">
                {data.providers.map((p) => (
                  <article key={p.id} className={`card integration ${p.active ? "active" : ""}`}>
                    <header>
                      <span className={`integration-mark mark-${p.id.toLowerCase()}`}>{providerInfo[p.id]?.mark ?? p.name[0]}</span>
                      <div className="grow">
                        <h3>{p.name}</h3>
                        <span className={`status-line ${p.active ? "ok" : p.configured ? "idle" : "off"}`}>
                          <span className="dot" />
                          {p.active ? "Ativo" : p.configured ? "Configurado, não ativo" : "Não configurado"}
                        </span>
                      </div>
                    </header>
                    <p className="muted small">{providerInfo[p.id]?.what}</p>
                    {p.webhookUrl && (
                      <div className="field">
                        <span className="label">URL do webhook</span>
                        <div className="code-line">
                          <code>{p.webhookUrl}</code>
                          <Copy value={p.webhookUrl} />
                        </div>
                        <span className={p.webhookSecretConfigured ? "ok-text small" : "warn-text small"}>
                          {p.webhookSecretConfigured ? "Segredo de assinatura definido" : "Defina o segredo de assinatura para aceitar notificações"}
                        </span>
                      </div>
                    )}
                    <div className="env-list">
                      {providerInfo[p.id]?.env.map((e) => (
                        <code key={e}>{e}</code>
                      ))}
                    </div>
                  </article>
                ))}
              </div>
            </section>

            <section className="card pad">
              <div className="section-head">
                <div>
                  <h2>Webhooks de saída</h2>
                  <p className="muted small">A Velo avisa outro sistema (ERP, CRM, planilha, automação) sempre que um pedido muda de situação.</p>
                </div>
                <button className="btn" onClick={test} disabled={sending || !data.webhook.configured}>
                  {sending ? <span className="spinner" /> : <Icon name="send" size={16} />} Enviar evento de teste
                </button>
              </div>
              <div className="webhook-grid">
                <dl className="kv left">
                  <dt>Destino</dt>
                  <dd>
                    {data.webhook.url ? (
                      <span className="code-line">
                        <code>{data.webhook.url}</code>
                      </span>
                    ) : (
                      <span className="muted">
                        Não configurado. Defina <code>VELO_WEBHOOK_URL</code>.
                      </span>
                    )}
                  </dd>
                  <dt>Assinatura</dt>
                  <dd>
                    {data.webhook.signed ? (
                      <span className="ok-text">
                        HMAC-SHA256 no cabeçalho <code>X-Velo-Signature</code>
                      </span>
                    ) : (
                      <span className="warn-text">
                        Sem assinatura. Defina <code>VELO_WEBHOOK_SECRET</code>.
                      </span>
                    )}
                  </dd>
                  <dt>Entrega</dt>
                  <dd>Depois de confirmar a transação, em segundo plano, até 3 tentativas (timeout de 5 s).</dd>
                </dl>
                <div>
                  <span className="label">Eventos enviados</span>
                  <div className="event-chips">
                    {data.webhook.events.map((e) => (
                      <span key={e} className="event-chip">
                        <code>{e}</code>
                        <span className="muted">{eventLabel[e] ?? ""}</span>
                      </span>
                    ))}
                  </div>
                </div>
              </div>
              <h3 className="sub-title">Últimas entregas</h3>
              {data.deliveries.length === 0 ? (
                <p className="muted small empty-line">
                  <Icon name="webhook" size={16} /> Nenhuma entrega desde que o servidor subiu. O histórico guarda as 50 mais recentes, em memória.
                </p>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Quando</th>
                        <th>Evento</th>
                        <th>Resultado</th>
                        <th className="right">HTTP</th>
                        <th className="right">Tentativas</th>
                        <th className="right">Tempo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.deliveries.map((d) => (
                        <tr key={d.id + d.at}>
                          <td className="num">{dateTime(d.at)}</td>
                          <td>
                            <code>{d.type}</code>
                          </td>
                          <td>
                            <span className={`pill ${d.status === "SUCCESS" ? "ok" : "danger"}`}>
                              <span className="dot" />
                              {d.status === "SUCCESS" ? "Entregue" : "Falhou"}
                            </span>
                            {d.error && <span className="muted small"> {d.error}</span>}
                          </td>
                          <td className="right num">{d.httpStatus ?? "—"}</td>
                          <td className="right num">{d.attempts}</td>
                          <td className="right num">{d.durationMs} ms</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="card pad">
              <div className="section-head">
                <div>
                  <h2>Endereços</h2>
                  <p className="muted small">Consulta de CEP usada no checkout e na conta para preencher rua, bairro, cidade e UF.</p>
                </div>
                <span className={`status-line ${data.cep.enabled ? "ok" : "off"}`}>
                  <span className="dot" />
                  {data.cep.provider} {data.cep.enabled ? "ativo" : "desligado"}
                </span>
              </div>
              <p className="muted small">
                A consulta passa pela API da Velo (<code>GET /api/address/cep/&#123;cep&#125;</code>), com cache em memória. O navegador nunca chama
                serviços de fora, e a estimativa de prazo (<code>GET /api/shipping/quote</code>) usa a faixa do CEP, sem depender da internet.
              </p>
              <CepTester />
            </section>
          </div>
        ) : null}
      </div>
    </Page>
  );
}
