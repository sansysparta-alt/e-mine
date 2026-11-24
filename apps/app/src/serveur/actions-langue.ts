"use server";

import { cookies } from "next/headers";
import { COOKIE_LANGUE, langueValide, OPTIONS_COOKIE_LANGUE } from "@/i18n/langues";

/**
 * Change la langue de l'interface (cookie lu par next-intl et transmis à Keycloak). Une valeur
 * inconnue revient au français.
 */
export async function changerLangue(langue: string): Promise<void> {
  (await cookies()).set(COOKIE_LANGUE, langueValide(langue), OPTIONS_COOKIE_LANGUE);
}
