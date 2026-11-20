import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const avecTraductions = createNextIntlPlugin("./src/i18n/request.ts");

const config: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  transpilePackages: ["@emine/ui"],
};

export default avecTraductions(config);
