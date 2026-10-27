import { useCallback, useEffect, useState, useSyncExternalStore } from "react";

/*
 * Preferências guardadas só no navegador: tema, favoritos e vistos recentemente.
 * Nada aqui é sensível; o token da sessão continua só em memória.
 */

type Listener = () => void;
const listeners = new Set<Listener>();
const emit = () => listeners.forEach((l) => l());
const subscribe = (l: Listener) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* navegação privada ou armazenamento bloqueado: segue sem guardar */
  }
  cache.delete(key);
  emit();
}

const cache = new Map<string, unknown>();
function snapshot<T>(key: string, fallback: T): T {
  if (!cache.has(key)) cache.set(key, read(key, fallback));
  return cache.get(key) as T;
}

/* Tema */

export type ThemeMode = "system" | "light" | "dark";
const THEME_KEY = "velo.theme";

function systemDark() {
  return typeof window !== "undefined" && window.matchMedia?.("(prefers-color-scheme: dark)").matches;
}

export function applyTheme(mode: ThemeMode = snapshot<ThemeMode>(THEME_KEY, "system")) {
  const dark = mode === "dark" || (mode === "system" && systemDark());
  document.documentElement.dataset.theme = dark ? "dark" : "light";
}

export function useTheme() {
  const mode = useSyncExternalStore(subscribe, () => snapshot<ThemeMode>(THEME_KEY, "system"));
  useEffect(() => {
    applyTheme(mode);
    if (mode !== "system") return;
    const mq = window.matchMedia?.("(prefers-color-scheme: dark)");
    const on = () => applyTheme("system");
    mq?.addEventListener("change", on);
    return () => mq?.removeEventListener("change", on);
  }, [mode]);
  const setMode = useCallback((m: ThemeMode) => write(THEME_KEY, m), []);
  return { mode, setMode };
}

/* Favoritos (por usuário) */

const favKey = (userId: number | undefined) => `velo.favoritos.${userId ?? "anon"}`;
const EMPTY: number[] = [];

export function useFavorites(userId: number | undefined) {
  const key = favKey(userId);
  const ids = useSyncExternalStore(subscribe, () => snapshot<number[]>(key, EMPTY));
  const toggle = useCallback(
    (id: number) => {
      const cur = snapshot<number[]>(key, EMPTY);
      write(key, cur.includes(id) ? cur.filter((x) => x !== id) : [id, ...cur].slice(0, 200));
    },
    [key]
  );
  return { ids, has: (id: number) => ids.includes(id), toggle };
}

/* Vistos recentemente (por usuário) */

export interface RecentItem {
  id: number;
  slug: string;
  name: string;
  price: number;
  imageUrl: string | null;
  categorySlug: string | null;
}

const recentKey = (userId: number | undefined) => `velo.recentes.${userId ?? "anon"}`;
const NONE: RecentItem[] = [];

export function useRecent(userId: number | undefined) {
  const key = recentKey(userId);
  const items = useSyncExternalStore(subscribe, () => snapshot<RecentItem[]>(key, NONE));
  const push = useCallback(
    (item: RecentItem) => {
      const cur = snapshot<RecentItem[]>(key, NONE).filter((r) => r.id !== item.id);
      write(key, [item, ...cur].slice(0, 8));
    },
    [key]
  );
  const clear = useCallback(() => write(key, []), [key]);
  return { items, push, clear };
}

/* Atalho de teclado simples */

export function useHotkey(match: (e: KeyboardEvent) => boolean, run: (e: KeyboardEvent) => void) {
  const [fn] = useState(() => ({ match, run }));
  fn.match = match;
  fn.run = run;
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (fn.match(e)) fn.run(e);
    };
    window.addEventListener("keydown", on);
    return () => window.removeEventListener("keydown", on);
  }, [fn]);
}

export const isTyping = (e: KeyboardEvent) => {
  const t = e.target as HTMLElement | null;
  return !!t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.tagName === "SELECT" || t.isContentEditable);
};

/* Tom de fundo por categoria */

export const tint = (slug: string | null | undefined) => (slug ? `var(--tint-${slug}, var(--sand))` : "var(--sand)");
