"use server";

import { cookies } from "next/headers";
import { COOKIE_LANGUE, langueValide } from "@/i18n/langues";

/** Un an : la langue choisie reste celle de ce navigateur. */
const DUREE_COOKIE = 365 * 24 * 60 * 60;

/**
 * Change la langue de l'interface (cookie lu par next-intl et transmis à Keycloak). Une valeur
 * inconnue revient au français.
 */
export async function changerLangue(langue: string): Promise<void> {
  (await cookies()).set(COOKIE_LANGUE, langueValide(langue), {
    path: "/",
    maxAge: DUREE_COOKIE,
    sameSite: "lax",
    httpOnly: true,
  });
}
