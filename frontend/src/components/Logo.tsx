export function Mark({ size = 28, color = "currentColor", title }: { size?: number; color?: string; title?: string }) {
  // Split: o valor do pedido dividido entre os recebedores
  const altura = size * 0.85;
  return (
    <svg
      width={(altura * 62) / 64}
      height={altura}
      viewBox="21 18 62 64"
      fill={color}
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
    >
      <rect x="21" y="18" width="26" height="64" />
      <rect x="53" y="18" width="16" height="64" />
      <rect x="75" y="18" width="8" height="64" />
    </svg>
  );
}

export function Wordmark({ size = 22, color = "currentColor" }: { size?: number; color?: string }) {
  return (
    <span className="wordmark" style={{ fontSize: size, color }} aria-label="Velo">
      Velo
    </span>
  );
}

export function Logo({ size = 22, color = "currentColor" }: { size?: number; color?: string }) {
  return (
    <span className="logo" style={{ color }}>
      <Mark size={size} />
      <Wordmark size={size * 0.95} />
    </span>
  );
}
