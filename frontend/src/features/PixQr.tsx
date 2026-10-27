import { useEffect, useState } from "react";

export function PixQr({ value, size = 168 }: { value: string; size?: number }) {
  const [svg, setSvg] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    import("qrcode")
      .then((m) => ((m as unknown as { default?: typeof m }).default ?? m).toString(value, { type: "svg", margin: 0, errorCorrectionLevel: "M", color: { dark: "#191a17", light: "#ffffff" } }))
      .then((s) => alive && setSvg(s))
      .catch(() => alive && setSvg(null));
    return () => {
      alive = false;
    };
  }, [value]);
  return (
    <figure className="pix-qr" style={{ width: size + 24 }}>
      <div className="pix-qr-code" style={{ width: size, height: size }} dangerouslySetInnerHTML={svg ? { __html: svg } : undefined} role="img" aria-label="QR Code do PIX" />
      <figcaption>Aponte a câmera do app do seu banco</figcaption>
    </figure>
  );
}
