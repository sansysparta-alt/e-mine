import { LienEvitement } from "@emine/ui";
import type { Metadata, Viewport } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import type { ReactNode } from "react";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("site");
  return {
    title: { default: t("titre"), template: `%s · ${t("titre")}` },
    description: t("description"),
  };
}

export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#0a1f44" };

export default async function Disposition({ children }: { children: ReactNode }) {
  const langue = await getLocale();
  const t = await getTranslations("accessibilite");
  return (
    <html lang={langue}>
      <body className="min-h-dvh antialiased">
        <LienEvitement>{t("allerAuContenu")}</LienEvitement>
        <NextIntlClientProvider>{children}</NextIntlClientProvider>
      </body>
    </html>
  );
}
