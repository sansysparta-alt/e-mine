import en from "../../messages/en.json";
import fr from "../../messages/fr.json";
import {
  CODES_PROFILS,
  CODES_WORKFLOWS,
  libelleDocument,
  libelleProfil,
  libelleProfilParNom,
  libelleSignataire,
  libelleWorkflowParNom,
  type TraduireReferentiel,
} from "./referentiels";

/** Traduction minimale sur un objet de messages (comme next-intl). */
function traduire(messages: Record<string, unknown>): TraduireReferentiel {
  const lire = (cle: string) =>
    cle.split(".").reduce<unknown>((o, c) => (o as Record<string, unknown>)?.[c], messages);
  const t = ((cle: string) => String(lire(cle))) as TraduireReferentiel;
  t.has = (cle: string) => typeof lire(cle) === "string";
  return t;
}

const tFr = traduire(fr.referentiels);
const tEn = traduire(en.referentiels);

describe("référentiels traduits (bilingue FR / EN)", () => {
  it("les noms en base correspondent exactement aux profils et familles en français", () => {
    expect(Object.fromEntries(Object.entries(CODES_PROFILS).map(([n, c]) => [c, n]))).toEqual(
      fr.referentiels.profils,
    );
    expect(Object.fromEntries(Object.entries(CODES_WORKFLOWS).map(([n, c]) => [c, n]))).toEqual(
      fr.referentiels.workflows,
    );
  });

  it("traduit les profils, sans majuscule superflue en français", () => {
    expect(libelleProfil("ADMIN_TECH", "Administrateur Technique", tFr)).toBe(
      "Administrateur technique",
    );
    expect(libelleProfil("INVESTISSEUR", "x", tEn)).toBe("Foreign investor");
    expect(libelleProfilParNom("Agent du guichet unique (CPDM)", tEn)).toBe(
      "One-stop shop agent (CPDM)",
    );
    expect(libelleProfil("INCONNU", "Libellé de la base", tEn)).toBe("Libellé de la base");
    // Majuscule au début de chaque titre (« Ministre / Secrétaire général ») et pour les sigles ou
    // noms propres seulement.
    for (const nom of Object.values(fr.referentiels.profils)) {
      for (const titre of nom.split(" / ")) {
        for (const mot of titre.split(" ").slice(1)) {
          expect(mot, nom).toMatch(/^[a-zà-ÿ(]|^\(?[A-Z]{2,}\)?$|^Ministère\)$/);
        }
      }
    }
  });

  it("traduit les documents, les signataires et les familles", () => {
    const pex = { libelleFr: "Permis d'exploitation", libelleEn: "Mining licence" };
    expect(libelleDocument(pex, "fr")).toBe("Permis d'exploitation");
    expect(libelleDocument(pex, "en")).toBe("Mining licence");
    expect(libelleDocument({ libelleFr: "Sans traduction", libelleEn: null }, "en")).toBe(
      "Sans traduction",
    );
    expect(libelleSignataire("Directeur général", tEn)).toBe("Director General");
    expect(libelleWorkflowParNom("Titres miniers", tEn)).toBe("Mining titles");
  });

  it("en anglais, une demande est une « request » (jamais « application »)", () => {
    const textes: string[] = [];
    const parcourir = (o: unknown) => {
      if (typeof o === "string") textes.push(o);
      else if (o && typeof o === "object") Object.values(o).forEach(parcourir);
    };
    parcourir(en);
    expect(textes.filter((t) => /\bapplications?\b/i.test(t))).toEqual([]);
  });
});
