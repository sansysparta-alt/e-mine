import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { PaginationListe } from "./pagination-liste";

function afficher(total: number) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <PaginationListe
        chemin="/utilisateurs"
        page={1}
        taille={20}
        total={total}
        elements={{ un: "compte", plusieurs: "comptes" }}
      />
    </NextIntlClientProvider>,
  );
}

describe("résumé de la pagination", () => {
  it("s'accorde au singulier : « 1 compte », pas « 1 sur 1 comptes »", () => {
    afficher(1);
    expect(screen.getByRole("status")).toHaveTextContent(/^1 compte$/);
  });

  it("reste au pluriel au-delà", () => {
    afficher(45);
    expect(screen.getByRole("status")).toHaveTextContent("1–20 sur 45 comptes");
  });
});
