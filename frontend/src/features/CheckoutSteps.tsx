import { Icon } from "../components/Icon";

const STEPS = ["Carrinho", "Entrega", "Pagamento", "Confirmação"];

export function CheckoutSteps({ current }: { current: number }) {
  return (
    <ol className="steps" aria-label="Etapas da compra">
      {STEPS.map((s, i) => {
        const state = i < current ? "done" : i === current ? "now" : "next";
        return (
          <li key={s} className={state} aria-current={state === "now" ? "step" : undefined}>
            <span className="step-mark num">{state === "done" ? <Icon name="check" size={13} stroke={2.4} /> : i + 1}</span>
            <span className="step-label">{s}</span>
          </li>
        );
      })}
    </ol>
  );
}
