"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchMe, login as apiLogin, logout as apiLogout, type LoginProfile } from "../../lib/api-client";

export type AuthState = {
  profile: LoginProfile | null;
  busy: boolean;
  notice: string;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  handleSessionExpired: () => void;
  setNotice: (notice: string) => void;
};

export function useAuth(loginSuccessMessage: string, loginNoticeMessage: string, sessionExpiredMessage: string): AuthState {
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(loginNoticeMessage);

  // V2: Restore profile from httpOnly cookie via /api/users/me
  useEffect(() => {
    let cancelled = false;
    fetchMe()
      .then((restoredProfile) => {
        if (!cancelled) setProfile(restoredProfile);
      })
      .catch(() => {
        // Not logged in — that's fine
      });
    return () => { cancelled = true; };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    setBusy(true);
    try {
      const nextProfile = await apiLogin(username, password);
      setProfile(nextProfile);
      setNotice(loginSuccessMessage);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Login failed");
      throw error;
    } finally {
      setBusy(false);
    }
  }, [loginSuccessMessage]);

  const logout = useCallback(() => {
    apiLogout().catch(() => {});
    setProfile(null);
    setNotice(loginNoticeMessage);
  }, [loginNoticeMessage]);

  const handleSessionExpired = useCallback(() => {
    setProfile(null);
    setNotice(sessionExpiredMessage);
  }, [sessionExpiredMessage]);

  return { profile, busy, notice, login, logout, handleSessionExpired, setNotice };
}
