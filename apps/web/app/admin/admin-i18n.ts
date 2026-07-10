import type { AppLocale } from "../../lib/api-client";

export type AdminLocale = AppLocale;

export function adminText(locale: AdminLocale, zh: string, en: string) {
  return locale === "zh-CN" ? zh : en;
}

export function normalizeLocale(value: string | undefined): AdminLocale {
  return value === "en" ? "en" : "zh-CN";
}
