import { cookies } from "next/headers";
import { getRequestConfig } from "next-intl/server";
import { COOKIE_LANGUE, langueValide } from "./langues";

export { LANGUE_PAR_DEFAUT } from "./langues";

/**
 * Langue de l'interface : celle choisie par l'utilisateur (menu sous l'avatar, page « Mon
 * profil »), gardée dans un cookie ; le français par défaut.
 */
export default getRequestConfig(async () => {
  const locale = langueValide((await cookies()).get(COOKIE_LANGUE)?.value);
  return {
    locale,
    timeZone: "Africa/Conakry",
    messages: (await import(`../../messages/${locale}.json`)).default,
  };
});
