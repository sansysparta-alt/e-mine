import { render, screen } from "@testing-library/react";
import { BadgeStatut, TONS_STATUT } from "./badge-statut";

/** Luminance relative WCAG d'une couleur #rrggbb. */
function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = Number.parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contraste(a: string, b: string): number {
  const [clair, fonce] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (clair + 0.05) / (fonce + 0.05);
}

describe("badges de statut (code couleur commun)", () => {
  it.each(Object.entries(TONS_STATUT))(
    "« %s » : contraste du texte ≥ 4,5:1 (WCAG AA)",
    (_, ton) => {
      expect(contraste(ton.texte, ton.fond)).toBeGreaterThanOrEqual(4.5);
    },
  );

  it("chaque ton a sa propre icône (le sens ne repose pas sur la seule couleur)", () => {
    const icones = Object.values(TONS_STATUT).map((t) => t.icone);
    expect(new Set(icones).size).toBe(icones.length);
  });

  it("affiche le libellé, l'icône décorative et les couleurs du ton", () => {
    render(<BadgeStatut ton="danger">Rejetée</BadgeStatut>);
    const badge = screen.getByText("Rejetée");
    expect(badge).toHaveAttribute("data-ton", "danger");
    expect(badge.style.color).toBe("rgb(161, 26, 18)");
    expect(badge.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});
