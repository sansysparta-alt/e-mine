import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../messages/fr.json";
import { empreinteSha256, extraireCode } from "./verification";
import { VerifierFichier } from "./verifier-fichier";

const CODE = "0123456789abcdef0123456789abcdef";

describe("vérification publique (US-06.5)", () => {
  it("lit le code seul ou dans l'adresse du QR, sans tenir compte de la casse", () => {
    expect(extraireCode(CODE)).toBe(CODE);
    expect(extraireCode(` https://site.test/verifier/${CODE.toUpperCase()} `)).toBe(CODE);
    expect(extraireCode("pas-un-code")).toBeNull();
    expect(extraireCode(undefined)).toBeNull();
  });

  it("calcule l'empreinte SHA-256 dans le navigateur", async () => {
    const octets = new TextEncoder().encode("abc");
    expect(await empreinteSha256(octets.buffer as ArrayBuffer)).toBe(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
    );
  });

  it("dit si le fichier présenté est identique ou modifié", async () => {
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <VerifierFichier empreinteAttendue="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" />
      </NextIntlClientProvider>,
    );
    const champ = screen.getByLabelText("Fichier PDF à comparer avec le document délivré");
    fireEvent.change(champ, { target: { files: [new File(["abc"], "permis.pdf")] } });
    expect(await screen.findByText("Le fichier est identique au document délivré")).toBeVisible();
    fireEvent.change(champ, { target: { files: [new File(["abd"], "permis-modifie.pdf")] } });
    expect(
      await screen.findByText("Le fichier ne correspond pas au document délivré"),
    ).toBeVisible();
    expect(screen.getByText(/permis-modifie\.pdf/)).toBeVisible();
  });
});
