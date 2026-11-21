// Page de paiement du simulateur : compte à rebours de la session et protection contre le double clic.
// La page fonctionne sans JavaScript (formulaires classiques) ; ce script n'ajoute que du confort.
(function () {
  "use strict";

  const rebours = document.querySelector("[data-compte-a-rebours]");
  if (rebours) {
    let restant = Number(rebours.getAttribute("data-secondes")) || 0;
    const afficher = () => {
      if (restant <= 0) {
        rebours.textContent = "Session expirée : revenez sur e-mine pour relancer le paiement.";
        return false;
      }
      const minutes = Math.floor(restant / 60);
      const secondes = String(restant % 60).padStart(2, "0");
      rebours.textContent = "Il reste " + minutes + " min " + secondes + " s.";
      return true;
    };
    if (afficher()) {
      const minuterie = setInterval(() => {
        restant -= 1;
        if (!afficher()) clearInterval(minuterie);
      }, 1000);
    }
  }

  const formulaire = document.querySelector("[data-formulaire-paiement]");
  const bouton = document.querySelector("[data-bouton-payer]");
  const texte = document.querySelector("[data-texte-bouton]");
  if (formulaire && bouton && texte) {
    let envoye = false;
    formulaire.addEventListener("submit", (evenement) => {
      if (envoye) {
        evenement.preventDefault();
        return;
      }
      envoye = true;
      bouton.setAttribute("aria-disabled", "true");
      texte.textContent = "Paiement en cours…";
    });
  }
})();
