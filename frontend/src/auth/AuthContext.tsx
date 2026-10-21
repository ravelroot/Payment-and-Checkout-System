import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { configureApi, get, post } from "../lib/api";
import type { Me, SessionInfo } from "../lib/types";

type EndReason = "LOGOUT" | "IDLE" | "EXPIRED" | "UNAUTHORIZED";

interface AuthState {
  user: Me | null;
  idleMinutes: number;
  maxMinutes: number;
  login: (email: string, password: string) => Promise<Me>;
  register: (fullName: string, email: string, password: string) => Promise<Me>;
  logout: (reason?: EndReason, message?: string) => void;
  notice: string | null;
  clearNotice: () => void;
  timing: React.MutableRefObject<Timing>;
  touch: () => void;
  refreshMe: () => Promise<void>;
}

export interface Timing {
  lastActivity: number;
  lastPing: number;
  serverIdleAt: number;
  expiresAt: number;
}

const AuthContext = createContext<AuthState | null>(null);

const MESSAGES: Record<string, string> = {
  IDLE: "Sua sessão terminou depois de 20 minutos sem atividade. Entre novamente.",
  EXPIRED: "Sua sessão atingiu o limite de 30 minutos. Entre novamente para continuar.",
  SESSION_IDLE: "Sua sessão terminou por inatividade. Entre novamente.",
  SESSION_EXPIRED: "Sua sessão atingiu o tempo máximo. Entre novamente."
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [user, setUser] = useState<Me | null>(null);
  const [limits, setLimits] = useState({ idle: 20, max: 30 });
  const [notice, setNotice] = useState<string | null>(null);
  const tokenRef = useRef<string | null>(null);
  const timing = useRef<Timing>({ lastActivity: 0, lastPing: 0, serverIdleAt: 0, expiresAt: 0 });

  const end = useCallback(
    (reason: EndReason, message?: string) => {
      const token = tokenRef.current;
      tokenRef.current = null;
      if (token && reason === "LOGOUT") {
        fetch("/api/auth/logout", { method: "POST", headers: { Authorization: `Bearer ${token}` } }).catch(() => {});
      } else if (token && reason !== "UNAUTHORIZED") {
        navigator.sendBeacon?.("/api/auth/logout", new Blob([token], { type: "text/plain" }));
      }
      setUser(null);
      setNotice(reason === "LOGOUT" ? null : message ?? MESSAGES[reason] ?? null);
      navigate("/entrar", { replace: true });
    },
    [navigate]
  );

  useEffect(() => {
    configureApi({
      token: () => tokenRef.current,
      onSession: (idleAt, expiresAt) => {
        timing.current.serverIdleAt = idleAt;
        timing.current.expiresAt = expiresAt;
      },
      onUnauthorized: (code, message) => end("UNAUTHORIZED", MESSAGES[code] ?? message)
    });
  }, [end]);

  useEffect(() => {
    const leave = () => {
      const token = tokenRef.current;
      if (token) navigator.sendBeacon?.("/api/auth/logout", new Blob([token], { type: "text/plain" }));
    };
    window.addEventListener("pagehide", leave);
    return () => window.removeEventListener("pagehide", leave);
  }, []);

  const start = useCallback((s: SessionInfo) => {
    tokenRef.current = s.token;
    const now = Date.now();
    timing.current = {
      lastActivity: now,
      lastPing: now,
      serverIdleAt: new Date(s.idleExpiresAt).getTime(),
      expiresAt: new Date(s.expiresAt).getTime()
    };
    setLimits({ idle: s.idleMinutes, max: s.maxMinutes });
    setNotice(null);
    setUser(s.user);
    return s.user;
  }, []);

  const login = useCallback(
    async (email: string, password: string) => start(await post<SessionInfo>("/api/auth/login", { email, password })),
    [start]
  );

  const register = useCallback(
    async (fullName: string, email: string, password: string) =>
      start(await post<SessionInfo>("/api/auth/register", { fullName, email, password })),
    [start]
  );

  const logout = useCallback((reason: EndReason = "LOGOUT", message?: string) => end(reason, message), [end]);
  const touch = useCallback(() => {
    timing.current.lastActivity = Date.now();
  }, []);

  const refreshMe = useCallback(async () => {
    if (!tokenRef.current) return;
    setUser(await get<Me>("/api/auth/me"));
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      idleMinutes: limits.idle,
      maxMinutes: limits.max,
      login,
      register,
      logout,
      notice,
      clearNotice: () => setNotice(null),
      timing,
      touch,
      refreshMe
    }),
    [user, limits, login, register, logout, notice, touch, refreshMe]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("AuthProvider ausente");
  return ctx;
}

export function useIsBackoffice() {
  const { user } = useAuth();
  return user?.role === "ADMIN" || user?.role === "STAFF";
}
