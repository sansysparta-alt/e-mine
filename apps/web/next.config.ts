import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const avecTraductions = createNextIntlPlugin("./src/i18n/request.ts");

const config: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  devIndicators: false,
  transpilePackages: ["@emine/ui", "@emine/api-client"],
};

export default avecTraductions(config);
