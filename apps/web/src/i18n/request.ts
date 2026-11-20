import { getRequestConfig } from "next-intl/server";

/**
 * V1 : interface en français uniquement (docs/07 §2). Les clés anglaises existent déjà dans
 * messages/en.json ; le choix de la langue arrivera avec le sélecteur (US-00.6).
 */
export const LANGUE_PAR_DEFAUT = "fr";

export default getRequestConfig(async () => {
  const locale = LANGUE_PAR_DEFAUT;
  return {
    locale,
    timeZone: "Africa/Conakry",
    messages: (await import(`../../messages/${locale}.json`)).default,
  };
});
