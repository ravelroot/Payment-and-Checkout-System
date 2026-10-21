export function Mark({ size = 28, color = "currentColor", title }: { size?: number; color?: string; title?: string }) {
  return (
    <svg
      width={(size * 328) / 296}
      height={size}
      viewBox="66 108 328 296"
      fill="none"
      stroke={color}
      strokeWidth={24}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
    >
      <path d="M190 200H380L369 390H201Z" />
      <path d="M237 200V170A48 48 0 0 1 333 170V200" />
      <path d="M80 260H140M105 315H150M80 370H140" />
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
