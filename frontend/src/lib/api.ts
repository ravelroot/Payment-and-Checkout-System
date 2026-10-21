export class ApiError extends Error {
  status: number;
  code: string;
  fields: Record<string, string>;

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

type Hooks = {
  token: () => string | null;
  onSession: (idleExpiresAt: number, expiresAt: number) => void;
  onUnauthorized: (code: string, message: string) => void;
};

let hooks: Hooks = {
  token: () => null,
  onSession: () => {},
  onUnauthorized: () => {}
};

export function configureApi(h: Hooks) {
  hooks = h;
}

type Options = { method?: string; body?: unknown; passive?: boolean; signal?: AbortSignal };

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  const token = hooks.token();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (opts.passive) headers["X-Velo-Passive"] = "true";
  let body: BodyInit | undefined;
  if (opts.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(opts.body);
  }
  let res: Response;
  try {
    res = await fetch(path, { method: opts.method ?? (body ? "POST" : "GET"), headers, body, signal: opts.signal });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw e;
    throw new ApiError(0, "NETWORK", "Não foi possível falar com o servidor. Confira se a API está rodando.");
  }
  const idle = res.headers.get("X-Velo-Idle-Expires-At");
  const max = res.headers.get("X-Velo-Expires-At");
  if (idle && max) hooks.onSession(Number(idle), Number(max));
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (!res.ok) {
    const code = (data && (data as { code?: string }).code) || "ERROR";
    const message = (data && (data as { message?: string }).message) || "Algo deu errado. Tente de novo.";
    const fields = (data && (data as { fields?: Record<string, string> }).fields) || {};
    if (res.status === 401 && token && !path.startsWith("/api/auth/login")) {
      hooks.onUnauthorized(code, message);
    }
    throw new ApiError(res.status, code, message, fields);
  }
  return data as T;
}

function safeJson(t: string) {
  try {
    return JSON.parse(t);
  } catch {
    return null;
  }
}

export const get = <T>(path: string, signal?: AbortSignal) => api<T>(path, { signal });
export const post = <T>(path: string, body?: unknown) => api<T>(path, { method: "POST", body: body ?? {} });
export const put = <T>(path: string, body?: unknown) => api<T>(path, { method: "PUT", body: body ?? {} });
export const del = <T>(path: string) => api<T>(path, { method: "DELETE" });

export function qs(params: Record<string, string | number | boolean | null | undefined>) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== null && v !== undefined && v !== "" && v !== false) p.set(k, String(v));
  }
  const s = p.toString();
  return s ? `?${s}` : "";
}
